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

package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.Counter;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMeta;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
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
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.SerializedObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIScoring", image = "WEKAS.svg", name = "PMI Scoring", description = "Score or evaluate a PMI model", categoryDescription = "Data Mining" )
public class PMIScoringMeta extends BaseStepMeta implements StepMetaInterface {

  public static Class<?> PKG = PMIScoringMeta.class;

  /**
   * Default batch scoring size
   */
  public static final int DEFAULT_BATCH_SCORING_SIZE = 100;

  /**
   * User batch scoring size
   */
  protected String m_batchScoringSize = "";

  /**
   * Use a model file specified in an incoming field
   */
  protected boolean m_fileNameFromField;

  /**
   * Whether to cache loaded models in memory (when they are being specified by
   * a field in the incoming rows
   */
  protected boolean m_cacheLoadedModels;

  /**
   * The name of the field that is being used to specify model file name/path
   */
  protected String m_fieldNameToLoadModelFrom;

  /**
   * File name of the serialized PMI (wrapped) model to load/import
   */
  protected String m_modelFileName;

  /**
   * File name to save incrementally updated model to
   */
  protected String m_savedModelFileName;

  /**
   * True if predicted probabilities are to be output (has no effect if the
   * class (target is numeric)
   */
  protected boolean m_outputProbabilities;

  /**
   * True if user has selected to update a model on the incoming data stream and
   * the model supports incremental updates and there exists a column in the
   * incoming data stream that has been matched successfully to the class
   * attribute (if one exists).
   */
  protected boolean m_updateIncrementalModel;

  /**
   * Whether to store serialized model data into the step's metadata (rather than load from filesystem)
   */
  protected boolean m_storeModelInStepMetaData;

  /**
   * Whether to perform evaluation on the incoming stream (if targets are present) rather than score the data.
   * Applies to supervised models only
   */
  protected boolean m_evaluateRatherThanScore;

  /**
   * True if information retrieval metrics are to be output when evaluating
   */
  protected boolean m_outputIRMetrics;

  /**
   * True if area under the curve metrics are to be output (these require caching predictions, so will not be suitable
   * for very large test sets)
   */
  protected boolean m_outputAUCMetrics;

  /**
   * Holds the underlying model
   */
  protected transient PMIScoringModel m_model;

  /**
   * Holds a default model. Used only when model files are sourced from a field in the incoming data rows. In this case,
   * it is the fallback model if there is no model file specified in the current incoming row. It is also necessary so
   * that getFields() can determine the full output structure
   */
  protected transient PMIScoringModel m_defaultModel;

  /**
   * Set whether to perform evaluation on the incoming stream (if targets are present) rather than score the data.
   * Applies to supervised models only
   *
   * @param evaluateRatherThanScore true to perform evaluation (and output evaluation metrics) rather than score
   *                                the incoming data
   */
  public void setEvaluateRatherThanScore( boolean evaluateRatherThanScore ) {
    m_evaluateRatherThanScore = evaluateRatherThanScore;
  }

  /**
   * Get whether to perform evaluation on the incoming stream (if targets are present) rather than score the data.
   * Applies to supervised models only
   *
   * @return true to perform evaluation (and output evaluation metrics) rather than score
   * the incoming data
   */
  public boolean getEvaluateRatherThanScore() {
    return m_evaluateRatherThanScore;
  }

  /**
   * Set whether information retrieval metrics are to be output when performing evaluation
   *
   * @param outputIRMetrics true to compute and output IR metrics
   */
  public void setOutputIRMetrics( boolean outputIRMetrics ) {
    m_outputIRMetrics = outputIRMetrics;
  }

  /**
   * Get whether information retrieval metrics are to be output when performing evaluation
   *
   * @return true to compute and output IR metrics
   */
  public boolean getOutputIRMetrics() {
    return m_outputIRMetrics;
  }

  /**
   * Set whether to output area under the curve metrics or not. Note that AUC metrics require that predictions be cached,
   * so turning this on may not be suitable for very large test sets.
   *
   * @param outputAUCMetrics true to compute and output AUC metrics
   */
  public void setOutputAUCMetrics( boolean outputAUCMetrics ) {
    m_outputAUCMetrics = outputAUCMetrics;
  }

