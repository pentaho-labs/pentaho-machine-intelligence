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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Counter;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMeta;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.repository.kdr.KettleDatabaseRepository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.w3c.dom.Node;

import weka.classifiers.timeseries.AbstractForecaster;
import weka.core.Instances;
import weka.core.SerializedObject;

/**
 * Contains the meta data for the WekaForecasting step.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision$
 */
@Step( id = "PMIForecasting", image = "WEKAS.svg", documentationUrl = "http://wiki.pentaho.com/display/EAI/Weka+Forecasting", name = "PMI Forecasting", description = "This step makes time series forecasts from a pre-built WEKA forecasting model", categoryDescription = "Data Mining" )
public class PMIForecastingMeta extends BaseStepMeta implements StepMetaInterface {

  public static Class<?> PKG = PMIForecastingMeta.class;

  public static final String XML_TAG = "weka_forecasting";

  /**
   * File name of the serialized Weka model to load/import
   */
  private String m_modelFileName;

  /**
   * The number of steps to forecast into the future
   */
  private String m_numSteps = "1";

  /**
   * If using an artificial time stamp, this is the number of historical priming
   * instances presented to the forecaster that occur after the last training
   * instance in time
   */
  private String m_artificialTimeStartOffset = "0";

  /**
   * Holds the actual forecaster
   */
  private WekaForecastingModel m_model;

  /**
   * used to map attribute indices to incoming field indices
   */
  private int[] m_mappingIndexes;

  /**
   * Whether to rebuild the model on the incoming data stream
   */
  private boolean m_rebuildModel;

  /**
   * If rebuilding the model, save it out to this named file
   */
  private String m_savedForecasterFileName;

  // logging
  // protected LogChannelInterface m_log;

  /**
   * Creates a new <code>PMIForecastingMeta</code> instance.
   */
  public PMIForecastingMeta() {
    super(); // allocate BaseStepMeta
  }

  /**
   * Set whether to rebuild the forecaster on the incoming data stream.
   *
   * @param rebuild true if the forecaster is to be rebuilt.
   */
  public void setRebuildForecaster( boolean rebuild ) {
    m_rebuildModel = rebuild;
  }

  /**
   * Gets whether the forecaster is to be rebuilt/re-estimated on the the
   * incoming data stream.
   *
   * @return true if the forecaster is to be rebuilt/re-estimated.
   */
  public boolean getRebuildForecaster() {
    return m_rebuildModel;
  }

  /**
   * Set the name of the file to save the forecaster out to or null not to save
   * the forecaster. The model will only be saved if the rebuild forecaster
   * property is set.
   *
   * @param name the name of the file to save the rebuilt forecaster to.
   */
  public void setSavedForecasterFileName( String name ) {
    m_savedForecasterFileName = name;
  }

  /**
   * Get the name of the file to save the forecaster out to. May return null -
   * this indicates not to save.
   *
   * @return the name of the file to save to or null if not to save.
   */
  public String getSavedForecasterFileName() {
    return m_savedForecasterFileName;
  }

  /**
   * Set the number of steps to forecast into the future
   *
   * @param numSteps the number of steps to forecast
   */
  public void setNumStepsToForecast( String numSteps ) {
    m_numSteps = numSteps;
  }

  /**
   * Get the number of steps to forecast into the future
   *
   * @return the number of steps to forecast
   */
  public String getNumStepsToForecast() {
    return m_numSteps;
  }

  /**
   * Set the offset, from the value associated with the last training instance,
   * for the artificial time stamp. Has no effect if an artificial time stamp is
   * not in use by the forecaster. If in use, this needs to be set so that the
   * forecaster knows what time stamp value corresponds to the first requested
   * forecast (i.e. it should be equal to the number of recent historical
   * priming instances that occur after the last training instance in time).
   *
   * @param art the offset from the last artificial time value in the training
   *            data for which the forecast is requested.
   */
  public void setArtificialTimeStartOffset( String art ) {
    m_artificialTimeStartOffset = art;
  }

