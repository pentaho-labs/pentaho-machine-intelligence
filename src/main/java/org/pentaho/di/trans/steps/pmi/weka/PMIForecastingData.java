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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

import weka.classifiers.evaluation.NumericPrediction;
import weka.classifiers.timeseries.AbstractForecaster;
import weka.classifiers.timeseries.TSForecaster;
import weka.classifiers.timeseries.core.OverlayForecaster;
import weka.core.SerializationHelper;
import weka.filters.supervised.attribute.TSLagMaker;
import weka.classifiers.timeseries.core.TSLagUser;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

/**
 * Holds temporary data and has routines for loading serialized forecasting models.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision$
 */
public class PMIForecastingData extends BaseStepData implements StepDataInterface {

  // some constants for various input field - attribute match/type
  // problems
  public static final int NO_MATCH = -1;
  public static final int TYPE_MISMATCH = -2;

  // this class contains intermediate results,
  // info about the input format, derived output
  // format etc.

  // the output data format
  protected RowMetaInterface m_outputRowMeta;

  // holds values for instances constructed for prediction
  private double[] m_vals = null;

  public PMIForecastingData() {
    super();
  }

  /**
   * Get the meta data for the output format
   *
   * @return a <code>RowMetaInterface</code> value
   */
  public RowMetaInterface getOutputRowMeta() {
    return m_outputRowMeta;
  }

  /**
   * Set the meta data for the output format
   *
   * @param rmi a <code>RowMetaInterface</code> value
   */
  public void setOutputRowMeta( RowMetaInterface rmi ) {
    m_outputRowMeta = rmi;
  }

  /**
   * Fixes the type of forecasted fields (if necessary). A forecaster forecasts
   * target values as doubles. If incoming target fields values are non-double
   * numeric types (as they might be for historical priming rows), then values
   * need to be converted to Double to match the output row meta data.
   *
   * @param row          row to check
   * @param targetFields list of target fields predicted by the forecaster
   * @param inputRowMeta the input row meta data
   * @throws KettleException if a problem occurs.
   */
  public void fixTypesForTargets( Object[] row, List<String> targetFields, RowMetaInterface inputRowMeta )
      throws KettleException {
    for ( String target : targetFields ) {
      int index = inputRowMeta.indexOfValue( target );

      // check type
      if ( !( row[index] instanceof Double ) ) {
        ValueMetaInterface v = inputRowMeta.getValueMeta( index );
        Double newVal = v.getNumber( row[index] );
        row[index] = newVal;
      }
    }
  }

  public boolean sortCheck( TSForecaster forecaster, Instances data ) throws KettleException {

    boolean ok = true;

    if ( forecaster instanceof TSLagUser ) {
      // see what sort (if any) time stamp is in use
      TSLagMaker lagMaker = ( (TSLagUser) forecaster ).getTSLagMaker();

      if ( lagMaker.getAdjustForTrends() && !lagMaker.isUsingAnArtificialTimeIndex() && !Const
          .isEmpty( lagMaker.getTimeStampField() ) ) {

        String timeStampName = lagMaker.getTimeStampField();
        if ( data.attribute( timeStampName ) == null ) {
          throw new KettleException(
              "[WekaForecastingData] can't find the time " + "stamp field \"" + timeStampName + "\" in the data!" );
        }

        int timeIndex = data.attribute( timeStampName ).index();
        Instance previous = null;
        for ( int i = 0; i < data.numInstances(); i++ ) {
          Instance current = data.instance( i );
          if ( previous == null && !current.isMissing( timeIndex ) ) {
            previous = current;
          } else {
            if ( !previous.isMissing( timeIndex ) && !current.isMissing( timeIndex ) ) {
              if ( current.value( timeIndex ) < previous.value( timeIndex ) ) {
                ok = false;
                break;
              }
            }
            if ( !current.isMissing( timeIndex ) ) {
              previous = current;
            }
          }
        }
      }
    }

    return ok;
  }