  /**
   * Get whether to output area under the curve metrics or not. Note that AUC metrics require that predictions be cached,
   * so turning this on may not be suitable for very large test sets.
   *
   * @return true to compute and output AUC metrics
   */
  public boolean getOutputAUCMetrics() {
    return m_outputAUCMetrics;
  }

  /**
   * Set whether to store the serialized model into the step's metadata
   *
   * @param b true to serialize the model into the step metadata
   */
  public void setStoreModelInStepMetaData( boolean b ) {
    m_storeModelInStepMetaData = b;
  }

  /**
   * Get whether to store the serialized model into the step's metadata
   *
   * @return true to serialize the model into the step metadata
   */
  public boolean getStoreModelInStepMetaData() {
    return m_storeModelInStepMetaData;
  }

  /**
   * Set the batch size to use if the model is a batch scoring model
   *
   * @param size the size of the batch to use
   */
  public void setBatchScoringSize( String size ) {
    m_batchScoringSize = size;
  }

  /**
   * Get the batch size to use if the model is a batch scoring model
   *
   * @return the size of the batch to use
   */
  public String getBatchScoringSize() {
    return m_batchScoringSize;
  }

  /**
   * Set whether filename is coming from an incoming field
   *
   * @param f true if the model to use is specified via path in an incoming
   *          field value
   */
  public void setFileNameFromField( boolean f ) {
    m_fileNameFromField = f;
  }

  /**
   * Get whether filename is coming from an incoming field
   *
   * @return true if the model to use is specified via path in an incoming field
   * value
   */
  public boolean getFileNameFromField() {
    return m_fileNameFromField;
  }

  /**
   * Set whether to cache loaded models in memory
   *
   * @param l true if models are to be cached in memory
   */
  public void setCacheLoadedModels( boolean l ) {
    m_cacheLoadedModels = l;
  }

  /**
   * Get whether to cache loaded models in memory
   *
   * @return true if models are to be cached in memory
   */
  public boolean getCacheLoadedModels() {
    return m_cacheLoadedModels;
  }

  /**
   * Set the name of the incoming field that holds paths to model files
   *
   * @param fn the name of the incoming field that holds model paths
   */
  public void setFieldNameToLoadModelFrom( String fn ) {
    m_fieldNameToLoadModelFrom = fn;
  }

  /**
   * Get the name of the incoming field that holds paths to model files
   *
   * @return the name of the incoming field that holds model paths
   */
  public String getFieldNameToLoadModelFrom() {
    return m_fieldNameToLoadModelFrom;
  }

  /**
   * Set the file name of the serialized PMI model to load/import from
   *
   * @param mfile the file name
   */
  public void setSerializedModelFileName( String mfile ) {
    m_modelFileName = mfile;
  }

  /**
   * Get the filename of the serialized PMI model to load/import from
   *
   * @return the file name
   */
  public String getSerializedModelFileName() {
    return m_modelFileName;
  }

  /**
   * Set the file name that the incrementally updated model will be saved to
   * when the current stream of data terminates
   *
   * @param savedM the file name to save to
   */
  public void setSavedModelFileName( String savedM ) {
    m_savedModelFileName = savedM;
  }

  /**
   * Get the file name that the incrementally updated model will be saved to
   * when the current stream of data terminates
   *
   * @return the file name to save to
   */
  public String getSavedModelFileName() {
    return m_savedModelFileName;
  }

  /**
   * Set the PMI model
   *
   * @param model a <code>PMIScoringModel</code> that encapsulates the actual
   *              model (Classifier or Clusterer)
   */
  public void setModel( PMIScoringModel model ) {
    m_model = model;
  }

  /**
   * Get the PMI model
   *
   * @return a <code>PMIScoringModel</code> that encapsulates the actual
   * model (Classifier or Clusterer)
   */
  public PMIScoringModel getModel() {
    return m_model;
  }

  /**
   * Gets the default model (only used when model file names are being sourced
   * from a field in the incoming rows).
   *
   * @return the default model to use when there is no filename provided in the
   * incoming data row.
   */
  public PMIScoringModel getDefaultModel() {
    return m_defaultModel;
  }

  /**
   * Sets the default model (only used when model file names are being sourced
   * from a field in the incoming rows).
   *
   * @param defaultM the default model to use.
   */
  public void setDefaultModel( PMIScoringModel defaultM ) {
    m_defaultModel = defaultM;
  }