  /**
   * Get the offset, from the value associated with the last training instance,
   * for the artificial time stamp. Has no effect if an artificial time stamp is
   * not in use by the forecaster. If in use, this needs to be set so that the
   * forecaster knows what time stamp value corresponds to the first requested
   * forecast (i.e. it should be equal to the number of recent historical
   * priming instances that occur after the last training instance in time).
   *
   * @return the offset from the last artificial time value in the training data
   * for which the forecast is requested.
   */
  public String getArtificialTimeStartOffset() {
    return m_artificialTimeStartOffset;
  }

  /**
   * Set the file name of the serialized Weka model to load/import from
   *
   * @param mfile the file name
   */
  public void setSerializedModelFileName( String mfile ) {
    m_modelFileName = mfile;
  }

  /**
   * Get the filename of the serialized Weka model to load/import from
   *
   * @return the file name
   */
  public String getSerializedModelFileName() {
    return m_modelFileName;
  }

  /**
   * Set the forecasting model
   *
   * @param model a <code>WekaForecastingModel</code>
   */
  public void setModel( WekaForecastingModel model ) {
    m_model = model;
  }

  /**
   * Get the forecasting model
   *
   * @return a <code>TSForecaster</code>
   */
  public WekaForecastingModel getModel() {
    return m_model;
  }

  /**
   * Finds a mapping between the attributes that a forecasting model model has
   * seen during training and the incoming row format. Returns an array of
   * indices, where the element at index 0 of the array is the index of the
   * Kettle field that corresponds to the first attribute in the Instances
   * structure, the element at index 1 is the index of the Kettle fields that
   * corresponds to the second attribute, ...
   *
   * @param header       the Instances header
   * @param inputRowMeta the meta data for the incoming rows
   */
  public void mapIncomingRowMetaData( Instances header, RowMetaInterface inputRowMeta ) {
    m_mappingIndexes = PMIForecastingData.findMappings( header, inputRowMeta );
  }

  /**
   * Get the mapping from attributes to incoming Kettle fields
   *
   * @return the mapping as an array of integer indices
   */
  public int[] getMappingIndexes() {
    return m_mappingIndexes;
  }

  /**
   * Return the XML describing this (configured) step
   *
   * @return a <code>String</code> containing the XML
   */
  @Override public String getXML() {

    StringBuffer retval = new StringBuffer( 100 );

    retval.append( "<" + XML_TAG + ">" );

    retval.append( XMLHandler.addTagValue( "num_steps", m_numSteps ) );
    retval.append( XMLHandler.addTagValue( "artificial_offset", m_artificialTimeStartOffset ) );

    WekaForecastingModel temp = m_model;

    // can we save the model as XML?
    if ( temp != null && Const.isEmpty( m_modelFileName ) ) {

      try {
        // Convert model to base64 encoding
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream( bao );
        ObjectOutputStream oo = new ObjectOutputStream( bos );
        oo.writeObject( temp );
        oo.flush();
        byte[] model = bao.toByteArray();
        String base64model = XMLHandler.addTagValue( "weka_forecasting_model", model );

        System.out.println( "Serializing  model." );
        System.out.println(
            BaseMessages.getString( PKG, "PMIForecastingMeta.Log.SizeOfModel" ) + " " + base64model.length() );
        // System.err.println("Size of base64 model "+base64model.length());
        retval.append( base64model );
        oo.close();
      } catch ( Exception ex ) {
        System.out.println( BaseMessages.getString( PKG, "PMIForecastingMeta.Log.Base64SerializationProblem" ) );
        // System.err.println("Problem serializing model to base64 (Meta.getXML())");
      }
    } else {
      if ( !Const.isEmpty( m_modelFileName ) ) {
        /*
         * m_log.logBasic("[PMIForecastingMeta] ",
         * Messages.getString("PMIForecastingMeta.Log.ModelSourcedFromFile") +
         * " " + m_modelFileName);
         */

        System.out.println(
            BaseMessages.getString( PKG, "PMIForecastingMeta.Log.ModelSourcedFromFile" ) + " " + m_modelFileName );

        /*
         * logBasic(Messages.getString(
         * "PMIForecastingMeta.Log.ModelSourcedFromFile") + " " +
         * m_modelFileName);
         */
        // logBasic(lm);
      }
      /*
       * System.err.println("Model will be sourced from file " +
       * m_modelFileName);
       */
      // save the model file name
      retval.append( XMLHandler.addTagValue( "model_file_name", m_modelFileName ) );

      retval.append( XMLHandler.addTagValue( "rebuild_model", m_rebuildModel ) );

      if ( !Const.isEmpty( m_savedForecasterFileName ) ) {
        retval.append( XMLHandler.addTagValue( "save_file_name", m_savedForecasterFileName ) );
      }
    }

    retval.append( "</" + XML_TAG + ">" );
    return retval.toString();
  }