  /**
   * Loads a serialized model. Models can either be binary serialized Java
   * objects, objects deep-serialized to xml, or PMML.
   *
   * @param modelFile a <code>File</code> value
   * @return the model
   * @throws Exception if there is a problem laoding the model.
   */
  public static WekaForecastingModel loadSerializedModel( File modelFile, LogChannelInterface log ) throws Exception {

    Object model = null;
    Instances header = null;

    InputStream is = new FileInputStream( modelFile );
    if ( modelFile.getName().toLowerCase().endsWith( ".gz" ) ) {
      is = new GZIPInputStream( is );
    }
    ObjectInputStream oi = SerializationHelper.getObjectInputStream( is );

    model = oi.readObject();

    // try and grab the header
    header = (Instances) oi.readObject();

    oi.close();

    if ( !( model instanceof TSForecaster ) ) {
      log.logError( "[WekaForecastingData] " + BaseMessages
          .getString( PMIForecastingMeta.PKG, "PMIForecastingMeta.Log.ModelIsNotAForecaster" ) );
      throw new Exception( "[WekaForecastingData] " + BaseMessages
          .getString( PMIForecastingMeta.PKG, "PMIForecastingMeta.Log.ModelIsNotAForecaster" ) );
    }

    // System.err.println(header);

    WekaForecastingModel wsm = new WekaForecastingModel( (TSForecaster) model );
    wsm.setHeader( header );

    wsm.setLog( log );
    return wsm;
  }

  public static void saveSerializedModel( WekaForecastingModel wsm, File saveTo ) throws Exception {

    Object model = wsm.getModel();
    Instances header = wsm.getHeader();
    OutputStream os = new FileOutputStream( saveTo );

    if ( saveTo.getName().toLowerCase().endsWith( ".gz" ) ) {
      os = new GZIPOutputStream( os );
    }
    ObjectOutputStream oos = new ObjectOutputStream( new BufferedOutputStream( os ) );

    oos.writeObject( model );
    oos.writeObject( header );
    oos.close();
  }

  /**
   * Finds a mapping between the attributes that a forecasting model has been
   * trained with and the incoming Kettle row format. Returns an array of
   * indices, where the element at index 0 of the array is the index of the
   * Kettle field that corresponds to the first attribute in the Instances
   * structure, the element at index 1 is the index of the Kettle fields that
   * corresponds to the second attribute, ...
   *
   * @param header       the Instances header
   * @param inputRowMeta the meta data for the incoming rows
   * @return the mapping as an array of integer indices
   */
  public static int[] findMappings( Instances header, RowMetaInterface inputRowMeta ) {
    // Instances header = m_model.getHeader();
    int[] mappingIndexes = new int[header.numAttributes()];

    HashMap<String, Integer> inputFieldLookup = new HashMap<String, Integer>();
    for ( int i = 0; i < inputRowMeta.size(); i++ ) {
      ValueMetaInterface inField = inputRowMeta.getValueMeta( i );
      inputFieldLookup.put( inField.getName(), Integer.valueOf( i ) );
    }

    // check each attribute in the header against what is incoming
    for ( int i = 0; i < header.numAttributes(); i++ ) {
      Attribute temp = header.attribute( i );
      String attName = temp.name();

      // look for a matching name
      Integer matchIndex = inputFieldLookup.get( attName );
      boolean ok = false;
      int status = NO_MATCH;
      if ( matchIndex != null ) {
        // check for type compatibility
        ValueMetaInterface tempField = inputRowMeta.getValueMeta( matchIndex.intValue() );
        if ( tempField.isNumeric() || tempField.isBoolean() ) {
          if ( temp.isNumeric() ) {
            ok = true;
            status = 0;
          } else {
            status = TYPE_MISMATCH;
          }
        } else if ( tempField.isString() ) {
          if ( temp.isNominal() ) {
            ok = true;
            status = 0;
            // All we can assume is that this input field is ok.
            // Since we wont know what the possible values are
            // until the data is pumping through, we will defer
            // the matching of legal values until then
          } else {
            status = TYPE_MISMATCH;
          }
        } else if ( tempField.isDate() ) {
          if ( temp.isDate() ) {
            ok = true;
            status = 0;
          } else {
            status = TYPE_MISMATCH;
          }
        } else {
          // any other type is a mismatch (might be able to do
          // something with dates at some stage)
          status = TYPE_MISMATCH;
        }
      }
      if ( ok ) {
        mappingIndexes[i] = matchIndex.intValue();
      } else {
        // mark this attribute as missing or type mismatch
        mappingIndexes[i] = status;
      }
    }
    return mappingIndexes;
  }

