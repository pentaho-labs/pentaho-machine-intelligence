/*******************************************************************************
 * Pentaho Data Science
 * <p/>
 * Copyright (c) 2002-2018 Hitachi Vantara. All rights reserved.
 * <p/>
 * ******************************************************************************
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 * <p/>
 ******************************************************************************/

package org.pentaho.di.trans.steps.pmi.weka;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.dm.commons.LogAdapter;

import weka.classifiers.timeseries.AbstractForecaster;
import weka.classifiers.timeseries.core.IncrementallyPrimeable;
import weka.classifiers.timeseries.core.OverlayForecaster;
import weka.filters.supervised.attribute.TSLagMaker;
import weka.classifiers.timeseries.core.TSLagUser;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Applies a pre-built weka forecasting model to make a forecast for future time
 * steps. Incoming rows are treated as historical data for priming the model
 * with - i.e. the priming process ensures that all the lagged and derived field
 * values for the forecaster are filled with respect to the most recent
 * historical data. The forecaster can then be applied in a closed-loop manner
 * to forecast for future time steps. The closed-loop process takes values
 * forecasted for the next time step and feeds them back into the model in order
 * to make a forecast for subsequent time step, and so on. Therefore, the
 * priming data is expected to up to, and including, the current time step (i.e.
 * up to one step immediately prior to the first future forecast step).
 * IMPORTANT: priming rows are assumed to be equally spaced in time and are
 * sorted in ascending order of time. If a time stamp is included in the data
 * then we check that this is the case. It is also assumed that the priming data
 * (and overlay data if provided) have the same time interval and periodicity as
 * the data used to train the model. If this is not the case then results may be
 * nonsensical. We do not check for this.
 * <p>
 * <p>
 * If the forecasting model has been trained with "overlay" data, i.e. fields
 * that are not forecasted or derived automatically, then the incoming data
 * stream needs to contain these fields - not only for the priming data, but
 * *also* for time steps that are to be forecasted. We assume that overlay data
 * for future time steps to be forecasted is indicated by the presence of rows
 * that contain all missing (null) values for the target fields to be
 * forecasted. Of course, the values of the overlay fields need to be
 * non-missing in this data. The number of "overlay" rows determines the number
 * of steps forecasted by the forecaster, and overides any value specified in
 * the "numSteps" parameter.
 * <p>
 * <p>
 * When a forecast is generated, confidence intervals will be included for any
 * steps for which they were estimated when training the model.
 * <p>
 * <p>
 * Attributes that the Weka model was constructed from are automatically mapped
 * to incoming Kettle fields on the basis of name and type. Any attributes that
 * cannot be mapped due to type mismatch or not being present in the incoming
 * fields receive missing values when incoming Kettle rows are converted to
 * Weka's Instance format. Similarly, any values for string fields that have not
 * been seen during the training of the Weka model are converted to missing
 * values.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision$
 */
public class PMIForecasting extends BaseStep implements StepInterface {

  private PMIForecastingMeta m_meta;
  private PMIForecastingData m_data;

  private final TransMeta m_transMeta;

  private final LogAdapter m_log;

  /**
   * Creates a new <code>WekaForecasting</code> instance.
   *
   * @param stepMeta          holds the step's meta data
   * @param stepDataInterface holds the step's temporary data
   * @param copyNr            the number assigned to the step
   * @param transMeta         meta data for the transformation
   * @param trans             a <code>Trans</code> value
   */
  public PMIForecasting( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta,
      Trans trans ) {
    super( stepMeta, stepDataInterface, copyNr, transMeta, trans );
    m_transMeta = transMeta;

    m_log = new LogAdapter( getLogChannel() );
  }