  /**
   * Check for equality
   *
   * @param obj an <code>Object</code> to compare with
   * @return true if equal to the supplied object
   */
  @Override public boolean equals( Object obj ) {
    if ( obj != null && ( obj.getClass().equals( this.getClass() ) ) ) {
      PMIForecastingMeta m = (PMIForecastingMeta) obj;
      return ( getXML() == m.getXML() );
    }

    return false;
  }

  /**
   * Hash code method
   *
   * @return the hash code for this object
   */
  @Override public int hashCode() {
    return getXML().hashCode();
  }

  /**
   * Clone this step's meta data
   *
   * @return the cloned meta data
   */
  @Override public Object clone() {
    PMIForecastingMeta retval = (PMIForecastingMeta) super.clone();
    // deep copy the model (if any)
    if ( m_model != null ) {
      try {
        SerializedObject so = new SerializedObject( m_model );
        WekaForecastingModel copy = (WekaForecastingModel) so.getObject();
        // copy.setLog(getLog());
        retval.setModel( copy );
      } catch ( Exception ex ) {
        logError( BaseMessages.getString( PKG, "PMIForecastingMeta.Log.DeepCopyingError" ) );
        // System.err.println("Problem deep copying scoring model (meta.clone())");
      }
    }

    return retval;
  }

  @Override public void setDefault() {
    m_modelFileName = null;
  }

  /**
   * Loads the meta data for this (configured) step from XML.
   *
   * @param stepnode the step to load
   * @throws KettleXMLException if an error occurs
   */
  @Override public void loadXML( Node stepnode, List<DatabaseMeta> databases, Map<String, Counter> counters )
      throws KettleXMLException {
    int nrModels = XMLHandler.countNodes( stepnode, XML_TAG );

    if ( nrModels > 0 ) {
      Node wekanode = XMLHandler.getSubNodeByNr( stepnode, XML_TAG, 0 );

      m_numSteps = XMLHandler.getTagValue( wekanode, "num_steps" );
      /*
       * String numSteps = XMLHandler.getTagValue(wekanode, "num_steps"); try {
       * m_numSteps = Integer.parseInt(numSteps); } catch (NumberFormatException
       * ex) { throw new
       * KettleXMLException("Can't parse the number of steps to forecast" +
       * " from the XML definition!"); }
       */

      m_artificialTimeStartOffset = XMLHandler.getTagValue( wekanode, "artificial_offset" );
      /*
       * String offset = XMLHandler.getTagValue(wekanode, "artificial_offset");
       * try { m_artificialTimeStartOffset = Integer.parseInt(offset); } catch
       * (NumberFormatException ex) { throw new
       * KettleXMLException("Can't parse the artificial time offeset " +
       * " from the XML definition!"); }
       */

      // try and get the XML-based model
      boolean success = false;
      try {
        String base64modelXML = XMLHandler.getTagValue( wekanode, "weka_forecasting_model" );
        // System.err.println("Got base64 string...");
        // System.err.println(base64modelXML);
        deSerializeBase64Model( base64modelXML );
        success = true;

        logBasic( "Deserializing model." );
        // System.err.println("Successfully de-serialized model!");
        logDetailed( BaseMessages.getString( PKG, "PMIForecastingMeta.Log.DeserializationSuccess" ) );
      } catch ( Exception ex ) {
        success = false;
      }

      if ( !success ) {
        // fall back and try and grab a model file name
        m_modelFileName = XMLHandler.getTagValue( wekanode, "model_file_name" );
      }

      String temp = XMLHandler.getTagValue( wekanode, "rebuild_model" );
      if ( temp == null || temp.equalsIgnoreCase( "N" ) ) {
        m_rebuildModel = false;
      } else {
        m_rebuildModel = true;
      }

      String fileName = XMLHandler.getTagValue( wekanode, "save_file_name" );
      if ( !Const.isEmpty( fileName ) ) {
        m_savedForecasterFileName = fileName;
      } else {
        m_savedForecasterFileName = null;
      }

    }

    // check the model status. If no model and we have
    // a file name, try and load here. Otherwise, loading
    // wont occur until the transformation starts or the
    // user opens the configuration gui in Spoon. This affects
    // the result of the getFields method and has an impact
    // on downstream steps that need to know what we produce
    WekaForecastingModel temp = m_model;
    if ( temp == null && !Const.isEmpty( m_modelFileName ) ) {
      try {
        loadModelFile();
      } catch ( Exception ex ) {
        throw new KettleXMLException( "Problem de-serializing model " + "file using supplied file name!" );
      }
    }
  }