  /**
   * Generates a forecast given a forecasting model (sourced from the meta
   * object).
   *
   * @param inputMeta   the incoming row meta data
   * @param outputMeta  the outgoing row meta data
   * @param meta        the forecasting meta
   * @param overlayData a list of rows for future time steps (in the same format
   *                    as the incoming rows) containing values for "overlay" fields. May
   *                    be null if overlay data is not in use.
   * @return a List of rows containing the forecast.
   * @throws Exception if a problem occurs.
   */
  public List<Object[]> generateForecast( RowMetaInterface inputMeta, RowMetaInterface outputMeta,
      PMIForecastingMeta meta, List<Object[]> overlayData, TransMeta transMeta, PrintStream... progress )
      throws Exception {

    int[] mappingIndexes = meta.getMappingIndexes();
    WekaForecastingModel model = meta.getModel();

    String timeStampName = "";
    TSLagMaker lagMaker = null;
    if ( model.getModel() instanceof TSLagUser ) {
      lagMaker = ( (TSLagUser) model.getModel() ).getTSLagMaker();
      if ( !lagMaker.isUsingAnArtificialTimeIndex() && lagMaker.getAdjustForTrends() ) {
        timeStampName = lagMaker.getTimeStampField();
      }
    }

    double lastTimeFromPrime = -1; // this matters not if we're not using a time
    // stamp
    if ( lagMaker != null ) {
      if ( lagMaker.getAdjustForTrends() && lagMaker.getTimeStampField() != null
          && lagMaker.getTimeStampField().length() > 0 && !lagMaker.isUsingAnArtificialTimeIndex() ) {

        lastTimeFromPrime = lagMaker.getCurrentTimeStampValue();
      } else if ( lagMaker.getAdjustForTrends() && lagMaker.isUsingAnArtificialTimeIndex() ) {

        // If an artificial time stamp is in use then we need to set the
        // initial value to whatever offset from training that the user has
        // indicated to be the first forecasted point.
        double artificialStartValue = lagMaker.getArtificialTimeStartValue();
        String art = transMeta.environmentSubstitute( meta.getArtificialTimeStartOffset() );
        artificialStartValue += Integer.parseInt( art );
        lagMaker.setArtificialTimeStartValue( artificialStartValue );
      }
    }

    boolean
        overlay =
        ( overlayData != null && overlayData.size() > 0 && model.getModel() instanceof OverlayForecaster
            && ( (OverlayForecaster) model.getModel() ).isUsingOverlayData() );

    String numS = transMeta.environmentSubstitute( meta.getNumStepsToForecast() );
    int numSteps = ( overlay ) ? overlayData.size() : Integer.parseInt( numS );
    Instances overlayAsInstances = null;

    if ( overlay ) {
      overlayAsInstances = new Instances( model.getHeader(), 0 );
      for ( int i = 0; i < overlayData.size(); i++ ) {
        Instance converted = constructInstance( inputMeta, overlayData.get( i ), mappingIndexes, model );
        overlayAsInstances.add( converted );
      }
    }

    List<String> fieldsToForecast = AbstractForecaster.stringToList( model.getModel().getFieldsToForecast() );

    List<Object[]> convertedForecast = new ArrayList<Object[]>();

    List<List<NumericPrediction>> forecast = null;
    if ( model.getModel() instanceof OverlayForecaster ) {
      forecast = ( (OverlayForecaster) model.getModel() ).forecast( numSteps, overlayAsInstances, progress );
    } else {
      forecast = model.getModel().forecast( numSteps );
    }

    // now convert forecast into row format. If we have overlay data
    // then we can just fill in the forecasted values (and potentially add
    // for confidence intervals)
    // since the overlay rows contain all incoming fields; otherwise we
    // need to construct rows from scratch
    double time = lastTimeFromPrime;
    int timeStampIndex = -1;
    if ( timeStampName.length() > 0 ) {
      timeStampIndex = outputMeta.indexOfValue( timeStampName );
      if ( timeStampIndex < 0 ) {
        throw new Exception( "[WekaForecastingData] Oh oh, couldn't find time " + "stamp: " + timeStampName
            + " in the input row meta data" );
      }
    }

    for ( int i = 0; i < numSteps; i++ ) {
      Object[] currentRow = ( overlay ) ? overlayData.get( i ) : new Object[outputMeta.size()];
      List<NumericPrediction> predsForStep = forecast.get( i );

      if ( model.isProducingConfidenceIntervals() ) {
        // resize for confidence intervals
        currentRow = RowDataUtil.resizeArray( currentRow, outputMeta.size() );

        // set the forecasted flag
        int flagIndex = outputMeta.indexOfValue( "Forecasted" );
        currentRow[flagIndex] = new Boolean( true );
      }

      if ( timeStampIndex != -1 ) {
        // set time value
        time = lagMaker.advanceSuppliedTimeValue( time );
        if ( outputMeta.getValueMeta( timeStampIndex ).isDate() ) {
          Object d = new Date();
          ( (Date) d ).setTime( (long) time );

          // convert to binary storage type if necessary
          if ( outputMeta.getValueMeta( timeStampIndex ).getStorageType()
              == ValueMetaInterface.STORAGE_TYPE_BINARY_STRING ) {
            d = outputMeta.getValueMeta( timeStampIndex ).convertToBinaryStringStorageType( d );
          }
          currentRow[timeStampIndex] = d;
        } else {
          // convert to binary storage type if necessary
          Object t = new Double( time );
          if ( outputMeta.getValueMeta( timeStampIndex ).getStorageType()
              == ValueMetaInterface.STORAGE_TYPE_BINARY_STRING ) {
            t = outputMeta.getValueMeta( timeStampIndex ).convertToBinaryStringStorageType( t );
          }
          currentRow[timeStampIndex] = t;
        }
      }

      for ( int j = 0; j < fieldsToForecast.size(); j++ ) {
        String target = fieldsToForecast.get( j );
        int index = outputMeta.indexOfValue( target );
        if ( index < 0 ) {
          throw new Exception(
              "[WekaForecastingData] Oh oh, couldn't find target: " + target + " in the input row meta data!" );
        }

        NumericPrediction predForTargetAtStep = predsForStep.get( j );
        double y = predForTargetAtStep.predicted();
        double yHigh = y;
        double yLow = y;
        double[][] conf = predForTargetAtStep.predictionIntervals();
        if ( !Utils.isMissingValue( y ) ) {
          currentRow[index] = new Double( y );
        }

        // any confidence bounds?
        if ( conf.length > 0 ) {
          yLow = conf[0][0];
          yHigh = conf[0][1];
          int indexOfLow = outputMeta.indexOfValue( target + "_lowerBound" );
          int indexOfHigh = outputMeta.indexOfValue( target + "_upperBound" );
          currentRow[indexOfLow] = new Double( yLow );
          currentRow[indexOfHigh] = new Double( yHigh );
        }
      }
      convertedForecast.add( currentRow );
    }

    // need to construct an Instance to represent this
    // input row
    /*
     * Instance toScore = constructInstance(inputMeta, inputRow, mappingIndexes,
     * model); double[] prediction = model.distributionForInstance(toScore);
     * 
     * // Update the model?? if (meta.getUpdateIncrementalModel() &&
     * model.isUpdateableModel() && !toScore.isMissing(toScore.classIndex())) {
     * model.update(toScore); } // First copy the input data to the new
     * result... Object[] resultRow = RowDataUtil.resizeArray(inputRow,
     * outputMeta.size()); int index = inputMeta.size();
     * 
     * // output for numeric class or discrete class value if (prediction.length
     * == 1 || !outputProbs) { if (supervised) { if (classAtt.isNumeric()) {
     * Double newVal = new Double(prediction[0]); resultRow[index++] = newVal; }
     * else { int maxProb = Utils.maxIndex(prediction); if (prediction[maxProb]
     * > 0) { String newVal = classAtt.value(maxProb); resultRow[index++] =
     * newVal; } else { String newVal = "Unable to predict"; resultRow[index++]
     * = newVal; } } } else { int maxProb = Utils.maxIndex(prediction); if
     * (prediction[maxProb] > 0) { Double newVal = new Double(maxProb);
     * resultRow[index++] = newVal; } else { String newVal =
     * "Unable to assign cluster"; resultRow[index++] = newVal; } } } else { //
     * output probability distribution for (int i = 0; i <
     * prediction.length;i++) { Double newVal = new Double(prediction[i]);
     * resultRow[index++] = newVal; } }
     * 
     * // resultRow[index] = " ";
     * 
     * return resultRow;
     */

    return convertedForecast;
  }