  /**
   * Set whether to predict probabilities
   *
   * @param b true if a probability distribution is to be output
   */
  public void setOutputProbabilities( boolean b ) {
    m_outputProbabilities = b;
  }

  /**
   * Get whether to predict probabilities
   *
   * @return a true if a probability distribution is to be output
   */
  public boolean getOutputProbabilities() {
    return m_outputProbabilities;
  }

  /**
   * Get whether the model is to be incrementally updated with each incoming row
   * (after making a prediction for it).
   *
   * @return a true if the model is to be updated incrementally with each
   * incoming row
   */
  public boolean getUpdateIncrementalModel() {
    return m_updateIncrementalModel;
  }

  /**
   * Set whether to update the model incrementally
   *
   * @param u true if the model should be updated with each incoming row (after
   *          predicting it)
   */
  public void setUpdateIncrementalModel( boolean u ) {
    m_updateIncrementalModel = u;
  }

  /**
   * Check for equality
   *
   * @param obj an <code>Object</code> to compare with
   * @return true if equal to the supplied object
   */
  @Override public boolean equals( Object obj ) {
    if ( obj != null && ( obj.getClass().equals( this.getClass() ) ) ) {
      PMIScoringMeta m = (PMIScoringMeta) obj;
      return ( getXML( false ) == m.getXML( false ) );
    }

    return false;
  }

  /**
   * Hash code method
   *
   * @return the hash code for this object
   */
  @Override public int hashCode() {
    return getXML( false ).hashCode();
  }

  /**
   * Clone this step's meta data
   *
   * @return the cloned meta data
   */
  @Override public Object clone() {
    PMIScoringMeta retval = (PMIScoringMeta) super.clone();
    /* // deep copy the model (if any)
    if ( m_model != null ) {
      try {
        SerializedObject so = new SerializedObject( m_model );
        PMIScoringModel copy = (PMIScoringModel) so.getObject();
        copy.setLog( getLog() );
        retval.setModel( copy );
      } catch ( Exception ex ) {
        logError( BaseMessages.getString( PKG, "PMIScoringMeta.Log.DeepCopyingError" ) ); //$NON-NLS-1$
      }
    }

    // deep copy the default model (if any)
    if ( m_defaultModel != null ) {
      try {
        SerializedObject so = new SerializedObject( m_defaultModel );
        PMIScoringModel copy = (PMIScoringModel) so.getObject();
        copy.setLog( getLog() );
        retval.setDefaultModel( copy );
      } catch ( Exception ex ) {
        logError( BaseMessages.getString( PKG, "PMIScoringMeta.Log.DeepCopyingError" ) ); //$NON-NLS-1$
      }
    } */

    return retval;
  }

  /**
   * Return the XML describing this (configured) step
   *
   * @return a <code>String</code> containing the XML
   */
  public String getXML() {
    return getXML( true );
  }