  protected void loadModelFile() throws Exception {
    File modelFile = new File( m_modelFileName );
    if ( modelFile.exists() ) {
      logBasic( "loading model from file." );
      m_model = PMIForecastingData.loadSerializedModel( modelFile, getLog() );
    }
  }

  protected void deSerializeBase64Model( String base64modelXML ) throws Exception {
    byte[] model = XMLHandler.stringToBinary( base64modelXML );
    // System.err.println("Got model byte array ok.");
    // System.err.println("Length of array "+model.length);

    // now de-serialize
    ByteArrayInputStream bis = new ByteArrayInputStream( model );
    ObjectInputStream ois = new ObjectInputStream( bis );

    m_model = (WekaForecastingModel) ois.readObject();

    ois.close();
  }

  /**
   * Read this step's configuration from a repository
   *
   * @param rep     the repository to access
   * @param id_step the id for this step
   * @throws KettleException if an error occurs
   */
  @Override public void readRep( Repository rep, ObjectId id_step, List<DatabaseMeta> databases,
      Map<String, Counter> counters ) throws KettleException {
    m_numSteps = rep.getStepAttributeString( id_step, 0, "num_steps" );
    m_artificialTimeStartOffset = rep.getStepAttributeString( id_step, 0, "artificial_offset" );

    m_rebuildModel = rep.getStepAttributeBoolean( id_step, 0, "rebuild_model" );
    m_savedForecasterFileName = rep.getStepAttributeString( id_step, 0, "save_file_name" );

    // try and get a filename first as this overrides any model stored
    // in the repository
    boolean success = false;
    try {
      m_modelFileName = rep.getStepAttributeString( id_step, 0, "model_file_name" );
      success = true;
      if ( m_modelFileName == null || Const.isEmpty( m_modelFileName ) ) {
        success = false;
      }
    } catch ( KettleException ex ) {
      success = false;
    }

    if ( !success ) {
      // try and get the model itself...
      try {
        String base64XMLModel = rep.getStepAttributeString( id_step, 0, "weka_forecasting_model" );
        logDebug( BaseMessages.getString( PKG, "PMIForecastingMeta.Log.SizeOfModel" ) + " " + base64XMLModel.length() );
        // System.err.println("Size of base64 string read " +
        // base64XMLModel.length());
        // System.err.println(xmlModel);
        if ( base64XMLModel != null && base64XMLModel.length() > 0 ) {
          // try to de-serialize
          deSerializeBase64Model( base64XMLModel );
          success = true;
        } else {
          success = false;
        }
      } catch ( Exception ex ) {
        ex.printStackTrace();
        success = false;
      }
    }

    // check the model status. If no model and we have
    // a file name, try and load here. Otherwise, loading
    // wont occur until the transformation starts or the
    // user opens the configuration gui in Spoon. This affects
    // the result of the getFields method and has an impact
    // on downstream steps that need to know what we produce
    WekaForecastingModel temp = m_model;
    if ( temp == null && !Const.isEmpty( m_modelFileName ) ) {
      try {
        loadModelFile();
      } catch ( Exception ex ) {
        throw new KettleException( "Problem de-serializing model " + "file using supplied file name!" );
      }
    }
  }