  private WekaForecastingModel setModel( String modelFileName ) throws KettleException {
    String modName = m_transMeta.environmentSubstitute( modelFileName );
    File modelFile = null;
    if ( modName.startsWith( "file:" ) ) {
      try {
        modName = modName.replace( " ", "%20" );
        modelFile = new File( new java.net.URI( modName ) );
      } catch ( Exception ex ) {
        throw new KettleException( "Malformed URI for model file" );
      }
    } else {
      modelFile = new File( modName );
    }
    if ( !modelFile.exists() ) {
      throw new KettleException( "Serialized model file does " + "not exist on disk!" );
    }

    // Load the model
    WekaForecastingModel model = null;
    try {
      model = PMIForecastingData.loadSerializedModel( modelFile, m_log );
      m_meta.setModel( model );
    } catch ( Exception ex ) {
      throw new KettleException( "Problem de-serializing model " + "file!" );
    }
    return model;
  }

  protected List<Object[]> m_overlayData;
  protected List<Object[]> m_bufferedPrimeData;
  protected boolean m_isIncrementallyPrimeable = false;
  protected boolean m_isUsingOverlayData = false;

  /**
   * rebuild the model on the incoming data before forecasting?
   */
  protected boolean m_rebuildModel = false;

  protected TSLagMaker m_modelLagMaker;
  protected String m_timeStampName = "";
  protected int m_timeStampRowIndex;
  protected List<String> m_fieldsToForecast;