  protected String getXML( boolean logging ) {
    StringBuilder retval = new StringBuilder();

    retval.append( XMLHandler.addTagValue( "output_probabilities", m_outputProbabilities ) );
    retval.append( XMLHandler.addTagValue( "update_model", m_updateIncrementalModel ) );
    retval.append( XMLHandler.addTagValue( "store_model_in_meta", m_storeModelInStepMetaData ) );

    if ( m_updateIncrementalModel ) {
      // any file name to save the changed model to?
      if ( !Const.isEmpty( m_savedModelFileName ) ) {
        retval.append( XMLHandler.addTagValue( "model_export_file_name", m_savedModelFileName ) );
      }
    }

    retval.append( XMLHandler.addTagValue( "file_name_from_field", m_fileNameFromField ) );
    if ( m_fileNameFromField ) {
      // any non-null field name?
      if ( !Const.isEmpty( m_fieldNameToLoadModelFrom ) ) {
        retval.append( XMLHandler.addTagValue( "field_name_to_load_from", m_fieldNameToLoadModelFrom ) );
        if ( logging ) {
          getLog().logDebug( BaseMessages.getString( PKG, "PMIScoringMeta.Log.ModelSourcedFromField" ) + " "
              + m_fieldNameToLoadModelFrom );
        }
      }
    }

    if ( !Const.isEmpty( m_batchScoringSize ) ) {
      retval.append( XMLHandler.addTagValue( "batch_scoring_size", m_batchScoringSize ) );
    }

    retval.append( XMLHandler.addTagValue( "cache_loaded_models", m_cacheLoadedModels ) );

    retval.append( XMLHandler.addTagValue( "perform_evaluation", m_evaluateRatherThanScore ) );
    retval.append( XMLHandler.addTagValue( "output_ir_metrics", m_outputIRMetrics ) );
    retval.append( XMLHandler.addTagValue( "output_auc_metrics", m_outputAUCMetrics ) );

    PMIScoringModel temp = m_fileNameFromField ? m_defaultModel : m_model;
    if ( temp != null && Const.isEmpty( getSerializedModelFileName() ) ) {
      byte[] model = serializeModelToBase64( temp );
      if ( model != null ) {
        try {
          String base64model = XMLHandler.addTagValue( "pmi_scoring_model", model );
          String modType = ( m_fileNameFromField ) ? "default" : "";
          if ( logging ) {
            getLog().logDebug( "Serializing " + modType + " model." );
            getLog().logDebug(
                BaseMessages.getString( PKG, "PMIScoringMeta.Log.SizeOfModel" ) + " " + base64model.length() );
          }

          retval.append( base64model );
        } catch ( IOException e ) {
          if ( logging ) {
            getLog().logError( BaseMessages.getString( PKG, "PMIScoringMeta.Log.Base64SerializationProblem" ) );
          }
        }
      }
    } else {
      if ( !Const.isEmpty( m_modelFileName ) ) {

        if ( logging ) {
          logDetailed(
              BaseMessages.getString( PKG, "PMIScoringMeta.Log.ModelSourcedFromFile" ) + " " + m_modelFileName );
        }
      }

      // save the model file name
      retval.append( XMLHandler.addTagValue( "model_file_name", m_modelFileName ) );
    }

    return retval.toString();
  }

  /**
   * Loads the meta data for this (configured) step from XML.
   *
   * @param stepnode the step to load
   */
  public void loadXML( Node stepnode, List<DatabaseMeta> databases, Map<String, Counter> counters ) {
    String temp = XMLHandler.getTagValue( stepnode, "file_name_from_field" );
    if ( temp.equalsIgnoreCase( "N" ) ) {
      m_fileNameFromField = false;
    } else {
      m_fileNameFromField = true;
    }

    if ( m_fileNameFromField ) {
      m_fieldNameToLoadModelFrom = XMLHandler.getTagValue( stepnode, "field_name_to_load_from" );
    }

    m_batchScoringSize = XMLHandler.getTagValue( stepnode, "batch_scoring_size" );

    String store = XMLHandler.getTagValue( stepnode, "store_model_in_meta" );
    if ( store != null ) {
      m_storeModelInStepMetaData = store.equalsIgnoreCase( "Y" );
    }

    String eval = XMLHandler.getTagValue( stepnode, "perform_evaluation" );
    if ( !Const.isEmpty( eval ) ) {
      setEvaluateRatherThanScore( eval.equalsIgnoreCase( "Y" ) );
    }
    String outputIR = XMLHandler.getTagValue( stepnode, "output_ir_metrics" );
    if ( !Const.isEmpty( outputIR ) ) {
      setOutputIRMetrics( outputIR.equalsIgnoreCase( "Y" ) );
    }
    String outputAUC = XMLHandler.getTagValue( stepnode, "output_auc_metrics" );
    if ( !Const.isEmpty( outputAUC ) ) {
      setOutputAUCMetrics( outputAUC.equalsIgnoreCase( "Y" ) );
    }

    temp = XMLHandler.getTagValue( stepnode, "cache_loaded_models" );
    if ( temp.equalsIgnoreCase( "N" ) ) {
      m_cacheLoadedModels = false;
    } else {
      m_cacheLoadedModels = true;
    }

    // try and get the XML-based model
    boolean success = false;
    try {
      String base64modelXML = XMLHandler.getTagValue( stepnode, "pmi_scoring_model" );

      deSerializeBase64Model( base64modelXML );
      success = true;

      String modType = ( m_fileNameFromField ) ? "default" : "";
      logDebug( "Deserializing " + modType + " model." );

      logDebug( BaseMessages.getString( PKG, "PMIScoringMeta.Log.DeserializationSuccess" ) );
    } catch ( Exception ex ) {
      success = false;
    }

    if ( !success ) {
      // fall back and try and grab a model file name
      m_modelFileName = XMLHandler.getTagValue( stepnode, "model_file_name" );
    }

    temp = XMLHandler.getTagValue( stepnode, "output_probabilities" );
    if ( temp.equalsIgnoreCase( "N" ) ) {
      m_outputProbabilities = false;
    } else {
      m_outputProbabilities = true;
    }

    temp = XMLHandler.getTagValue( stepnode, "update_model" );
    if ( temp.equalsIgnoreCase( "N" ) ) {
      m_updateIncrementalModel = false;
    } else {
      m_updateIncrementalModel = true;
    }

    if ( m_updateIncrementalModel ) {
      m_savedModelFileName = XMLHandler.getTagValue( stepnode, "model_export_file_name" );
    }
  }