  /**
   * Save this step's meta data to a repository
   *
   * @param rep               the repository to save to
   * @param id_transformation transformation id
   * @param id_step           step id
   * @throws KettleException if an error occurs
   */
  @Override public void saveRep( Repository rep, ObjectId id_transformation, ObjectId id_step ) throws KettleException {

    WekaForecastingModel temp = m_model;

    rep.saveStepAttribute( id_transformation, id_step, 0, "num_steps", m_numSteps );
    rep.saveStepAttribute( id_transformation, id_step, 0, "artificial_offset", m_artificialTimeStartOffset );
    rep.saveStepAttribute( id_transformation, id_step, 0, "rebuild_model", m_rebuildModel );
    if ( m_rebuildModel && !Const.isEmpty( m_savedForecasterFileName ) ) {
      rep.saveStepAttribute( id_transformation, id_step, 0, "save_file_name", m_savedForecasterFileName );
    }

    if ( temp != null && Const.isEmpty( m_modelFileName ) ) {
      try {
        // Convert model to base64 encoding
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        BufferedOutputStream bos = new BufferedOutputStream( bao );
        ObjectOutputStream oo = new ObjectOutputStream( bos );
        oo.writeObject( temp );
        oo.flush();
        byte[] model = bao.toByteArray();
        String base64XMLModel = KettleDatabaseRepository.byteArrayToString( model );

        logBasic( "Serializing model." );

        // String xmlModel = XStream.serialize(m_model);
        rep.saveStepAttribute( id_transformation, id_step, 0, "weka_forecasting_model", base64XMLModel );
        oo.close();
      } catch ( Exception ex ) {
        logError( BaseMessages.getString( PKG, "WekaScoringDialog.Log.Base64SerializationProblem" ) );
        // System.err.println("Problem serializing model to base64 (Meta.saveRep())");
      }
    } else {
      // either XStream is not present or user wants to source from
      // file
      if ( !Const.isEmpty( m_modelFileName ) ) {
        logBasic(
            BaseMessages.getString( PKG, "PMIForecastingMeta.Log.ModelSourcedFromFile" ) + " " + m_modelFileName );
      }
      /*
       * System.err.println("Model will be sourced from file " +
       * m_modelFileName);
       */
      rep.saveStepAttribute( id_transformation, id_step, 0, "model_file_name", m_modelFileName );
    }
  }

  /**
   * Generates row meta data to represent the fields output by this step
   *
   * @param row      the meta data for the output produced
   * @param origin   the name of the step to be used as the origin
   * @param info     The input rows metadata that enters the step through the
   *                 specified channels in the same order as in method getInfoSteps().
   *                 The step metadata can then choose what to do with it: ignore it or
   *                 not.
   * @param nextStep if this is a non-null value, it's the next step in the
   *                 transformation. The one who's asking, the step where the data is
   *                 targetted towards.
   * @param space    not sure what this is :-)
   * @throws KettleStepException if an error occurs
   */
  @Override public void getFields( RowMetaInterface row, String origin, RowMetaInterface[] info, StepMeta nextStep,
      VariableSpace space ) throws KettleStepException {

    if ( m_model == null && !Const.isEmpty( getSerializedModelFileName() ) ) {
      // see if we can load from a file.

      String modName = getSerializedModelFileName();
      modName = space.environmentSubstitute( modName );
      File modelFile = null;
      if ( modName.startsWith( "file:" ) ) {
        try {
          modelFile = new File( new java.net.URI( modName ) );
        } catch ( Exception ex ) {
          throw new KettleStepException( "Malformed URI for model file" );
        }
      } else {
        modelFile = new File( modName );
      }
      if ( !modelFile.exists() ) {
        throw new KettleStepException( "Serialized model file does " + "not exist on disk!" );
      }

      try {
        WekaForecastingModel model = PMIForecastingData.loadSerializedModel( modelFile, getLog() );
        setModel( model );
      } catch ( Exception ex ) {
        throw new KettleStepException( "Problem de-serializing model file" );
      }
    }

    RowMetaInterface orig = row.clone();
    row.clear();

    // convert all predicted target numeric input fields to number (Double)
    List<String> fieldsToForecast = AbstractForecaster.stringToList( m_model.getModel().getFieldsToForecast() );
    for ( int i = 0; i < orig.size(); i++ ) {
      ValueMetaInterface temp = orig.getValueMeta( i );
      String name = temp.getName();
      if ( temp.isNumeric() ) {
        int index = fieldsToForecast.indexOf( name );
        if ( index >= 0 ) {
          ValueMetaInterface newV = new ValueMeta( temp.getName(), ValueMetaInterface.TYPE_NUMBER );
          newV.setOrigin( temp.getOrigin() );
          row.addValueMeta( newV );
        } else {
          row.addValueMeta( temp );
        }
      } else {
        row.addValueMeta( temp );
      }
    }

    // We will be emitting all incoming Kettle fields. Historical
    // priming rows will be passed through. Forecasted rows will
    // contain values for all targets that this forecaster has been
    // trained to predict. All other fields will be set to missing,
    // with the exception of overlay data. Overlay data, if in use,
    // is expected to be provided for future time steps.

    // add fields for confidence intervals
    if ( m_model.isProducingConfidenceIntervals() ) {
      List<String> fields = m_model.getForecastedFields();

      for ( String field : fields ) {
        ValueMetaInterface newVM = new ValueMeta( field + "_lowerBound", ValueMetaInterface.TYPE_NUMBER );
        newVM.setOrigin( origin );
        row.addValueMeta( newVM );

        newVM = new ValueMeta( field + "_upperBound", ValueMetaInterface.TYPE_NUMBER );
        newVM.setOrigin( origin );
        row.addValueMeta( newVM );
      }
    }

    // add boolean historical/forecasted flag
    ValueMetaInterface newVM = new ValueMeta( "Forecasted", ValueMetaInterface.TYPE_BOOLEAN );
    newVM.setOrigin( origin );
    row.addValueMeta( newVM );
  }