  /**
   * Process an incoming row of data.
   *
   * @param smi a <code>StepMetaInterface</code> value
   * @param sdi a <code>StepDataInterface</code> value
   * @return a <code>boolean</code> value
   * @throws KettleException if an error occurs
   */
  @Override public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {

    m_meta = (PMIForecastingMeta) smi;
    m_data = (PMIForecastingData) sdi;

    Object[] r = getRow();

    if ( r == null ) {
      // m_meta.getModel().done();

      // now we need to check/flush buffers, generate the actual forecast
      // emit the rows containing the forecast
      if ( ( !m_isIncrementallyPrimeable || m_rebuildModel ) && m_bufferedPrimeData.size() > 0 ) {

        if ( m_rebuildModel ) {
          logBasic( "Rebuilding/re-estimating forecaster..." );
        } else {
          logBasic( "Flushing priming buffer..." );
        }

        Instances primeData = new Instances( m_meta.getModel().getHeader(), 0 );
        for ( int i = 0; i < m_bufferedPrimeData.size(); i++ ) {
          Object[] bufferedRow = m_bufferedPrimeData.get( i );

          Instance
              converted =
              m_data.constructInstance( getInputRowMeta(), bufferedRow, m_meta.getMappingIndexes(), m_meta.getModel() );
          primeData.add( converted );

          // push out this historical row
          Object[] outputRow = RowDataUtil.resizeArray( bufferedRow, m_data.getOutputRowMeta().size() );

          m_data.fixTypesForTargets( outputRow, m_fieldsToForecast, getInputRowMeta() );
          int flagIndex = m_data.getOutputRowMeta().indexOfValue( "Forecasted" );
          outputRow[flagIndex] = new Boolean( false );
          putRow( m_data.getOutputRowMeta(), outputRow );
        }
        // prime/rebuild the forecaster
        try {
          if ( m_rebuildModel ) {
            if ( !m_data.sortCheck( m_meta.getModel().getModel(), primeData ) ) {
              throw new KettleException(
                  "Unable to rebuild/re-estimate the model " + "because the incoming data does not appear to be in "
                      + "ascending order of the time stamp" );
            }
            m_meta.getModel().getModel().buildForecaster( primeData, m_log );
          }

          m_meta.getModel().getModel().primeForecaster( primeData );
        } catch ( Exception e ) {
          e.printStackTrace();
          throw new KettleException( e );
        }
      }

      // get the forecast from the data class
      try {
        logBasic( "Generating forecast..." );
        List<Object[]>
            outputForecast =
            m_data.generateForecast( getInputRowMeta(), m_data.getOutputRowMeta(), m_meta, m_overlayData, m_transMeta,
                m_log );

        for ( int i = 0; i < outputForecast.size(); i++ ) {
          putRow( m_data.getOutputRowMeta(), outputForecast.get( i ) );
        }
      } catch ( Exception e ) {
        e.printStackTrace();
        throw new KettleException( "[WekaForecasting] a problem occurred during " + "forecasting", e );
      }

      // save the forecaster out to a file if requested
      if ( m_rebuildModel && !Const.isEmpty( m_meta.getSavedForecasterFileName() ) ) {
        try {
          String modName = m_transMeta.environmentSubstitute( m_meta.getSavedForecasterFileName() );

          File updatedModelFile = null;
          if ( modName.startsWith( "file:" ) ) {
            try {
              modName = modName.replace( " ", "%20" );
              updatedModelFile = new File( new java.net.URI( modName ) );
            } catch ( Exception ex ) {
              throw new KettleException( "Malformed URI for updated forecaster file" );
            }
          } else {
            updatedModelFile = new File( modName );
          }
          logBasic( "Saving forecaster to file \"" + updatedModelFile + "\"" );
          PMIForecastingData.saveSerializedModel( m_meta.getModel(), updatedModelFile );
        } catch ( Exception ex ) {
          throw new KettleException( "Problem saving updated forecaster to file!" );
        }
      }

      setOutputDone();
      return false;
    }

    // Handle the first row
    if ( first ) {
      first = false;

      logBasic( "Configuring forecaster..." );

      if ( m_meta.getModel() == null || !Const.isEmpty( m_meta.getSerializedModelFileName() ) ) {
        // If we don't have a model, or a file name is set, then load from file

        // Check that we have a file to try and load a classifier from
        if ( Const.isEmpty( m_meta.getSerializedModelFileName() ) ) {
          throw new KettleException( "No filename to load  " + "model from!!" );
        }

        setModel( m_meta.getSerializedModelFileName() );
      }

      m_data.setOutputRowMeta( getInputRowMeta().clone() );

      // Check the input row meta data against the instances
      // header that the classifier was trained with
      try {
        Instances header = m_meta.getModel().getHeader();
        m_meta.mapIncomingRowMetaData( header, getInputRowMeta() );
      } catch ( Exception ex ) {
        throw new KettleException(
            "[WekaForecasting] Incoming data format does not seem to " + "match what the model was trained with" );
      }

      // Determine the output format
      m_meta.getFields( m_data.getOutputRowMeta(), getStepname(), null, null, this );
      // System.err.println("Output Format: \n"
      // + m_data.getOutputRowMeta().toStringMeta());

      // set time stamp name and index in incoming rows (if necessary)
      if ( m_meta.getModel().getModel() instanceof TSLagUser ) {
        m_modelLagMaker = ( (TSLagUser) m_meta.getModel().getModel() ).getTSLagMaker();
        if ( !m_modelLagMaker.isUsingAnArtificialTimeIndex() && m_modelLagMaker.getAdjustForTrends() ) {
          m_timeStampName = m_modelLagMaker.getTimeStampField();
          if ( m_timeStampName.length() > 0 ) {
            m_timeStampRowIndex = getInputRowMeta().indexOfValue( m_timeStampName );
            if ( m_timeStampRowIndex < 0 ) {
              throw new KettleException(
                  "Can't find the time s" + "tamp field: " + m_timeStampName + " in the incoming rows!" );
            }
          }
        }
      }

      m_isUsingOverlayData =
          ( m_meta.getModel().getModel() instanceof OverlayForecaster && ( (OverlayForecaster) m_meta.getModel()
              .getModel() ).isUsingOverlayData() );

      m_rebuildModel = m_meta.getRebuildForecaster();

      if ( m_meta.getModel().getModel() instanceof IncrementallyPrimeable && !m_rebuildModel ) {
        m_isIncrementallyPrimeable = true;
        logBasic( "Forecaster will be primed incrementally." );
        // first reset lag histories
        try {
          m_meta.getModel().getModel().primeForecaster( new Instances( m_meta.getModel().getHeader(), 0 ) );

          // actual priming will happen below in the main part of this method
          // that handles each incoming row

          /*
           * // incrementally prime Instance toPrimeWith =
           * m_data.constructInstance(getInputRowMeta(), r,
           * m_meta.getMappingIndexes(), m_meta.getModel());
           * 
           * ((IncrementallyPrimeable)m_meta.getModel().getModel()).
           * primeForecasterIncremental(toPrimeWith);
           */
        } catch ( Exception ex ) {
          ex.printStackTrace();
          throw new KettleException( "Problem during initialization " + "of the priming data." );
        }
      } else if ( m_rebuildModel ) {
        logBasic( "Forecaster will be rebuilt/re-estimated on incoming data" );
      }

      if ( m_isUsingOverlayData ) {
        logBasic(
            "Forecaster is using overlay data. We expect to see overlay " + "field values for forecasting period." );
        m_overlayData = new ArrayList<Object[]>();
      }

      if ( !( m_meta.getModel().getModel() instanceof IncrementallyPrimeable ) || m_rebuildModel ) {
        m_bufferedPrimeData = new ArrayList<Object[]>();
      }

      m_fieldsToForecast = AbstractForecaster.stringToList( m_meta.getModel().getModel().getFieldsToForecast() );

    } // end (if first)

    // if we are expecting overlay data, then check this row to see if all
    // target values predicted by the forecaster are missing. If so, then this
    // *might* indicate the start of the overlay data. We will start buffering
    // rows into the overlay buffer. If we get a row with all non-missing
    // targets
    // at some future point then we will flush the overlay buffer either into
    // the
    // forecaster as priming instances (if forecaster is incrementally
    // primeable)
    // or into the buffered prime/training data if forecaster is not
    // incrementally
    // primeable or we are rebuilding/re-estimating the model
    if ( m_isUsingOverlayData ) {
      boolean allMissing = true;
      for ( String field : m_fieldsToForecast ) {
        int index = getInputRowMeta().indexOfValue( field );
        ValueMetaInterface tempField = getInputRowMeta().getValueMeta( index );
        if ( !tempField.isNull( r[index] ) ) {
          allMissing = false;
          break;
        }
      }

      // add it into the overlay buffer
      if ( allMissing ) {
        m_overlayData.add( r );
        logBasic( "Buffering overlay row" );
      }

      if ( !allMissing ) {
        // check the overlay buffer - if it's not empty then flush it
        // into either the forecaster directly (if incrementally primeable)
        // or into the priming buffer
        if ( m_overlayData.size() > 0 ) {

          // first buffer this one (will get flushed anyway)
          m_overlayData.add( r );
          logBasic( "Encountered an supposed overlay instance with non-missing "
              + "target values - converting buffered overlay data into " + ( m_rebuildModel ? "training" : "priming" )
              + " data..." );

          for ( int i = 0; i < m_overlayData.size(); i++ ) {
            if ( m_isIncrementallyPrimeable && !m_rebuildModel ) {
              /*
               * Instance converted =
               * m_data.constructInstance(getInputRowMeta(), r,
               * m_meta.getMappingIndexes(), m_meta.getModel());
               */
              Instance
                  converted =
                  m_data.constructInstance( getInputRowMeta(), m_overlayData.get( i ), m_meta.getMappingIndexes(),
                      m_meta.getModel() );
              try {
                ( (IncrementallyPrimeable) m_meta.getModel().getModel() ).primeForecasterIncremental( converted );

                // output this row immediately (make sure we include any fields
                // for confidence intervals (these will be necessarily
                // null/missing
                // for historical instances
                Object[] outputRow = RowDataUtil.resizeArray( r, m_data.getOutputRowMeta().size() );
                m_data.fixTypesForTargets( outputRow, m_fieldsToForecast, getInputRowMeta() );

                // set the forecasted flag
                int flagIndex = m_data.getOutputRowMeta().indexOfValue( "Forecasted" );
                outputRow[flagIndex] = new Boolean( false );

                putRow( m_data.getOutputRowMeta(), outputRow );
              } catch ( Exception e ) {
                e.printStackTrace();
                throw new KettleException( e );
              }
            } else {
              // transfer to the priming buffer
              // m_bufferedPrimeData.add(r);
              m_bufferedPrimeData.add( m_overlayData.get( i ) );
            }
          }

          // clear out the buffer
          m_overlayData.clear();
        } else {
          // not all missing and overlay buffer is empty then it's a priming
          // instance

          // either buffer it or send it directly to the forecaster (if
          // incrementally
          // primeable
          if ( m_isIncrementallyPrimeable && !m_rebuildModel ) {
            Instance
                converted =
                m_data.constructInstance( getInputRowMeta(), r, m_meta.getMappingIndexes(), m_meta.getModel() );
            // System.err.println("-------- converterd prime " + converted);
            try {
              ( (IncrementallyPrimeable) m_meta.getModel().getModel() ).primeForecasterIncremental( converted );

              // output this row immediately (make sure we include any fields
              // for confidence intervals (these will be necessarily
              // null/missing
              // for historical instances
              Object[] outputRow = RowDataUtil.resizeArray( r, m_data.getOutputRowMeta().size() );
              m_data.fixTypesForTargets( outputRow, m_fieldsToForecast, getInputRowMeta() );

              // set the forecasted flag
              int flagIndex = m_data.getOutputRowMeta().indexOfValue( "Forecasted" );
              outputRow[flagIndex] = new Boolean( false );

              putRow( m_data.getOutputRowMeta(), outputRow );
            } catch ( Exception e ) {
              e.printStackTrace();
              throw new KettleException( e );
            }
          } else {
            // buffer
            m_bufferedPrimeData.add( r );
          }

        }
      }
    } else { // not using overlay data
      // either buffer it or send it directly to the forecaster (if
      // incrementally
      // primeable and not rebuilding/re-estimating the model)
      if ( m_isIncrementallyPrimeable && !m_rebuildModel ) {
        Instance
            converted =
            m_data.constructInstance( getInputRowMeta(), r, m_meta.getMappingIndexes(), m_meta.getModel() );
        // System.err.println("-------- converterd prime " + converted);
        try {
          ( (IncrementallyPrimeable) m_meta.getModel().getModel() ).primeForecasterIncremental( converted );

          // output this row immediately (make sure we include any fields
          // for confidence intervals (these will be necessarily null/missing
          // for historical instances
          Object[] outputRow = RowDataUtil.resizeArray( r, m_data.getOutputRowMeta().size() );
          m_data.fixTypesForTargets( outputRow, m_fieldsToForecast, getInputRowMeta() );

          // set the forecasted flag
          int flagIndex = m_data.getOutputRowMeta().indexOfValue( "Forecasted" );
          outputRow[flagIndex] = new Boolean( false );

          putRow( m_data.getOutputRowMeta(), outputRow );
        } catch ( Exception e ) {
          e.printStackTrace();
          throw new KettleException( e );
        }
      } else {
        // buffer
        m_bufferedPrimeData.add( r );
      }
    }

    if ( log.isRowLevel() ) {
      log.logRowlevel( toString(), "Read row #" + linesRead + " : " + r );
    }

    if ( checkFeedback( linesRead ) ) {
      logBasic( "Linenr " + linesRead );
    }
    return true;
  }

  /**
   * Initialize the step.
   *
   * @param smi a <code>StepMetaInterface</code> value
   * @param sdi a <code>StepDataInterface</code> value
   * @return a <code>boolean</code> value
   */
  @Override public boolean init( StepMetaInterface smi, StepDataInterface sdi ) {
    m_meta = (PMIForecastingMeta) smi;
    m_data = (PMIForecastingData) sdi;

    if ( super.init( smi, sdi ) ) {
      return true;
    }
    return false;
  }
}