  /**
   * Read this step's configuration from a repository
   *
   * @param rep     the repository to access
   * @param id_step the id for this step
   * @throws KettleException if an error occurs
   */
  public void readRep( Repository rep, ObjectId id_step, List<DatabaseMeta> databases, Map<String, Counter> counters )
      throws KettleException {
    m_fileNameFromField = rep.getStepAttributeBoolean( id_step, 0, "file_name_from_field" );

    m_batchScoringSize = rep.getStepAttributeString( id_step, 0, "batch_scoring_size" );

    if ( m_fileNameFromField ) {
      m_fieldNameToLoadModelFrom = rep.getStepAttributeString( id_step, 0, "field_name_to_load_from" );
    }

    m_cacheLoadedModels = rep.getStepAttributeBoolean( id_step, 0, "cache_loaded_models" );

    m_storeModelInStepMetaData = rep.getStepAttributeBoolean( id_step, 0, "store_model_in_meta" );

    setEvaluateRatherThanScore( rep.getStepAttributeBoolean( id_step, 0, "perform_evaluation" ) );
    setOutputIRMetrics( rep.getStepAttributeBoolean( id_step, 0, "output_ir_metrics" ) );
    setOutputAUCMetrics( rep.getStepAttributeBoolean( id_step, 0, "output_auc_metrics" ) );

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
        String base64XMLModel = rep.getStepAttributeString( id_step, 0, "pmi_scoring_model" );
        logDebug( BaseMessages.getString( PKG, "PMIScoringMeta.Log.SizeOfModel" ) + " " + base64XMLModel.length() );

        if ( !Const.isEmpty( base64XMLModel ) ) {
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

    m_outputProbabilities = rep.getStepAttributeBoolean( id_step, 0, "output_probabilities" ); //$NON-NLS-1$

    m_updateIncrementalModel = rep.getStepAttributeBoolean( id_step, 0, "update_model" ); //$NON-NLS-1$

    if ( m_updateIncrementalModel ) {
      m_savedModelFileName = rep.getStepAttributeString( id_step, 0, "model_export_file_name" ); //$NON-NLS-1$
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
  public void saveRep( Repository rep, ObjectId id_transformation, ObjectId id_step ) throws KettleException {

    rep.saveStepAttribute( id_transformation, id_step, 0, "output_probabilities", m_outputProbabilities );

    rep.saveStepAttribute( id_transformation, id_step, 0, "update_model", m_updateIncrementalModel );

    if ( m_updateIncrementalModel ) {
      // any file name to save the changed model to?
      if ( !Const.isEmpty( m_savedModelFileName ) ) {
        rep.saveStepAttribute( id_transformation, id_step, 0, "model_export_file_name",
            m_savedModelFileName ); //$NON-NLS-1$
      }
    }

    rep.saveStepAttribute( id_transformation, id_step, 0, "file_name_from_field", m_fileNameFromField );
    if ( m_fileNameFromField ) {
      rep.saveStepAttribute( id_transformation, id_step, 0, "field_name_to_load_from", m_fieldNameToLoadModelFrom );
    }

    rep.saveStepAttribute( id_transformation, id_step, 0, "cache_loaded_models", m_cacheLoadedModels );

    rep.saveStepAttribute( id_transformation, id_step, 0, "store_model_in_meta", m_storeModelInStepMetaData );

    rep.saveStepAttribute( id_transformation, id_step, 0, "perform_evaluation", m_evaluateRatherThanScore );
    rep.saveStepAttribute( id_transformation, id_step, 0, "output_ir_metrics", m_outputIRMetrics );
    rep.saveStepAttribute( id_transformation, id_step, 0, "output_auc_metrics", m_outputAUCMetrics );

    if ( !Const.isEmpty( m_batchScoringSize ) ) {
      rep.saveStepAttribute( id_transformation, id_step, 0, "batch_scoring_size", m_batchScoringSize );
    }

    PMIScoringModel temp = ( m_fileNameFromField ) ? m_defaultModel : m_model;

    if ( temp != null && Const.isEmpty( m_modelFileName ) ) {
      try {
        // Convert model to base64 encoding
        byte[] model = serializeModelToBase64( temp );
        String base64XMLModel = KettleDatabaseRepository.byteArrayToString( model );

        String modType = ( m_fileNameFromField ) ? "default" : "";
        logDebug( "Serializing " + modType + " model." );

        rep.saveStepAttribute( id_transformation, id_step, 0, "pmi_scoring_model", base64XMLModel );
      } catch ( Exception ex ) {
        logError( BaseMessages.getString( PKG, "PMIScoringDialog.Log.Base64SerializationProblem" ), ex );
      }
    } else {
      // either XStream is not present or user wants to source from
      // file
      if ( !Const.isEmpty( m_modelFileName ) ) {
        logDetailed( BaseMessages.getString( PKG, "PMIScoringMeta.Log.ModelSourcedFromFile" ) + " " + m_modelFileName );
      }

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

      try {
        if ( !PMIScoringData.modelFileExists( modName, space ) ) {
          throw new KettleStepException( BaseMessages.getString( PKG, "PMIScoring.Error.NonExistentModelFile" ) );
        }

        PMIScoringModel model = PMIScoringData.loadSerializedModel( m_modelFileName, getLog(), space );
        setModel( model );
      } catch ( Exception ex ) {
        throw new KettleStepException( BaseMessages.getString( PKG, "PMIScoring.Error.ProblemDeserializingModel" ),
            ex );
      }
    }

    if ( m_model != null ) {
      // output fields when performing evaluation rather than scoring
      if ( getEvaluateRatherThanScore() && m_model.isSupervisedLearningModel() ) {
        try {
          getFieldsEvalMode( row, space, (PMIScoringClassifier) m_model );
        } catch ( KettlePluginException e ) {
          throw new KettleStepException( e );
        }
        return;
      }

      try {
        Instances header = m_model.getHeader();
        String classAttName;
        boolean supervised = m_model.isSupervisedLearningModel();

        if ( supervised ) {
          classAttName = header.classAttribute().name();

          if ( header.classAttribute().isNumeric() || !m_outputProbabilities ) {
            int
                valueType =
                ( header.classAttribute().isNumeric() ) ? ValueMetaInterface.TYPE_NUMBER :
                    ValueMetaInterface.TYPE_STRING;

            ValueMetaInterface newVM = ValueMetaFactory.createValueMeta( classAttName + "_predicted", valueType );
            newVM.setOrigin( origin );
            row.addValueMeta( newVM );
          } else {
            for ( int i = 0; i < header.classAttribute().numValues(); i++ ) {
              String classVal = header.classAttribute().value( i );
              ValueMetaInterface
                  newVM =
                  ValueMetaFactory.createValueMeta( classAttName + ":" + classVal + "_predicted_prob",
                      ValueMetaInterface.TYPE_NUMBER );
              newVM.setOrigin( origin );
              row.addValueMeta( newVM );
            }
          }
        } else {
          if ( m_outputProbabilities ) {
            try {
              int numClusters = ( (PMIScoringClusterer) m_model ).numberOfClusters();
              for ( int i = 0; i < numClusters; i++ ) {
                ValueMetaInterface
                    newVM =
                    ValueMetaFactory
                        .createValueMeta( "cluster_" + i + "_predicted_prob", ValueMetaInterface.TYPE_NUMBER );
                newVM.setOrigin( origin );
                row.addValueMeta( newVM );
              }
            } catch ( Exception ex ) {
              throw new KettleStepException(
                  BaseMessages.getString( PKG, "PMIScoringMeta.Error.UnableToGetNumberOfClusters" ), ex );
            }
          } else {
            ValueMetaInterface
                newVM =
                ValueMetaFactory.createValueMeta( "cluster#_predicted", ValueMetaInterface.TYPE_NUMBER );
            newVM.setOrigin( origin );
            row.addValueMeta( newVM );
          }
        }
      } catch ( KettlePluginException e ) {
        throw new KettleStepException( e );
      }
    }
  }

  /**
   * Generates row metadata to represent evaluation-based output fields
   *
   * @param outRowMeta the output row metadata
   * @param vars       environment variables
   * @param model      the model/default model (used to obtain training data structure)
   * @throws KettlePluginException if a problem occurs
   */
  protected void getFieldsEvalMode( RowMetaInterface outRowMeta, VariableSpace vars, PMIScoringClassifier model )
      throws KettlePluginException {
    outRowMeta.clear();
    Instances header = model.getHeader();
    Attribute classAtt = header.classAttribute();
    boolean classIsNominal = classAtt.isNominal();

    ValueMetaInterface vm = null;

    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.LearningSchemeName" ),
            ValueMetaInterface.TYPE_STRING );
    outRowMeta.addValueMeta( vm );

    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.LearningSchemeOptions" ),
            ValueMetaInterface.TYPE_STRING );
    outRowMeta.addValueMeta( vm );

    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.EvaluationMode" ),
            ValueMetaInterface.TYPE_STRING );
    outRowMeta.addValueMeta( vm );

    vm =
        ValueMetaFactory
            .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.UnclassifiedInstancesFieldName" ),
                ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );

    if ( classIsNominal ) {
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.CorrectInstancesFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
      vm =
          ValueMetaFactory
              .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.IncorrectInstancesFieldName" ),
                  ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.PercentCorrectFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.PercentIncorrectFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
    }
    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.MAEFieldName" ),
            ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );
    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.RMSEFieldName" ),
            ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );
    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.RAEFieldName" ),
            ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );
    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.RRSEFieldName" ),
            ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );
    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.TotalNumInstancesFieldName" ),
            ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );

    if ( classIsNominal ) {
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.KappaStatisticFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
    }

    if ( classIsNominal ) {
      if ( getOutputIRMetrics() ) {
        for ( int i = 0; i < classAtt.numValues(); i++ ) {
          String label = classAtt.value( i );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.TPRateFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.FPRateFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.PrecisionFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.RecallFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.FMeasureFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.MCCFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );
        }
      }

      if ( getOutputAUCMetrics() ) {
        for ( int i = 0; i < classAtt.numValues(); i++ ) {
          String label = classAtt.value( i );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.AUCFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );

          vm =
              ValueMetaFactory
                  .createValueMeta( label + "_" + BaseMessages.getString( PKG, "BasePMIStepData.PRCFieldName" ),
                      ValueMetaInterface.TYPE_NUMBER );
          outRowMeta.addValueMeta( vm );
        }
      }
    }
  }

  protected byte[] serializeModelToBase64( PMIScoringModel model ) {
    try {
      ByteArrayOutputStream bao = new ByteArrayOutputStream();
      BufferedOutputStream bos = new BufferedOutputStream( bao );
      ObjectOutputStream oo = new ObjectOutputStream( bos );
      oo.writeObject( model );
      oo.flush();
      return bao.toByteArray();
    } catch ( Exception ex ) {
      getLog().logDebug( "Unable to serialize model to base 64!" );
    }
    return null;
  }

  protected void deSerializeBase64Model( String base64modelXML ) throws Exception {
    byte[] model = XMLHandler.stringToBinary( base64modelXML );

    // now de-serialize
    ByteArrayInputStream bis = new ByteArrayInputStream( model );
    ObjectInputStream ois = SerializationHelper.getObjectInputStream( bis );

    if ( m_fileNameFromField ) {
      m_defaultModel = (PMIScoringModel) ois.readObject();
    } else {
      m_model = (PMIScoringModel) ois.readObject();
    }
    ois.close();
  }

  @Override public void setDefault() {
    m_modelFileName = null;
    m_outputProbabilities = false;
  }

  @Override
  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int i, TransMeta transMeta,
      Trans trans ) {
    return new PMIScoring( stepMeta, stepDataInterface, i, transMeta, trans );
  }

  @Override public StepDataInterface getStepData() {
    return new PMIScoringData();
  }
}