  /**
   * Helper method that constructs an Instance to input to the Weka model based
   * on incoming Kettle fields and pre-constructed attribute-to-field mapping
   * data.
   *
   * @param inputMeta      a <code>RowMetaInterface</code> value
   * @param inputRow       an <code>Object</code> value
   * @param mappingIndexes an <code>int</code> value
   * @param model          a <code>WekaScoringModel</code> value
   * @return an <code>Instance</code> value
   */
  protected Instance constructInstance( RowMetaInterface inputMeta, Object[] inputRow, int[] mappingIndexes,
      WekaForecastingModel model ) {

    Instances header = model.getHeader();

    m_vals = new double[header.numAttributes()];

    for ( int i = 0; i < header.numAttributes(); i++ ) {

      if ( mappingIndexes[i] >= 0 ) {
        try {
          Object inputVal = inputRow[mappingIndexes[i]];

          Attribute temp = header.attribute( i );
          // String attName = temp.name();
          ValueMetaInterface tempField = inputMeta.getValueMeta( mappingIndexes[i] );
          int fieldType = tempField.getType();

          // if (inputVal == null) {

          // Check for missing value (null or empty string)
          if ( tempField.isNull( inputVal ) ) {
            m_vals[i] = Utils.missingValue();
            continue;
          }

          switch ( temp.type() ) {
            case Attribute.NUMERIC:
              if ( fieldType == ValueMetaInterface.TYPE_BOOLEAN ) {
                Boolean b = tempField.getBoolean( inputVal );
                if ( b.booleanValue() ) {
                  m_vals[i] = 1.0;
                } else {
                  m_vals[i] = 0.0;
                }
              } else if ( fieldType == ValueMetaInterface.TYPE_INTEGER ) {
                Long t = tempField.getInteger( inputVal );
                m_vals[i] = t.longValue();
              } else {
                Double n = tempField.getNumber( inputVal );
                m_vals[i] = n.doubleValue();
              }

              break;
            case Attribute.NOMINAL:
              String s = tempField.getString( inputVal );
              // now need to look for this value in the attribute
              // in order to get the correct index
              int index = temp.indexOfValue( s );
              if ( index < 0 ) {
                // set to missing value
                m_vals[i] = Utils.missingValue();
              } else {
                m_vals[i] = index;
              }
              break;
            case Attribute.DATE:
              Date d = tempField.getDate( inputVal );
              m_vals[i] = d.getTime();
              break;
            default:
              // System.err.println("Missing - default " + i);
              m_vals[i] = Utils.missingValue();
          }
        } catch ( Exception e ) {
          // System.err.println("Exception - missing " + i);
          m_vals[i] = Utils.missingValue();
        }
      } else {
        // set to missing value
        // System.err.println("Unmapped " + i);
        m_vals[i] = Utils.missingValue();
      }

      // m_vals[i] = Instance.missingValue();
    }

    /*
     * for (int i = 0; i < header.numAttributes(); i++) { if (mappingIndexes[i]
     * >= 0) { Object inputVal = inputRow[mappingIndexes[i]]; if (inputVal ==
     * null) { // set missing m_vals[i] = Instance.missingValue(); continue; }
     * Attribute temp = header.attribute(i); // String attName = temp.name();
     * ValueMetaInterface tempField = inputMeta.getValueMeta(mappingIndexes[i]);
     * 
     * // Quick check for type mismatch // (i.e. string occuring in what was
     * thought to be // a numeric incoming field if (temp.isNumeric()) { if
     * (!tempField.isBoolean() && !tempField.isNumeric()) { m_vals[i] =
     * Instance.missingValue(); continue; } } else { if (!tempField.isString())
     * { m_vals[i] = Instance.missingValue(); continue; } }
     * 
     * int fieldType = tempField.getType();
     * 
     * try { switch(fieldType) { case ValueMetaInterface.TYPE_BOOLEAN: { Boolean
     * b = tempField.getBoolean(inputVal); if (b.booleanValue()) { m_vals[i] =
     * 1.0; } else { m_vals[i] = 0.0; } } break;
     * 
     * case ValueMetaInterface.TYPE_NUMBER: case
     * ValueMetaInterface.TYPE_INTEGER: { Number n =
     * tempField.getNumber(inputVal); m_vals[i] = n.doubleValue(); } break;
     * 
     * case ValueMetaInterface.TYPE_STRING: { String s =
     * tempField.getString(inputVal); // now need to look for this value in the
     * attribute // in order to get the correct index int index =
     * temp.indexOfValue(s); if (index < 0) { // set to missing value m_vals[i]
     * = Instance.missingValue(); } else { m_vals[i] = (double)index; } } break;
     * 
     * default: // for unsupported type set to missing value m_vals[i] =
     * Instance.missingValue(); break; } } catch (Exception ex) { // quietly
     * ignore -- set to missing anything that // is not parseable as the
     * expected type m_vals[i] = Instance.missingValue(); } } else { // set to
     * missing value m_vals[i] = Instance.missingValue(); } }
     */
    Instance newInst = new DenseInstance( 1.0, m_vals );
    newInst.setDataset( header );
    return newInst;
  }
}