  /**
   * Check the settings of this step and put findings in a remarks list.
   *
   * @param remarks   the list to put the remarks in. see
   *                  <code>org.pentaho.di.core.CheckResult</code>
   * @param transmeta the transform meta data
   * @param stepMeta  the step meta data
   * @param prev      the fields coming from a previous step
   * @param input     the input step names
   * @param output    the output step names
   * @param info      the fields that are used as information by the step
   */
  @Override public void check( List<CheckResultInterface> remarks, TransMeta transmeta, StepMeta stepMeta,
      RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info ) {

    CheckResult cr;

    if ( ( prev == null ) || ( prev.size() == 0 ) ) {
      cr =
          new CheckResult( CheckResult.TYPE_RESULT_WARNING, "Not receiving any fields from previous steps!", stepMeta );
      remarks.add( cr );
    } else {
      cr =
          new CheckResult( CheckResult.TYPE_RESULT_OK,
              "Step is connected to previous one, receiving " + prev.size() + " fields", stepMeta );
      remarks.add( cr );
    }

    // See if we have input streams leading to this step!
    if ( input.length > 0 ) {
      cr = new CheckResult( CheckResult.TYPE_RESULT_OK, "Step is receiving info from other steps.", stepMeta );
      remarks.add( cr );
    } else {
      cr = new CheckResult( CheckResult.TYPE_RESULT_ERROR, "No input received from other steps!", stepMeta );
      remarks.add( cr );
    }

    if ( m_model == null ) {
      if ( !Const.isEmpty( m_modelFileName ) ) {
        File f = new File( m_modelFileName );
        if ( !f.exists() ) {
          cr =
              new CheckResult( CheckResult.TYPE_RESULT_ERROR, "Step does not have access to a " + "usable model!",
                  stepMeta );
          remarks.add( cr );
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.pentaho.di.trans.step.BaseStepMeta#getDialogClassName()
   */
  @Override public String getDialogClassName() {
    return "org.pentaho.di.ui.trans.steps.pmi.weka.PMIForecastingDialog";
  }

  /**
   * Get the executing step, needed by Trans to launch a step.
   *
   * @param stepMeta          the step info
   * @param stepDataInterface the step data interface linked to this step. Here
   *                          the step can store temporary data, database connections, etc.
   * @param cnr               the copy number to get.
   * @param tr                the transformation info.
   * @param trans             the launching transformation
   * @return a <code>StepInterface</code> value
   */
  @Override public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta tr,
      Trans trans ) {

    return new PMIForecasting( stepMeta, stepDataInterface, cnr, tr, trans );
  }

  /**
   * Get a new instance of the appropriate data class. This data class
   * implements the StepDataInterface. It basically contains the persisting data
   * that needs to live on, even if a worker thread is terminated.
   *
   * @return a <code>StepDataInterface</code> value
   */
  @Override public StepDataInterface getStepData() {

    return new PMIForecastingData();
  }
}
