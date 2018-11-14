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
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepIOMeta;
import org.pentaho.di.trans.step.StepIOMetaInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.di.trans.step.errorhandling.StreamIcon;
import org.pentaho.di.trans.step.errorhandling.StreamInterface;
import org.pentaho.di.trans.step.errorhandling.Stream;
import org.pentaho.di.ui.trans.steps.pmi.BaseSupervisedPMIStepDialog;
import org.pentaho.dm.commons.ArffMeta;
import org.pentaho.metastore.api.IMetaStore;
import org.pentaho.pmi.Evaluator;
import org.pentaho.pmi.engines.WekaEngine;
import org.w3c.dom.Node;
import weka.core.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for PMI step metadata. Handles all the configuration required for an arbitrary PMI classifier/regressor.
 * Subclasses need only provide a no-args constructor that calls s{@code setSchemeName()} in order to set the internal
 * name for a scheme that PMI supports ({@see Scheme}) for a list of schemes that are supported. Note that a given engine
 * might or might not support a specific scheme.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public abstract class BaseSupervisedPMIStepMeta extends BaseStepMeta implements StepMetaInterface {

  public static Class<?> PKG = BaseSupervisedPMIStep.class;

  protected static final String ENGINE_NAME_TAG = "engine_name";
  protected static final String SCHEME_NAME_TAG = "scheme_name";
  protected static final String SCHEME_OPTS_TAG = "scheme_options";
  protected static final String ROW_HANDLING_MODE = "row_handling_mode";
  protected static final String BATCH_SIZE_TAG = "batch_size";
  protected static final String USE_RESERVOIR_SAMPLING_TAG = "use_reservoir_sampling";
  protected static final String RESERVOIR_SAMPLING_SIZE_TAG = "reservoir_size";
  protected static final String RESERVOIR_SAMPLING_RANDOM_SEED_TAG = "reservoir_seed";
  protected static final String STRATIFICATION_FIELD_NAME_TAG = "stratification_field_name";
  protected static final String INCOMING_FIELD_META_TAG = "incoming_field_meta";
  protected static final String CLASS_FIELD_TAG = "class_attribute";
  protected static final String TRAINING_STEP_INPUT_NAME_TAG = "training_step_input_name";
  protected static final String TEST_STEP_INPUT_NAME_TAG = "test_step_input_name";
  protected static final String SAMPLING_CONFIGS_TAG = "sampling_configs";
  protected static final String PREPROCESSING_CONFIGS_TAG = "preprocessing_configs";
  protected static final String SINGLE_SAMPLING_CONFIG_TAG = "sampling_config";
  protected static final String SINGLE_PREPROCESSING_CONFIG_TAG = "preprocessing_config";
  protected static final String EVAL_MODE_TAG = "evaluation_mode";
  protected static final String SPLIT_PERCENTAGE_TAG = "split_percentage";
  protected static final String X_VAL_FOLDS_TAG = "x_val_folds";
  protected static final String RANDOM_SEED_TAG = "random_seed";
  protected static final String MODEL_OUTPUT_DIRECTORY_TAG = "model_output_path";
  protected static final String MODEL_FILE_NAME_TAG = "model_file_name";
  protected static final String OUTPUT_AUC_METRICS_TAG = "output_auc_metrics";
  protected static final String OUTPUT_IR_METRICS_TAG = "output_ir_metrics";
  protected static final String INCREMENTAL_TRAININ_INITIAL_ROW_CACHE_SIZE_TAG = "incremental_initial_cache";

  /**
   * Default row handling strategy
   */
  protected static final String
      DEFAULT_ROWS_TO_PROCESS =
      BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.AllEntry.Label" );

  /**
   * Engine to use
   */
  protected String m_engineName = WekaEngine.ENGINE_NAME;

  /**
   * The name of the predictive scheme to use
   */
  protected String m_schemeName = "";

  /**
   * The command-line options of the underlying predictive scheme
   */
  protected String m_underlyingSchemeOptions = "";

  protected String m_rowsToProcess = DEFAULT_ROWS_TO_PROCESS;

  protected String m_stratificationFieldName = "";

  /**
   * Batch size to use when row handling is set to batch mode
   */
  protected String m_batchSize = "";

  /**
   * True if reservoir sampling is to be performed
   */
  protected boolean m_useReservoirSampling;

  /**
   * Random seed for reservoir sampling
   */
  protected String m_randomSeedReservoirSampling = "1";

  /**
   * Size of the reservoir if doing reservoir sampling
   */
  protected String m_reservoirSize = "";

  /**
   * Info on incoming fields and how they should be treated for the modeling process
   */
  protected List<ArffMeta> m_fieldMeta = new ArrayList<>();

  /**
   * The name of the step providing training data (this will be ignored if there is only one incoming info stream)
   */
  protected String m_trainingStepInputName = "";

  /**
   * The name of the step providing separate test set data (this will be ignored if there is only one incoming info stream)
   */
  protected String m_testingStepInputName = "";

  /**
   * Configs for sampling
   */
  protected Map<String, String> m_samplingConfigs = new LinkedHashMap<>();

  /**
   * Configs for preprocessing
   */
  protected Map<String, String> m_preprocessingConfigs = new LinkedHashMap<>();

  /**
   * The evaluation to perform
   */
  protected Evaluator.EvalMode m_evalMode = Evaluator.EvalMode.NONE;

  /**
   * Size of the training split (%) if doing a percentage split evaluation
   */
  protected String m_percentageSplit = "66";

  /**
   * Number of folds to use if doing a cross-validation evaluation
   */
  protected String m_xValFolds = "10";

  /**
   * Random number seed to use for splitting the data in percentage split or cross-validation
   */
  protected String m_randomSeed = "1";

  /**
   * True to output the area under ROC and PRC curves when performing evaluation. This requires storing predictions, so
   * consumes more memory. It also requires that class labels are pre-specified by the user (so that output row metadata
   * can be generated prior to execution).
   */
  protected boolean m_outputAUCMetrics;

  /**
   * True to output IR metrics when performing evaluation. This requires storing predictions, so
   * consumes more memory. It also requires that class labels are pre-specified by the user (so that output row metadata
   * can be generated prior to execution).
   */
  protected boolean m_outputIRMetrics;

  /**
   * The name of the class attribute
   */
  protected String m_classField = "";

  /**
   * Directory to save the final model to
   */
  protected String m_modelOutputPath = "";

  /**
   * File name for the model
   */
  protected String m_modelFileName = "";

  /**
   * The number of rows to cache from the start of the incoming training stream when learning an incremental model. This will only be used if 1) the
   * underlying scheme supports incremental training and 2) the header for the data stream cannot be fully determined from the incoming row metadata
   * (i.e. there are string fields that will be converted to nominal attributes and the user has not specified the legal values apriori).
   */
  protected String m_incrementalInitialCache = "100";

  /**
   * If a scheme is resumable (implements IterativeClassifier), then this field can be used to specify a model to load
   * for continued training (eval modes NONE or SEPARATE_TEST only).
   */
  protected String m_resumableModelLoadPath = "";

  // --------- row handling --------------

  /**
   * Set the row handling strategy
   *
   * @param rowsToProcess the row handling strategy
   */
  public void setRowsToProcess( String rowsToProcess ) {
    m_rowsToProcess = rowsToProcess;
  }

  /**
   * Get the row handling strategy
   *
   * @return the row handling strategy
   */
  public String getRowsToProcess() {
    return m_rowsToProcess;
  }

  /**
   * If row handling mode is "stratified", then set the name of the field to
   * stratify on
   *
   * @param fieldName the name of the field to stratify on, if row handling mode is "stratified"
   */
  public void setStratificationFieldName( String fieldName ) {
    m_stratificationFieldName = fieldName;
  }

  /**
   * If row handling mode is "stratified", then get the name of the field to
   * stratify on
   *
   * @return the name of the field to stratify on, if row handling mode is "stratified"
   */
  public String getStratificationFieldName() {
    return m_stratificationFieldName;
  }

  /**
   * Set the size of the batch to use when row handling mode is set to "batch"
   *
   * @param batchSize the size of the batch to use
   */
  public void setBatchSize( String batchSize ) {
    m_batchSize = batchSize;
  }

  /**
   * Get the size of the batch to use when row handling mode is set to "batch"
   *
   * @return the size of the batch to use
   */
  public String getBatchSize() {
    return m_batchSize;
  }

  /**
   * Set whether to apply reservoir sampling. If true, then reservoir sampling is done
   * before any batch sampling/class balancing
   *
   * @param useReservoirSampling true if reservoir sampling is to be used
   */
  public void setUseReservoirSampling( boolean useReservoirSampling ) {
    m_useReservoirSampling = useReservoirSampling;
  }

  /**
   * Get whether to apply reservoir sampling. If true, then reservoir sampling is done
   * before any batch sampling/class balancing
   *
   * @return true if reservoir sampling is to be used
   */
  public boolean getUseReservoirSampling() {
    return m_useReservoirSampling;
  }

  /**
   * Set the seed used for reservoir sampling
   *
   * @param randomSeed the seed used for reservoir sampling
   */
  public void setRandomSeedReservoirSampling( String randomSeed ) {
    m_randomSeedReservoirSampling = randomSeed;
  }

  /**
   * Get the seed used for reservoir sampling
   *
   * @return the seed used for reservoir sampling
   */
  public String getRandomSeedReservoirSampling() {
    return m_randomSeedReservoirSampling;
  }

  /**
   * Set the size of the reservoir to use, if doing reservoir sampling
   *
   * @param reservoirSize the size of the reservoir to use
   */
  public void setReservoirSize( String reservoirSize ) {
    m_reservoirSize = reservoirSize;
  }

  /**
   * Get the size of the reservoir to use, if doing reservoir sampling
   *
   * @return the size of the reservoir to use
   */
  public String getReservoirSize() {
    return m_reservoirSize;
  }

  /**
   * Set the name of the step that is providing training data. This value can/will be safely ignored
   * in the case where there is only one step connected (assumed to be training data).
   *
   * @param name the name of the step providing training data
   */
  public void setTrainingStepInputName( String name ) {
    m_trainingStepInputName = name;
  }

  /**
   * Get the name of the step that is providing training data. This value can/will be safely ignored
   * in the case where there is only one step connected (assumed to be training data).
   *
   * @return the name of the step providing training data
   */
  public String getTrainingStepInputName() {
    return m_trainingStepInputName;
  }

  /**
   * Set the name of the step that is providing testing data. This value can/will be safely ignored
   * in the case where there is only one step connected (assumed to be training data).
   *
   * @param name the name of the step providing testing data
   */
  public void setTestingStepInputName( String name ) {
    m_testingStepInputName = name;
  }

  /**
   * Get the name of the step that is providing testing data. This value can/will be safely ignored
   * in the case where there is only one step connected (assumed to be training data).
   *
   * @return the name of the step providing testing data
   */
  public String getTestingStepInputName() {
    return m_testingStepInputName;
  }

  /**
   * Set the sampling configs to use for the scheme. These relate to batch
   * sampling using Weka's libraries.
   *
   * @param samplingConfigs the sampling configs to use for the scheme
   */
  public void setSamplingConfigs( Map<String, String> samplingConfigs ) {
    m_samplingConfigs = samplingConfigs;
  }

  /**
   * Get the sampling configs to use for the scheme
   *
   * @return the sampling configs to use for the scheme
   */
  public Map<String, String> getSamplingConfigs() {
    return m_samplingConfigs;
  }

  // --------- engine configuration --------------

  /**
   * Set the engine to use
   *
   * @param engineName the name of the engine to use
   */
  public void setEngineName( String engineName ) {
    m_engineName = engineName;
  }

  /**
   * Get the engine to use
   *
   * @return the name of the engine to use
   */
  public String getEngineName() {
    return m_engineName;
  }

  // --------- field configuration ------------

  /**
   * Set the field metadata info to use
   *
   * @param fieldMetadata the field metadata
   */
  public void setFieldMetadata( List<ArffMeta> fieldMetadata ) {
    m_fieldMeta = fieldMetadata;
  }

  /**
   * Get the field metadata info to use
   *
   * @return the field metadata
   */
  public List<ArffMeta> getFieldMetadata() {
    return m_fieldMeta;
  }

  /**
   * Set the incoming field to be considered as the class attribute
   *
   * @param classAttribute the name of the incoming field to use for the class
   */
  public void setClassField( String classAttribute ) {
    m_classField = classAttribute;
  }

  /**
   * Get the incoming field to be considered as the class attribute
   *
   * @return the name of the incoming field to use for the class
   */
  public String getClassField() {
    return m_classField;
  }

  // Preprocessing is (at least initially) going to be field manupulation -
  // e.g. string fields -> numeric term/freq vectors; collapsing high arity
  // nominal fields

  /**
   * Set the preprocessing configs to use with the scheme
   *
   * @param preprocessingConfigs the preprocessing configs to use
   */
  public void setPreprocessingConfigs( Map<String, String> preprocessingConfigs ) {
    m_preprocessingConfigs = preprocessingConfigs;
  }

  /**
   * Get the preprocessing configs to use with the scheme
   *
   * @return the preprocessing configs to use
   */
  public Map<String, String> getPreprocessingConfigs() {
    return m_preprocessingConfigs;
  }

  // --------- scheme configuration -----------

  /**
   * Set the name of the scheme to use - this is "fixed" by the individual steps that
   * extend BasePMIStep for various schemes
   *
   * @param schemeName the name of the scheme to use
   */
  protected void setSchemeName( String schemeName ) {
    m_schemeName = schemeName;
  }

  /**
   * Get the name of the scheme to use
   *
   * @return the name of the scheme to use
   */
  public String getSchemeName() {
    return m_schemeName;
  }

  /**
   * Set the command-line options for the underlying scheme
   *
   * @param commandLineOptions the command-line options to use
   */
  public void setSchemeCommandLineOptions( String commandLineOptions ) {
    m_underlyingSchemeOptions = commandLineOptions;
  }

  /**
   * Get the command-line options for the underlying scheme
   *
   * @return the command-line options to use
   */
  public String getSchemeCommandLineOptions() {
    return m_underlyingSchemeOptions;
  }

  // ------------- model output opts -----------------

  /**
   * Set the path (directory) to save the model to
   *
   * @param path the path to save the model to
   */
  public void setModelOutputPath( String path ) {
    m_modelOutputPath = path;
  }

  /**
   * Get the path (directory) to save the model to
   *
   * @return the path to save the model to
   */
  public String getModelOutputPath() {
    return m_modelOutputPath;
  }

  /**
   * Set the file name to store the model as
   *
   * @param fileName the file name to store as
   */
  public void setModelFileName( String fileName ) {
    m_modelFileName = fileName;
  }

  /**
   * Get the file name to store the model as
   *
   * @return the file name to store as
   */
  public String getModelFileName() {
    return m_modelFileName;
  }

  /**
   * Set a path to a searialize resumable model to load (and continue training)
   *
   * @param resumableModelPath the path to the resumable model
   */
  public void setResumableModelPath(String resumableModelPath) {
    m_resumableModelLoadPath = resumableModelPath;
  }

  /**
   * Get the path to a searialize resumable model to load (and continue training)
   *
   * @return the path to the resumable model
   */
  public String getResumableModelPath() {
    return m_resumableModelLoadPath;
  }

  // ------------- evaluation opts -------------------

  /**
   * Set the evaluation mode to use
   *
   * @param mode the evaluation mode to use
   */
  public void setEvalMode( Evaluator.EvalMode mode ) {
    m_evalMode = mode;
  }

  /**
   * Get the evaluation mode to use
   *
   * @return the evaluation mode to use
   */
  public Evaluator.EvalMode getEvalMode() {
    return m_evalMode;
  }

  /**
   * Set the split percentage, if using percentage split eval.
   *
   * @param split the split percentage (1 - 99) to use for training
   */
  public void setPercentageSplit( String split ) {
    m_percentageSplit = split;
  }

  /**
   * Get the split percentage, if using percentage split eval.
   *
   * @return the split percentage (1 - 99) to use for training
   */
  public String getPercentageSplit() {
    return m_percentageSplit;
  }

  /**
   * Set the number of cross-validation folds to use ( >= 2)
   *
   * @param folds the number of cross-validation folds to use
   */
  public void setXValFolds( String folds ) {
    m_xValFolds = folds;
  }

  /**
   * Get the number of cross-validation folds to use ( >= 2)
   *
   * @return the number of cross-validation folds to use
   */
  public String getXValFolds() {
    return m_xValFolds;
  }

  /**
   * Set the random seed used when splitting data via percentage split or into folds
   *
   * @param randomSeed the seed to use in the random number generator
   */
  public void setRandomSeed( String randomSeed ) {
    m_randomSeed = randomSeed;
  }

  /**
   * Get the random seed used when splitting data via percentage split or into folds
   *
   * @return the seed to use in the random number generator
   */
  public String getRandomSeed() {
    return m_randomSeed;
  }

  /**
   * Set whether to output area under the curve metrics when evaluating
   *
   * @param outputAUCMetrics true to output area under the curve metrics
   */
  public void setOutputAUCMetrics( boolean outputAUCMetrics ) {
    m_outputAUCMetrics = outputAUCMetrics;
  }

  /**
   * Get whether to output area under the curve metrics when evaluating
   *
   * @return true to output area under the curve metrics
   */
  public boolean getOutputAUCMetrics() {
    return m_outputAUCMetrics;
  }

  /**
   * Set whether to output IR retrieval metrics when evaluating
   *
   * @param outputIRMetrics true to output IR metrics
   */
  public void setOutputIRMetrics( boolean outputIRMetrics ) {
    m_outputIRMetrics = outputIRMetrics;
  }

  /**
   * Get whether to output IR retrieval metrics when evaluating
   *
   * @return true to output IR metrics
   */
  public boolean getOutputIRMetrics() {
    return m_outputIRMetrics;
  }

  // -- incremental schemes ---

  /**
   * Set the number of rows to cache from the start of the incoming training stream when learning an incremental model.
   * This will only be used if 1) the underlying scheme supports incremental training and 2) the header for the data
   * stream cannot be fully determined from the incoming row metadata
   * (i.e. there are string fields that will be converted to nominal attributes and the user has not specified the
   * legal values apriori).
   *
   * @param rowCacheSize the number of rows to cache (if necessary) from the start of the stream
   */
  public void setInitialRowCacheForNominalValDetermination( String rowCacheSize ) {
    m_incrementalInitialCache = rowCacheSize;
  }

  /**
   * Get the number of rows to cache from the start of the incoming training stream when learning an incremental model.
   * This will only be used if 1) the underlying scheme supports incremental training and 2) the header for the data
   * stream cannot be fully determined from the incoming row metadata
   * (i.e. there are string fields that will be converted to nominal attributes and the user has not specified the
   * legal values apriori).
   *
   * @return the number of rows to cache (if necessary) from the start of the stream
   */
  public String getInitialRowCacheForNominalValDetermination() {
    return m_incrementalInitialCache;
  }

  @Override public void saveRep( Repository rep, IMetaStore metaStore, ObjectId id_transformation, ObjectId id_step )
      throws KettleException {
    rep.saveStepAttribute( id_transformation, id_step, ENGINE_NAME_TAG, getEngineName() );
    rep.saveStepAttribute( id_transformation, id_step, SCHEME_NAME_TAG, getSchemeName() );
    rep.saveStepAttribute( id_transformation, id_step, SCHEME_OPTS_TAG, getSchemeCommandLineOptions() );
    rep.saveStepAttribute( id_transformation, id_step, ROW_HANDLING_MODE, getRowsToProcess() );
    rep.saveStepAttribute( id_transformation, id_step, BATCH_SIZE_TAG, getBatchSize() );
    rep.saveStepAttribute( id_transformation, id_step, USE_RESERVOIR_SAMPLING_TAG, getUseReservoirSampling() );
    rep.saveStepAttribute( id_transformation, id_step, RESERVOIR_SAMPLING_SIZE_TAG, getReservoirSize() );
    rep.saveStepAttribute( id_transformation, id_step, RESERVOIR_SAMPLING_RANDOM_SEED_TAG,
        getRandomSeedReservoirSampling() );
    rep.saveStepAttribute( id_transformation, id_step, STRATIFICATION_FIELD_NAME_TAG, getStratificationFieldName() );
    rep.saveStepAttribute( id_transformation, id_step, CLASS_FIELD_TAG, getClassField() );
    rep.saveStepAttribute( id_transformation, id_step, TRAINING_STEP_INPUT_NAME_TAG, getTrainingStepInputName() );
    rep.saveStepAttribute( id_transformation, id_step, TEST_STEP_INPUT_NAME_TAG, getTestingStepInputName() );
    rep.saveStepAttribute( id_transformation, id_step, EVAL_MODE_TAG, getEvalMode().toString() );
    rep.saveStepAttribute( id_transformation, id_step, SPLIT_PERCENTAGE_TAG, getPercentageSplit() );
    rep.saveStepAttribute( id_transformation, id_step, X_VAL_FOLDS_TAG, getXValFolds() );
    rep.saveStepAttribute( id_transformation, id_step, RANDOM_SEED_TAG, getRandomSeed() );
    rep.saveStepAttribute( id_transformation, id_step, MODEL_OUTPUT_DIRECTORY_TAG, getModelOutputPath() );
    rep.saveStepAttribute( id_transformation, id_step, MODEL_FILE_NAME_TAG, getModelFileName() );
    rep.saveStepAttribute( id_transformation, id_step, OUTPUT_AUC_METRICS_TAG, getOutputAUCMetrics() );
    rep.saveStepAttribute( id_transformation, id_step, OUTPUT_IR_METRICS_TAG, getOutputIRMetrics() );
    rep.saveStepAttribute( id_transformation, id_step, INCREMENTAL_TRAININ_INITIAL_ROW_CACHE_SIZE_TAG,
        getInitialRowCacheForNominalValDetermination() );

    // incoming fields
    for ( int i = 0; i < m_fieldMeta.size(); i++ ) {
      m_fieldMeta.get( i ).saveRep( rep, id_transformation, id_step, i );
    }

    int i = 0;
    for ( Map.Entry<String, String> e : m_samplingConfigs.entrySet() ) {
      rep.saveStepAttribute( id_transformation, id_step, i++, SINGLE_SAMPLING_CONFIG_TAG,
          e.getKey() + " " + e.getValue() );
    }
    i = 0;
    for ( Map.Entry<String, String> e : m_preprocessingConfigs.entrySet() ) {
      rep.saveStepAttribute( id_transformation, id_step, i++, SINGLE_PREPROCESSING_CONFIG_TAG,
          e.getKey() + " " + e.getValue() );
    }
  }

  @Override public String getXML() {
    StringBuilder buff = new StringBuilder();

    buff.append( XMLHandler.addTagValue( ENGINE_NAME_TAG, getEngineName() ) );
    buff.append( XMLHandler.addTagValue( SCHEME_NAME_TAG, getSchemeName() ) );
    buff.append( XMLHandler.addTagValue( SCHEME_OPTS_TAG, getSchemeCommandLineOptions() ) );
    buff.append( XMLHandler.addTagValue( ROW_HANDLING_MODE, getRowsToProcess() ) );
    buff.append( XMLHandler.addTagValue( BATCH_SIZE_TAG, getBatchSize() ) );
    buff.append( XMLHandler.addTagValue( USE_RESERVOIR_SAMPLING_TAG, getUseReservoirSampling() ) );
    buff.append( XMLHandler.addTagValue( RESERVOIR_SAMPLING_RANDOM_SEED_TAG, getRandomSeedReservoirSampling() ) );
    buff.append( XMLHandler.addTagValue( RESERVOIR_SAMPLING_SIZE_TAG, getReservoirSize() ) );
    buff.append( XMLHandler.addTagValue( STRATIFICATION_FIELD_NAME_TAG, getStratificationFieldName() ) );
    buff.append( XMLHandler.addTagValue( CLASS_FIELD_TAG, getClassField() ) );
    buff.append( XMLHandler.addTagValue( TRAINING_STEP_INPUT_NAME_TAG, getTrainingStepInputName() ) );
    buff.append( XMLHandler.addTagValue( TEST_STEP_INPUT_NAME_TAG, getTestingStepInputName() ) );
    buff.append( XMLHandler.addTagValue( EVAL_MODE_TAG, getEvalMode().toString() ) );
    buff.append( XMLHandler.addTagValue( SPLIT_PERCENTAGE_TAG, getPercentageSplit() ) );
    buff.append( XMLHandler.addTagValue( X_VAL_FOLDS_TAG, getXValFolds() ) );
    buff.append( XMLHandler.addTagValue( RANDOM_SEED_TAG, getRandomSeed() ) );
    buff.append( XMLHandler.addTagValue( MODEL_OUTPUT_DIRECTORY_TAG, getModelOutputPath() ) );
    buff.append( XMLHandler.addTagValue( MODEL_FILE_NAME_TAG, getModelFileName() ) );
    buff.append( XMLHandler.addTagValue( OUTPUT_AUC_METRICS_TAG, getOutputAUCMetrics() ) );
    buff.append( XMLHandler.addTagValue( OUTPUT_IR_METRICS_TAG, getOutputIRMetrics() ) );
    buff.append( XMLHandler.addTagValue( INCREMENTAL_TRAININ_INITIAL_ROW_CACHE_SIZE_TAG,
        getInitialRowCacheForNominalValDetermination() ) );

    // incoming field metadata
    if ( m_fieldMeta.size() > 0 ) {
      buff.append( XMLHandler.openTag( INCOMING_FIELD_META_TAG ) ).append( Const.CR );
      for ( ArffMeta arffMeta : m_fieldMeta ) {
        buff.append( "  " ).append( arffMeta.getXML() ).append( Const.CR );
      }
      buff.append( XMLHandler.closeTag( INCOMING_FIELD_META_TAG ) ).append( Const.CR );
    }

    // sampling configs
    if ( m_samplingConfigs.size() > 0 ) {
      buff.append( XMLHandler.openTag( SAMPLING_CONFIGS_TAG ) ).append( Const.CR );
      int i = 0;
      for ( Map.Entry<String, String> e : m_samplingConfigs.entrySet() ) {
        buff.append( "  " )
            .append( XMLHandler.addTagValue( SINGLE_SAMPLING_CONFIG_TAG + i++, e.getKey() + " " + e.getValue() ) );
      }
      buff.append( XMLHandler.closeTag( SAMPLING_CONFIGS_TAG ) ).append( Const.CR );
    }

    // preprocessing configs
    if ( m_preprocessingConfigs.size() > 0 ) {
      buff.append( XMLHandler.openTag( PREPROCESSING_CONFIGS_TAG ) ).append( Const.CR );
      int i = 0;
      for ( Map.Entry<String, String> e : m_preprocessingConfigs.entrySet() ) {
        buff.append( "  " )
            .append( XMLHandler.addTagValue( SINGLE_PREPROCESSING_CONFIG_TAG + i++, e.getKey() + " " + e.getValue() ) );
      }
      buff.append( XMLHandler.closeTag( PREPROCESSING_CONFIGS_TAG ) ).append( Const.CR );
    }

    return buff.toString();
  }

  @Override public void readRep( Repository rep, IMetaStore metaStore, ObjectId id_step, List<DatabaseMeta> dbs )
      throws KettleException {
    setEngineName( rep.getStepAttributeString( id_step, ENGINE_NAME_TAG ) );
    setSchemeName( rep.getStepAttributeString( id_step, SCHEME_NAME_TAG ) );
    String schemeOpts = rep.getStepAttributeString( id_step, SCHEME_OPTS_TAG );
    setSchemeCommandLineOptions( schemeOpts == null ? "" : schemeOpts );
    String rowHandling = rep.getStepAttributeString( id_step, ROW_HANDLING_MODE );
    setRowsToProcess( rowHandling == null ? "" : rowHandling );
    String batchSize = rep.getStepAttributeString( id_step, BATCH_SIZE_TAG );
    setBatchSize( batchSize == null ? "" : batchSize );
    setUseReservoirSampling( rep.getStepAttributeBoolean( id_step, USE_RESERVOIR_SAMPLING_TAG ) );
    String reservoirSize = rep.getStepAttributeString( id_step, RESERVOIR_SAMPLING_SIZE_TAG );
    setReservoirSize( reservoirSize == null ? "" : reservoirSize );
    String reservoirSeed = rep.getStepAttributeString( id_step, RESERVOIR_SAMPLING_RANDOM_SEED_TAG );
    setRandomSeedReservoirSampling( reservoirSeed == null ? "1" : reservoirSeed );
    String stratificationField = rep.getStepAttributeString( id_step, STRATIFICATION_FIELD_NAME_TAG );
    setStratificationFieldName( stratificationField == null ? "" : stratificationField );
    String classField = rep.getStepAttributeString( id_step, CLASS_FIELD_TAG );
    setClassField( classField == null ? "" : classField );
    String trainingStepInput = rep.getStepAttributeString( id_step, TRAINING_STEP_INPUT_NAME_TAG );
    setTrainingStepInputName( trainingStepInput == null ? "" : trainingStepInput );
    String testStepInput = rep.getStepAttributeString( id_step, TEST_STEP_INPUT_NAME_TAG );
    setTestingStepInputName( testStepInput == null ? "" : testStepInput );
    String evalMode = rep.getStepAttributeString( id_step, EVAL_MODE_TAG );
    for ( Evaluator.EvalMode evalV : Evaluator.EvalMode.values() ) {
      if ( evalV.toString().equalsIgnoreCase( evalMode ) ) {
        setEvalMode( evalV );
        break;
      }
    }
    String splitPercentage = rep.getStepAttributeString( id_step, SPLIT_PERCENTAGE_TAG );
    setPercentageSplit( splitPercentage == null ? "" : splitPercentage );
    String xValFolds = rep.getStepAttributeString( id_step, X_VAL_FOLDS_TAG );
    setXValFolds( xValFolds == null ? "" : xValFolds );
    String randomSeed = rep.getStepAttributeString( id_step, RANDOM_SEED_TAG );
    setRandomSeed( randomSeed == null ? "" : randomSeed );
    String modelOutputPath = rep.getStepAttributeString( id_step, MODEL_OUTPUT_DIRECTORY_TAG );
    setModelOutputPath( modelOutputPath == null ? "" : modelOutputPath );
    String modelFileName = rep.getStepAttributeString( id_step, MODEL_FILE_NAME_TAG );
    setModelFileName( modelFileName == null ? "" : modelFileName );
    setOutputAUCMetrics( rep.getStepAttributeBoolean( id_step, OUTPUT_AUC_METRICS_TAG ) );
    setOutputIRMetrics( rep.getStepAttributeBoolean( id_step, OUTPUT_IR_METRICS_TAG ) );
    setInitialRowCacheForNominalValDetermination(
        rep.getStepAttributeString( id_step, INCREMENTAL_TRAININ_INITIAL_ROW_CACHE_SIZE_TAG ) );

    // incoming field metadata
    int numFields = rep.countNrStepAttributes( id_step, "field_name" );
    m_fieldMeta.clear();
    for ( int i = 0; i < numFields; i++ ) {
      m_fieldMeta.add( new ArffMeta( rep, id_step, i ) );
    }

    try {
      // sampling configs
      m_samplingConfigs.clear();
      numFields = rep.countNrStepAttributes( id_step, SINGLE_SAMPLING_CONFIG_TAG );
      for ( int i = 0; i < numFields; i++ ) {
        String config = rep.getStepAttributeString( id_step, i, SINGLE_SAMPLING_CONFIG_TAG );
        String[] parts = Utils.splitOptions( config );
        String samplerClass = parts[0].trim();
        parts[0] = "";
        m_samplingConfigs.put( samplerClass, Utils.joinOptions( parts ) );
      }

      // preprocessing configs
      m_preprocessingConfigs.clear();
      numFields = rep.countNrStepAttributes( id_step, SINGLE_PREPROCESSING_CONFIG_TAG );
      for ( int i = 0; i < numFields; i++ ) {
        String config = rep.getStepAttributeString( id_step, i, SINGLE_PREPROCESSING_CONFIG_TAG );
        String[] parts = Utils.splitOptions( config );
        String preprocessorClass = parts[0].trim();
        parts[0] = "";
        m_preprocessingConfigs.put( preprocessorClass, Utils.joinOptions( parts ) );
      }
    } catch ( Exception ex ) {
      throw new KettleException( ex );
    }

    List<StreamInterface> infoStreams = getStepIOMeta().getInfoStreams();
    if ( !Const.isEmpty( getTrainingStepInputName() ) ) {
      infoStreams.get( 0 ).setSubject( getTrainingStepInputName() );
    }
    if ( !Const.isEmpty( getTestingStepInputName() ) ) {
      infoStreams.get( 1 ).setSubject( getTestingStepInputName() );
    }
  }

  public void loadXML( Node stepnode, List<DatabaseMeta> dbs, IMetaStore metaStore ) throws KettleXMLException {
    setEngineName( XMLHandler.getTagValue( stepnode, ENGINE_NAME_TAG ) );
    setSchemeName( XMLHandler.getTagValue( stepnode, SCHEME_NAME_TAG ) );
    String schemeOpts = XMLHandler.getTagValue( stepnode, SCHEME_OPTS_TAG );
    setSchemeCommandLineOptions( schemeOpts == null ? "" : schemeOpts );
    String rowHandling = XMLHandler.getTagValue( stepnode, ROW_HANDLING_MODE );
    setRowsToProcess( rowHandling == null ? "" : rowHandling );
    String batchSize = XMLHandler.getTagValue( stepnode, BATCH_SIZE_TAG );
    setBatchSize( batchSize == null ? "" : batchSize );
    setUseReservoirSampling( XMLHandler.getTagValue( stepnode, USE_RESERVOIR_SAMPLING_TAG ).equalsIgnoreCase( "Y" ) );
    String reservoirSize = XMLHandler.getTagValue( stepnode, RESERVOIR_SAMPLING_SIZE_TAG );
    setReservoirSize( reservoirSize == null ? "" : reservoirSize );
    String reservoirSeed = XMLHandler.getTagValue( stepnode, RESERVOIR_SAMPLING_RANDOM_SEED_TAG );
    setRandomSeedReservoirSampling( reservoirSeed == null ? "1" : reservoirSeed );
    String stratificationField = XMLHandler.getTagValue( stepnode, STRATIFICATION_FIELD_NAME_TAG );
    setStratificationFieldName( stratificationField == null ? "" : stratificationField );
    String classField = XMLHandler.getTagValue( stepnode, CLASS_FIELD_TAG );
    setClassField( classField == null ? "" : classField );
    String trainingStepInput = XMLHandler.getTagValue( stepnode, TRAINING_STEP_INPUT_NAME_TAG );
    setTrainingStepInputName( trainingStepInput == null ? "" : trainingStepInput );
    String testStepInput = XMLHandler.getTagValue( stepnode, TEST_STEP_INPUT_NAME_TAG );
    setTestingStepInputName( testStepInput == null ? "" : testStepInput );
    String evalMode = XMLHandler.getTagValue( stepnode, EVAL_MODE_TAG );
    for ( Evaluator.EvalMode evalV : Evaluator.EvalMode.values() ) {
      if ( evalV.toString().equalsIgnoreCase( evalMode ) ) {
        setEvalMode( evalV );
        break;
      }
    }
    String splitPercentage = XMLHandler.getTagValue( stepnode, SPLIT_PERCENTAGE_TAG );
    setPercentageSplit( splitPercentage == null ? "" : splitPercentage );
    String xValFolds = XMLHandler.getTagValue( stepnode, X_VAL_FOLDS_TAG );
    setXValFolds( xValFolds == null ? "" : xValFolds );
    String randomSeed = XMLHandler.getTagValue( stepnode, RANDOM_SEED_TAG );
    setRandomSeed( randomSeed == null ? "" : randomSeed );
    String modelOutputPath = XMLHandler.getTagValue( stepnode, MODEL_OUTPUT_DIRECTORY_TAG );
    setModelOutputPath( modelOutputPath == null ? "" : modelOutputPath );
    String modelFileName = XMLHandler.getTagValue( stepnode, MODEL_FILE_NAME_TAG );
    setModelFileName( modelFileName == null ? "" : modelFileName );
    setOutputAUCMetrics( XMLHandler.getTagValue( stepnode, OUTPUT_AUC_METRICS_TAG ).equalsIgnoreCase( "Y" ) );
    setOutputIRMetrics( XMLHandler.getTagValue( stepnode, OUTPUT_IR_METRICS_TAG ).equalsIgnoreCase( "Y" ) );
    String incrementalCache = XMLHandler.getTagValue( stepnode, INCREMENTAL_TRAININ_INITIAL_ROW_CACHE_SIZE_TAG );
    setInitialRowCacheForNominalValDetermination( incrementalCache == null ? "100" : incrementalCache );

    // incoming field metadata
    Node fields = XMLHandler.getSubNode( stepnode, INCOMING_FIELD_META_TAG );
    if ( fields != null ) {
      int nrFields = XMLHandler.countNodes( fields, ArffMeta.XML_TAG );
      m_fieldMeta.clear();
      for ( int i = 0; i < nrFields; i++ ) {
        m_fieldMeta.add( new ArffMeta( XMLHandler.getSubNodeByNr( fields, ArffMeta.XML_TAG, i ) ) );
      }
    }

    try {
      // sampling configs
      fields = XMLHandler.getSubNode( stepnode, SAMPLING_CONFIGS_TAG );
      if ( fields != null ) {
        int i = 0;
        Node confNode = null;
        while ( ( confNode = XMLHandler.getSubNode( fields, SINGLE_SAMPLING_CONFIG_TAG + i ) ) != null ) {
          String config = XMLHandler.getNodeValue( confNode );
          String[] parts = Utils.splitOptions( config );
          String samplerClass = parts[0].trim();
          parts[0] = "";
          m_samplingConfigs.put( samplerClass, Utils.joinOptions( parts ) );
          i++;
        }
      }

      // preprocessing configs
      fields = XMLHandler.getSubNode( stepnode, PREPROCESSING_CONFIGS_TAG );
      if ( fields != null ) {
        int i = 0;
        Node confNode = null;
        while ( ( confNode = XMLHandler.getSubNode( fields, SINGLE_PREPROCESSING_CONFIG_TAG + i ) ) != null ) {
          String config = XMLHandler.getNodeValue( confNode );
          String[] parts = Utils.splitOptions( config );
          String preprocessorClass = parts[0].trim();
          parts[0] = "";
          m_preprocessingConfigs.put( preprocessorClass, Utils.joinOptions( parts ) );
          i++;
        }
      }
    } catch ( Exception e ) {
      throw new KettleXMLException( e );
    }

    List<StreamInterface> infoStreams = getStepIOMeta().getInfoStreams();
    if ( !Const.isEmpty( getTrainingStepInputName() ) ) {
      infoStreams.get( 0 ).setSubject( getTrainingStepInputName() );
    }
    if ( !Const.isEmpty( getTestingStepInputName() ) ) {
      infoStreams.get( 1 ).setSubject( getTestingStepInputName() );
    }
  }

  @Override
  public void getFields( RowMetaInterface rowMeta, String stepName, RowMetaInterface[] info, StepMeta nextStep,
      VariableSpace space, Repository repo, IMetaStore metaStore ) throws KettleStepException {
    try {
      BaseSupervisedPMIStepData.establishOutputRowMeta( rowMeta, space, this );
    } catch ( KettlePluginException e ) {
      throw new KettleStepException( e );
    }
  }

  @Override public void setDefault() {
    m_rowsToProcess = DEFAULT_ROWS_TO_PROCESS;
    m_engineName = WekaEngine.ENGINE_NAME;
  }

  @Override public StepDataInterface getStepData() {
    return new BaseSupervisedPMIStepData();
  }

  @Override
  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int i, TransMeta transMeta,
      Trans trans ) {
    return new BaseSupervisedPMIStep( stepMeta, stepDataInterface, i, transMeta, trans );
  }

  @Override public String getDialogClassName() {
    return BaseSupervisedPMIStepDialog.class.getCanonicalName();
  }

  public void clearStepIOMeta() {
    super.resetStepIoMeta();
  }

  @Override public void resetStepIoMeta() {
    // Don't reset!
  }

  @Override public void searchInfoAndTargetSteps( List<StepMeta> steps ) {
    List<StreamInterface> targetStreams = getStepIOMeta().getTargetStreams();

    for ( StreamInterface stream : targetStreams ) {
      stream.setStepMeta( StepMeta.findStep( steps, (String) stream.getSubject() ) );
    }
  }

  @Override public StepIOMetaInterface getStepIOMeta() {
    StepIOMetaInterface ioMeta = super.getStepIOMeta( false );

    if ( ioMeta == null ) {
      ioMeta = new StepIOMeta( true, true, false, true, false, false );

      ioMeta.addStream( new Stream( StreamInterface.StreamType.INFO, null, "Training stream", StreamIcon.INFO, null ) );
      // if ( getEvalMode() == Evaluator.EvalMode.SEPARATE_TEST_SET ) {
      ioMeta.addStream( new Stream( StreamInterface.StreamType.INFO, null, "Test stream", StreamIcon.INFO, null ) );
      // }
      setStepIOMeta( ioMeta );
    }

    return ioMeta;
  }
}
