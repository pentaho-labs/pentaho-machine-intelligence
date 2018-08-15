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
import org.pentaho.di.core.RowSet;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.exception.KettleValueException;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.errorhandling.StreamInterface;
import org.pentaho.di.trans.steps.reservoirsampling.ReservoirSamplingData;
import org.pentaho.dm.commons.ArffMeta;
import org.pentaho.pmi.Evaluator;
import org.pentaho.pmi.PMIEngine;
import org.pentaho.pmi.Scheme;
import weka.classifiers.Classifier;
import weka.classifiers.UpdateableClassifier;
import weka.core.Attribute;
import weka.core.BatchPredictor;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.SerializationHelper;
import weka.core.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.pentaho.di.trans.steps.pmi.BaseSupervisedPMIStepData.RowHandlingMode.All;
import static org.pentaho.di.trans.steps.pmi.BaseSupervisedPMIStepData.RowHandlingMode.Batch;
import static org.pentaho.di.trans.steps.pmi.BaseSupervisedPMIStepData.RowHandlingMode.Stratified;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class BaseSupervisedPMIStepData extends BaseStepData implements StepDataInterface {

  private static Class<?> PKG = BaseSupervisedPMIStepMeta.class;

  protected enum RowHandlingMode {All, Batch, Stratified}

  protected PMIEngine m_engine;
  protected Scheme m_scheme;

  protected RowHandlingMode m_rowHandlingMode = null;

  protected int m_batchSize = -1;

  /**
   * Keeps track of the current batch number
   */
  protected int m_batchCount = 1;

  /**
   * count rows (used for incremental classifiers during batch row handling mode)
   */
  protected int m_rowCount = 1;

  protected int m_reservoirSize;
  protected int m_randomSeed = 1;

  protected StreamInterface m_trainingStream;
  protected StreamInterface m_testStream;
  protected RowSet m_trainingRowSet;
  protected RowSet m_testRowSet;
  protected RowMetaInterface m_trainingRowMeta;
  protected RowMetaInterface m_testingRowMeta;

  /**
   * Holds indexes into the training stream for ArffMeta fields
   */
  protected Map<String, Integer> m_trainingFieldIndexes = new HashMap<>();

  /**
   * If there is a separate test stream, then this holds indexes into the test stream for ArffMeta fields
   */
  protected Map<String, Integer> m_testingFieldIndexes = new HashMap<>();

  /**
   * The index in the incoming training row structure of the class/target
   */
  protected int m_classIndex;
  protected int m_separateTestClassIndex;

  protected ArffMeta m_classArffMeta;

  /**
   * The index of the stratification field (if stratifying). This will not be part of the input to the model building
   * process
   */
  protected int m_stratificationIndex = -1;
  protected int m_separateTestStratificationIndex = -1;
  protected String m_stratificationFieldName = "";

  /**
   * Default of 10
   */
  protected int m_xValFolds = 10;

  /**
   * Default 2/3 1/3
   */
  protected int m_percentageSplit = 66;

  /**
   * training batch or sampling. We assume sorted data (by stratification field) for stratified mode
   */
  protected List<Object[]> m_trainingRows = new ArrayList<>();

  /**
   * Initial rows for incremental classifiers (stratified mode primarily) when a global header cannot be determined
   * at the outset from field metadata.
   */
  protected Map<String, List<Object[]>> m_initialIncrementalRows = new HashMap<>();

  protected ReservoirSamplingData m_trainingSampler;

  /**
   * Rows for BatchPredictors when doing separate test set evaluation
   */
  protected List<Object[]> m_separateTestSetBatchPredictorRows = new ArrayList<>();

  /**
   * Used to check for previously seen stratification values.
   */
  protected Set<String> m_stratificationCheck = new HashSet<>();

  /**
   * The current value for stratification
   */
  protected String m_currentStratificationValue = "";

  protected RowMetaInterface m_outputRowMeta;

  protected Map<String, Evaluator> m_evaluation = new HashMap<>();
  protected Map<String, Classifier> m_finalModels = new HashMap<>();
  protected Map<String, Instances> m_trainingHeaders = new HashMap<>();

  protected String m_modelOutputPath = "";
  protected String m_modelFileName = "";

  /**
   * -1 means process all rows as one batch when predicting
   */
  protected int m_batchPredictorPreferredBatchSize = -1;

  protected boolean m_schemeIsMoreEfficientBatchPredictor;

  /**
   * The number of instances to batch up for determining header info when incrementally training
   */
  protected int m_numInstancesForStreamingHeaderDetermination = 100;

  protected boolean m_trainingIncrementally;
  protected boolean m_incrementalHeaderDetermined;

  /**
   * Headers primarily for stratified mode. If a global header can be determined prior to seeing data, then each
   * stratified header will be identical; otherwise, each stratified dataset may contain different sets of values for
   * nominal attributes.
   */
  protected Map<String, Instances> m_incrementalHeaders;
  protected Instances m_incrementalHeader;
  protected Map<String, Evaluator> m_prequentialEvaluator;
  // protected Evaluator m_prequentialEvaluator;
  protected Map<String, Classifier> m_incrementalClassifier;
  // protected Classifier m_incrementalClassifier;

  protected void checkForIncrementalTraining( BaseSupervisedPMIStepMeta stepMeta, LogChannelInterface log )
      throws Exception {
    m_trainingIncrementally =
        m_scheme.supportsIncrementalTraining() && ( stepMeta.getEvalMode() == Evaluator.EvalMode.NONE
            || stepMeta.getEvalMode() == Evaluator.EvalMode.SEPARATE_TEST_SET
            || stepMeta.getEvalMode() == Evaluator.EvalMode.PREQUENTIAL );

    if ( m_trainingIncrementally ) {
      log.logBasic( BaseMessages.getString( PKG, "BasePMIStepData.TrainingIncrementally" ) );
    }

    m_incrementalHeaderDetermined = headerCanBeDeterminedImmediately( stepMeta );

    if ( m_trainingIncrementally ) {
      m_incrementalClassifier = new HashMap<>();
      m_incrementalHeaders = new HashMap<>();
      m_prequentialEvaluator = new HashMap<>();

      try {
        m_numInstancesForStreamingHeaderDetermination =
            Integer.parseInt( stepMeta.getInitialRowCacheForNominalValDetermination() );
      } catch ( NumberFormatException n ) {
        log.logBasic( BaseMessages.getString( PKG, "BasePMIStepData.Warning.UnableToParseIncrementalCacheSize",
            stepMeta.getInitialRowCacheForNominalValDetermination() ) );
      }

      if ( m_incrementalHeaderDetermined ) {
        initializeIncrementalClassifierAndEval( stepMeta, log,
            m_rowHandlingMode == Stratified ? null : "--Not stratified--" );
      }
    }
  }

  protected void initializeIncrementalClassifierAndEval( BaseSupervisedPMIStepMeta stepMeta, LogChannelInterface log,
      String stratVal ) throws Exception {

    // construct global header?
    if ( m_incrementalHeader == null && m_incrementalHeaderDetermined ) {
      m_incrementalHeader = determineHeader( m_trainingRows, "incremental training data", stepMeta );
    }

    if ( stratVal != null ) {
      m_incrementalClassifier
          .put( stratVal, (Classifier) m_scheme.getConfiguredScheme( m_incrementalHeaders.get( stratVal ) ) );
      if ( m_incrementalHeaderDetermined ) {
        m_incrementalClassifier.get( stratVal ).buildClassifier( m_incrementalHeader );
        m_incrementalHeaders.put( stratVal, m_incrementalHeader ); // store the global header
      }
    }

    if ( stepMeta.getEvalMode() == Evaluator.EvalMode.PREQUENTIAL ) {
      log.logBasic( BaseMessages.getString( PKG, "BasePMIStepData.PerformingPrequentialEvaluation" ) );

      if ( stratVal != null ) {
        m_prequentialEvaluator.put( stratVal,
            new Evaluator( stepMeta.getEvalMode(), m_randomSeed, stepMeta.getOutputAUCMetrics(),
                stepMeta.getOutputIRMetrics() ) );
        if ( m_incrementalHeaders.get( stratVal ) != null ) {
          m_prequentialEvaluator.get( stratVal )
              .initializeNoPriors( m_incrementalHeaders.get( stratVal ), m_incrementalClassifier.get( stratVal ) );
        }
      }
    }
  }

  protected void checkAllIncrementalHeaderCreationAndClearCache( BaseSupervisedPMIStepMeta stepMeta,
      LogChannelInterface log ) throws Exception {
    for ( Map.Entry<String, List<Object[]>> e : m_initialIncrementalRows.entrySet() ) {
      String stratVal = e.getKey();
      List<Object[]> rowsForStratVal = e.getValue();
      checkIncrementalHeaderCreationAndClearCache( stepMeta, log, stratVal, rowsForStratVal );
    }
  }

  protected void checkIncrementalHeaderCreationAndClearCache( BaseSupervisedPMIStepMeta stepMeta,
      LogChannelInterface log, String stratVal, List<Object[]> rowsForStratVal ) throws Exception {
    if ( rowsForStratVal.size() > 0 ) {
      // clear buffered instances first...

      Instances
          cached =
          buildDataset( m_incrementalHeaders.get( stratVal ), m_trainingRowMeta, rowsForStratVal,
              m_trainingFieldIndexes, stepMeta );
      rowsForStratVal.clear();
      m_incrementalClassifier.get( stratVal ).buildClassifier( m_incrementalHeaders.get( stratVal ) );
      // prequentially evaluate and train
      for ( int i = 0; i < cached.numInstances(); i++ ) {
        Instance current = cached.instance( i );

        if ( m_prequentialEvaluator != null && m_prequentialEvaluator.get( stratVal ) != null ) {
          // test first
          m_prequentialEvaluator.get( stratVal ).performEvaluationIncremental( current, log );
        }
        // then train
        ( (UpdateableClassifier) m_incrementalClassifier.get( stratVal ) ).updateClassifier( current );
      }
    }
  }

  protected Object[][] processRowIncremental( Object[] row, BaseSupervisedPMIStepMeta stepMeta, LogChannelInterface log,
      VariableSpace vars, String stratVal ) throws Exception {

    Object[][] result = null;
    if ( row != null ) {
      // prequentially evaluate (if necessary) and then train
      Instance
          toProcess =
          constructInstance( m_incrementalHeaders.get( stratVal ), m_trainingRowMeta, row, m_trainingFieldIndexes,
              stepMeta );
      if ( m_prequentialEvaluator != null && m_prequentialEvaluator.get( stratVal ) != null ) {
        // test first
        m_prequentialEvaluator.get( stratVal ).performEvaluationIncremental( toProcess, log );
      }
      // then train
      ( (UpdateableClassifier) m_incrementalClassifier.get( stratVal ) ).updateClassifier( toProcess );
    } else {
      // done so get eval row (if necessary)
      // have to iterate over map and save all models
      result = outputRowIncremental( stepMeta, log, vars );
    }

    return result;
  }

  protected Object[][] outputRowIncremental( BaseSupervisedPMIStepMeta stepMeta, LogChannelInterface log,
      VariableSpace vars ) throws KettleException {
    Object[][] result = null;

    // done so get eval row (if necessary)
    // have to iterate over map and save all models
    result = new Object[m_incrementalClassifier.size()][];
    if ( m_prequentialEvaluator != null ) {
      int i = 0;
      for ( String stratKey : m_prequentialEvaluator.keySet() ) {
        result[i++] =
            m_prequentialEvaluator.get( stratKey )
                .getEvalRow( m_rowHandlingMode == Stratified ? stratKey : null, m_outputRowMeta, -1 );
      }
    } else {
      int i = 0;
      for ( String stratKey : m_incrementalClassifier.keySet() ) {
        result[i] = RowDataUtil.allocateRowData( m_outputRowMeta.size() );
        String modelText = m_incrementalClassifier.get( stratKey ).toString();
        if ( m_rowHandlingMode == Stratified ) {
          result[i][0] = stratKey;
          result[i][1] = modelText;
        } else {
          result[i][0] = modelText;
        }
        i++;
      }
    }

    if ( !Const.isEmpty( m_modelOutputPath ) ) {
      for ( String stratKey : m_incrementalClassifier.keySet() ) {
        // save model to file
        m_currentStratificationValue = stratKey;
        saveModel( m_incrementalClassifier.get( stratKey ), m_incrementalHeaders.get( stratKey ), stepMeta, log );
      }
    }

    m_rowCount = 1; // reset row count for batch mode

    return result;
  }

  protected Object[][] handleIncrementalTrainingRow( Object[] row, BaseSupervisedPMIStepMeta stepMeta,
      LogChannelInterface log, VariableSpace vars ) throws Exception {

    Object[][] result = null;
    if ( row == null ) {
      checkAllIncrementalHeaderCreationAndClearCache( stepMeta, log );
      result = processRowIncremental( null, stepMeta, log, vars, null );
    } else {
      String currentStratVal = "--Not stratified--";
      if ( m_stratificationIndex > -1 ) {
        Object stratVal = row[m_stratificationIndex];
        ValueMetaInterface stratVM = m_trainingRowMeta.getValueMeta( m_stratificationIndex );
        currentStratVal = stratVM.getString( stratVal );
      }
      if ( !m_incrementalHeaders.containsKey( currentStratVal ) ) {
        if ( m_incrementalHeaderDetermined ) {
          // use the global header and initialize a new classifier for this stratification value
          initializeIncrementalClassifierAndEval( stepMeta, log, currentStratVal );
        } else {
          // check the cached rows for this stratification value
          List<Object[]> stratRows = m_initialIncrementalRows.get( currentStratVal );
          if ( stratRows == null ) {
            stratRows = new ArrayList<>();
            m_initialIncrementalRows.put( currentStratVal, stratRows );
          }
          // m_trainingRows.add( row );
          stratRows.add( row );
          if ( stratRows.size() == m_numInstancesForStreamingHeaderDetermination ) {
            m_incrementalHeaders
                .put( currentStratVal, determineHeader( m_trainingRows, "incremental training data", stepMeta ) );
            // m_incrementalHeaderDetermined = true;
            initializeIncrementalClassifierAndEval( stepMeta, log, currentStratVal );
          }
        }
      } else {
        checkIncrementalHeaderCreationAndClearCache( stepMeta, log, currentStratVal,
            m_initialIncrementalRows.get( currentStratVal ) );
        result = processRowIncremental( row, stepMeta, log, vars, currentStratVal );
        if ( m_rowHandlingMode == Batch && m_rowCount > 1 ) {
          if ( m_rowCount % m_batchSize == 0 ) {
            // Output current model (if necessary) and output eval row (if necessary); then reset classifier for next batch
            outputRowIncremental( stepMeta, log, vars );
            m_initialIncrementalRows.get( "--Not stratified--" )
                .clear(); // force a new header to be determined (if necessary)
            initializeIncrementalClassifierAndEval( stepMeta, log, "--Not stratified--" );
            m_rowCount = 1;
          }
          m_rowCount++;
        }
      }
    }

    return result;
  }

  protected Object[][] handleTrainingRow( Object[] row, BaseSupervisedPMIStepMeta stepMeta, LogChannelInterface log,
      VariableSpace vars ) throws KettleException {
    // TODO - handle incremental learners (only Weka at present) at some stage in the future.
    // Could support MOA's streaming learners as another "engine" perhaps

    if ( m_trainingIncrementally ) {
      try {
        return handleIncrementalTrainingRow( row, stepMeta, log, vars );
      } catch ( Exception ex ) {
        throw new KettleException( ex );
      }
    }

    Object[][] evaluationOutputRow = null;
    if ( m_rowHandlingMode == All || m_rowHandlingMode == Batch ) {
      if ( row != null ) {
        if ( stepMeta.getUseReservoirSampling() ) {
          m_trainingSampler.processRow( row );
        } else {
          m_trainingRows.add( row );
          if ( m_rowHandlingMode == Batch && m_trainingRows.size() == m_batchSize ) {
            evaluationOutputRow = new Object[1][];
            evaluationOutputRow[0] =
                processTrainingBatch( m_trainingRows, null, stepMeta, "Batch training data", log, vars );
            m_trainingRows.clear();
          }
        }
      } else {
        // no more rows
        List<Object[]> data = stepMeta.getUseReservoirSampling() ? m_trainingSampler.getSample() : m_trainingRows;
        if ( data != null && data.size() > 0 ) {
          evaluationOutputRow = new Object[1][];
          evaluationOutputRow[0] = processTrainingBatch( data, null, stepMeta, "Batch training data", log, vars );
        }
      }
    } else {
      // stratified mode
      if ( row != null ) {
        Object stratVal = row[m_stratificationIndex];
        ValueMetaInterface stratVM = m_trainingRowMeta.getValueMeta( m_stratificationIndex );
        if ( stratVM.isNull( stratVal ) ) {
          log.logDetailed( BaseMessages.getString( PKG, "BasePMIStep.Warning.NullStratificationFieldValue" ) );
          // TODO perhaps send this to the error stream?
        } else {
          if ( m_currentStratificationValue.equals( stratVM.getString( stratVal ) ) ) {
            if ( stepMeta.getUseReservoirSampling() ) {
              m_trainingSampler.processRow( row );
            } else {
              m_trainingRows.add( row );
            }
          } else {
            if ( !Const.isEmpty( m_currentStratificationValue ) ) {
              if ( m_stratificationCheck.contains( stratVM.getString( stratVal ) ) ) {
                throw new KettleException( BaseMessages
                    .getString( PKG, "BasePMIStep.Error.PreviouslySeenStratificationValue",
                        stratVM.getString( stratVal ) ) );
              }
              if ( stepMeta.getUseReservoirSampling() ) {
                List<Object[]> dataToTrainFrom = m_trainingSampler.getSample();
                if ( dataToTrainFrom != null ) {
                  evaluationOutputRow = new Object[1][];
                  evaluationOutputRow[0] =
                      processTrainingBatch( dataToTrainFrom, stratVM.getString( stratVal ), stepMeta,
                          "Stratified training sample", log, vars );
                }
                m_trainingSampler.cleanUp();
                m_trainingSampler.initialize( m_reservoirSize, m_randomSeed );
              } else if ( m_trainingRows.size() > 0 ) {
                evaluationOutputRow = new Object[1][];
                evaluationOutputRow[0] =
                    processTrainingBatch( m_trainingRows, m_currentStratificationValue, stepMeta,
                        "Stratified training data", log, vars );
                m_trainingRows.clear();
              }
              m_stratificationCheck.add( stratVM.getString( stratVal ) );
            }
            m_currentStratificationValue = stratVM.getString( stratVal );
            m_trainingRows.add( row );
          }
        }
      } else {
        // no more rows
        List<Object[]>
            dataToTrainFrom =
            stepMeta.getUseReservoirSampling() ? m_trainingSampler.getSample() : m_trainingRows;
        String stratVal = m_rowHandlingMode == Stratified ? m_currentStratificationValue : null;
        if ( dataToTrainFrom != null ) {
          evaluationOutputRow = new Object[1][];
          evaluationOutputRow[0] =
              processTrainingBatch( dataToTrainFrom, stratVal, stepMeta,
                  stepMeta.getUseReservoirSampling() ? "Stratified training sample" : "Stratified training data", log,
                  vars );
        }

        // reset current stratification value just in case we have a separate test set
        m_currentStratificationValue = "";
        m_stratificationCheck.clear();
      }
    }
    return evaluationOutputRow;
  }

  protected Object[] processTrainingBatch( List<Object[]> data, String stratificationValue,
      BaseSupervisedPMIStepMeta stepMeta, String relationName, LogChannelInterface log, VariableSpace vars )
      throws KettleException {

    Object[] outputRow = null;
    if ( data.size() > 0 ) {
      Instances trainingHeader = determineHeader( data, relationName, stepMeta );

      // build the training dataset
      Instances
          trainingData =
          buildDataset( trainingHeader, m_trainingRowMeta, data, m_trainingFieldIndexes, stepMeta );
      String evalKey = stratificationValue;
      if ( m_rowHandlingMode != Stratified ) {
        m_evaluation.clear();
        m_trainingHeaders.clear();
        evalKey = "non-stratified";
      }
      Evaluator
          evaluator =
          new Evaluator( stepMeta.getEvalMode(), m_randomSeed, stepMeta.getOutputAUCMetrics(),
              stepMeta.getOutputIRMetrics() );
      m_evaluation.put( evalKey, evaluator );
      m_trainingHeaders.put( evalKey, trainingHeader );
      if ( stepMeta.getEvalMode() == Evaluator.EvalMode.PERCENTAGE_SPLIT ) {
        evaluator.setPercentageSplit( m_percentageSplit );
      } else if ( stepMeta.getEvalMode() == Evaluator.EvalMode.CROSS_VALIDATION ) {
        evaluator.setXValFolds( m_xValFolds );
      }
      evaluator.setRandomSeed( m_randomSeed );
      try {
        // perform evaluation (if necessary)
        Classifier currentClassifier = (Classifier) m_scheme.getConfiguredScheme( trainingHeader );
        log.logDebug( "Training current classifier: " + currentClassifier.getClass().getCanonicalName() + " " + Utils
            .joinOptions( ( (OptionHandler) currentClassifier ).getOptions() ) );
        if ( currentClassifier instanceof BatchPredictor && ( (BatchPredictor) currentClassifier )
            .implementsMoreEfficientBatchPrediction() ) {
          m_schemeIsMoreEfficientBatchPredictor = true;
        }
        evaluator.initialize( trainingData, currentClassifier );

        // store the preferred batch prediction batch size (if necessary)
        if ( m_schemeIsMoreEfficientBatchPredictor ) {
          String prefBatchS = ( (BatchPredictor) evaluator.getClassifierTemplate() ).getBatchSize();
          if ( !Const.isEmpty( prefBatchS ) ) {
            m_batchPredictorPreferredBatchSize = Integer.parseInt( vars.environmentSubstitute( prefBatchS ) );
          }
        }
        evaluator.performEvaluation( null, log );

        outputRow =
            evaluator
                .getEvalRow( stratificationValue, m_outputRowMeta, m_rowHandlingMode == Batch ? m_batchCount : -1 );

        // build final model on all the data (but only if it is going to be saved somewhere or separate test set eval or there is no eval being done)
        if ( !Const.isEmpty( m_modelOutputPath ) || stepMeta.getEvalMode() == Evaluator.EvalMode.SEPARATE_TEST_SET
            || stepMeta.getEvalMode() == Evaluator.EvalMode.NONE ) {
          Classifier trainedFullModel = evaluator.buildFinalModel( log );
          m_finalModels.put( evalKey, trainedFullModel );

          // save model to file
          saveModel( trainedFullModel, trainingHeader, stepMeta, log );

          // output row is textual model?
          if ( stepMeta.getEvalMode() == Evaluator.EvalMode.NONE ) {
            outputRow = RowDataUtil.allocateRowData( Const.isEmpty( stratificationValue ) ? 1 : 2 );
            if ( !Const.isEmpty( stratificationValue ) ) {
              outputRow[0] = stratificationValue;
              outputRow[1] = trainedFullModel.toString();
            } else {
              outputRow[0] = trainedFullModel.toString();
            }
          }
        }
      } catch ( Exception ex ) {
        throw new KettleException( ex );
      }
    }
    return outputRow;
  }

  protected List<Object[]> handleSeparateTestRow( Object[] row, BaseSupervisedPMIStepMeta stepMeta,
      LogChannelInterface log, VariableSpace vars ) throws KettleException {

    if ( m_rowHandlingMode == Batch ) {
      throw new KettleException(
          BaseMessages.getString( PKG, "BasePMIStep.Error.SeparateTestSetEvalCantBeDoneForBatchRowHandling" ) );
    }

    // String stratVal = m_rowHandlingMode == Stratified ? m_currentStratificationValue : null;
    // String evalKey = stratVal == null ? "non-stratified" : stratVal;

    List<Object[]> evaluationOutputRows = new ArrayList<>();
    try {
      if ( m_rowHandlingMode == All ) {
        Instances header = m_trainingHeaders.get( "non-stratified" );
        Evaluator evaluator = m_evaluation.get( "non-stratified" );
        if ( row != null ) {
          if ( !m_schemeIsMoreEfficientBatchPredictor ) {
            Instance toTest = constructInstance( header, m_testingRowMeta, row, m_testingFieldIndexes, stepMeta );
            evaluator.performEvaluationIncremental( toTest, log );
          } else {
            // batch prediction
            // TODO move this batch size to the training phase
            m_separateTestSetBatchPredictorRows.add( row );
            if ( m_separateTestSetBatchPredictorRows.size() == m_batchPredictorPreferredBatchSize ) {
              processSeparateTestBatch( m_separateTestSetBatchPredictorRows, evaluator, header, stepMeta,
                  "separate test set", log, vars );

              // ready for next batch of test rows
              m_separateTestSetBatchPredictorRows.clear();
            }
          }
        } else {
          // done for incremental, but not necessarily for batch
          if ( m_schemeIsMoreEfficientBatchPredictor ) {
            if ( m_separateTestSetBatchPredictorRows.size() > 0 ) {
              processSeparateTestBatch( m_separateTestSetBatchPredictorRows, evaluator, header, stepMeta,
                  "separate test set", log, vars );
              // save memory
              m_separateTestSetBatchPredictorRows.clear();
            }
          }
          if ( evaluator.wasEvaluationPerformed() ) {
            evaluationOutputRows.add( evaluator.getEvalRow( null, m_outputRowMeta, -1 ) );
          }
        }
      } else if ( row != null ) {
        // stratified mode
        Object stratVal = row[m_separateTestStratificationIndex];
        ValueMetaInterface stratVM = m_testingRowMeta.getValueMeta( m_separateTestStratificationIndex );
        if ( stratVM.isNull( stratVal ) ) {
          log.logDetailed(
              BaseMessages.getString( PKG, "BasePMIStep.Warning.NullStratificationFieldValueSeparateTestSet" ) );
        } else {
          String stratS = stratVM.getString( stratVal );
          Evaluator evaluator = m_evaluation.get( stratS );
          if ( evaluator == null ) {
            log.logDetailed( BaseMessages
                .getString( PKG, "BasePMIStep.Warning.SeparateTestSetStratificationValueNotSeenDuringTraining",
                    stratS ) );
          } else {
            Instances header = m_trainingHeaders.get( stratS );
            if ( !m_schemeIsMoreEfficientBatchPredictor ) {
              // can test incrementally
              Instance toTest = constructInstance( header, m_testingRowMeta, row, m_testingFieldIndexes, stepMeta );
              evaluator.performEvaluationIncremental( toTest, log );
            } else {
              boolean testBatch = false;
              boolean add = false;
              boolean outputStratifiedEvalRow = false;
              // we assume test data is sorted in order of stratification field
              if ( m_currentStratificationValue.equals( stratS ) ) {
                m_separateTestSetBatchPredictorRows.add( row );
                add = false;

                // check the # rows against batch prediction batch size and set testBatch...
                testBatch = m_separateTestSetBatchPredictorRows.size() == m_batchPredictorPreferredBatchSize;
              } else {
                if ( !Const.isEmpty( m_currentStratificationValue ) ) {
                  // stratification value has changed
                  if ( m_stratificationCheck.contains( stratS ) ) {
                    throw new KettleException(
                        BaseMessages.getString( PKG, "BasePMIStep.Error.PreviouslySeenStratificationValue", stratS ) );
                  }
                  testBatch = true;
                  outputStratifiedEvalRow = true;
                }
                add = true;
              }

              if ( testBatch ) {
                processSeparateTestBatch( m_separateTestSetBatchPredictorRows, evaluator, header, stepMeta,
                    "stratified separate test set", log, vars );
                if ( evaluator.wasEvaluationPerformed() ) {
                  evaluationOutputRows.add( evaluator.getEvalRow( m_currentStratificationValue, m_outputRowMeta,
                      m_rowHandlingMode == Batch ? m_batchCount : -1 ) );
                }

                // ready for next batch of test rows
                m_separateTestSetBatchPredictorRows.clear();
              }
              if ( add ) {
                m_separateTestSetBatchPredictorRows.add( row );
              }
              m_currentStratificationValue = stratS;
            }
          }
        }
      } else {
        // last partial stratified batch (OR we need to iterate over evaluators and output eval rows if
        // our scheme has been tested incrementally

        boolean streamingPrediction = false;
        for ( Map.Entry<String, Evaluator> e : m_evaluation.entrySet() ) {
          Classifier template = e.getValue().getClassifierTemplate();
          if ( !m_schemeIsMoreEfficientBatchPredictor ) {
            streamingPrediction = true;
            // get an evaluation output row
            if ( e.getValue().wasEvaluationPerformed() ) {
              evaluationOutputRows.add( e.getValue()
                  .getEvalRow( e.getKey(), m_outputRowMeta, m_rowHandlingMode == Batch ? m_batchCount : -1 ) );
            }
          }
        }

        if ( !streamingPrediction ) {
          // check for any remaining rows for the current stratification value
          if ( m_separateTestSetBatchPredictorRows.size() > 0 ) {
            Evaluator evaluator = m_evaluation.get( m_currentStratificationValue );
            Instances header = m_trainingHeaders.get( m_currentStratificationValue );
            processSeparateTestBatch( m_separateTestSetBatchPredictorRows, evaluator, header, stepMeta,
                "stratified test set", log, vars );
            if ( evaluator.wasEvaluationPerformed() ) {
              evaluationOutputRows.add( evaluator.getEvalRow( m_currentStratificationValue, m_outputRowMeta,
                  m_rowHandlingMode == Batch ? m_batchCount : -1 ) );
            }
          }
        }
      }
    } catch ( Exception ex ) {
      throw new KettleException( ex );
    }

    return evaluationOutputRows;
  }

  protected void processSeparateTestBatch( List<Object[]> data, Evaluator evaluator, Instances trainingHeader,
      BaseSupervisedPMIStepMeta stepMeta, String relationName, LogChannelInterface log, VariableSpace vars )
      throws KettleException {

    if ( data.size() > 0 ) {
      Instances testData = buildDataset( trainingHeader, m_testingRowMeta, data, m_testingFieldIndexes, stepMeta );
      try {
        evaluator.performEvaluation( testData, log );
      } catch ( Exception e ) {
        throw new KettleException( e );
      }
    }
  }

  protected void saveModel( Classifier model, Instances header, BaseSupervisedPMIStepMeta stepMeta,
      LogChannelInterface log ) throws KettleException {
    if ( Const.isEmpty( m_modelOutputPath ) ) {
      return;
    }

    if ( m_modelOutputPath.toLowerCase().startsWith( "file:" ) ) {
      try {
        m_modelOutputPath = m_modelOutputPath.replace( " ", "%20" );
        File updatedPath = new File( new java.net.URI( m_modelOutputPath ) );
        m_modelOutputPath = updatedPath.toString();
      } catch ( Exception ex ) {
        throw new KettleException(
            BaseMessages.getString( PKG, "BasePMIStep.Error.MalformedURIForModelPath", m_modelOutputPath ) );
      }
    }

    String fileName = Const.isEmpty( m_modelFileName ) ? "model" : m_modelFileName;
    if ( m_rowHandlingMode == Stratified ) {
      fileName = m_currentStratificationValue + "_" + fileName;
    } else if ( m_rowHandlingMode == Batch ) {
      fileName = "" + m_batchCount + "_" + fileName;
      m_batchCount++;
    }

    File directory = new File( m_modelOutputPath );
    if ( directory.exists() && directory.isFile() ) {
      throw new KettleException(
          BaseMessages.getString( PKG, "BasePMIStep.Error.ModelOutputDirectoryIsNotADirectory", m_modelOutputPath ) );
    }
    if ( !directory.exists() ) {
      if ( !directory.mkdirs() ) {
        throw new KettleException( BaseMessages
            .getString( PKG, "BasePMIStep.Error.WasUnableToCreateOutputDirectoryForModels", m_modelOutputPath ) );
      }
    }
    try {
      log.logBasic( BaseMessages.getString( PKG, "BasePMIStep.Info.SavingModel", model.getClass().getCanonicalName(),
          m_modelOutputPath + File.separator + fileName ) );
      SerializationHelper.writeAll( m_modelOutputPath + File.separator + fileName, new Object[] { model, header } );
    } catch ( Exception e ) {
      throw new KettleException( e );
    }
  }

  protected Instances buildDataset( Instances header, RowMetaInterface inputRowMeta, List<Object[]> data,
      Map<String, Integer> streamFieldLookup, BaseSupervisedPMIStepMeta stepMeta ) throws KettleException {

    Instances dataset = new Instances( header, data.size() );
    for ( Object[] row : data ) {
      Instance toAdd = constructInstance( dataset, inputRowMeta, row, streamFieldLookup, stepMeta );
      dataset.add( toAdd );
    }

    return dataset;
  }

  protected Instance constructInstance( Instances header, RowMetaInterface inputRowMeta, Object[] row,
      Map<String, Integer> streamFieldLookup, BaseSupervisedPMIStepMeta stepMeta ) throws KettleValueException {

    double[] vals = new double[header.numAttributes()];
    for ( int i = 0; i < header.numAttributes(); i++ ) {
      String fieldName = header.attribute( i ).name();
      if ( streamFieldLookup.containsKey( fieldName ) ) {
        int streamIndex = streamFieldLookup.get( fieldName );
        ValueMetaInterface fieldMeta = inputRowMeta.getValueMeta( streamIndex );
        if ( fieldMeta.isNull( row[streamIndex] ) ) {
          vals[i] = Utils.missingValue();
        } else {
          if ( header.attribute( i ).isNumeric() ) {
            vals[i] = fieldMeta.getNumber( row[streamIndex] );
          } else if ( header.attribute( i ).isString() ) {
            vals[i] = header.attribute( i ).addStringValue( fieldMeta.getString( row[streamIndex] ) );
          } else if ( header.attribute( i ).isNominal() ) {
            String nomVal = fieldMeta.getString( row[streamIndex] );
            int nomIndex = header.attribute( i ).indexOfValue( nomVal );
            vals[i] = nomIndex < 0 ? Utils.missingValue() : nomIndex;
          }
        }
      } else {
        vals[i] = Utils.missingValue();
      }
    }

    Instance result = new DenseInstance( 1.0, vals );
    result.setDataset( header );
    return result;
  }

  /**
   * Check whether a batch of rows will be needed in order to determine header metadata. Used when
   * training schemes that support incremental updates.
   *
   * @param stepMeta the step metadata
   * @return true if the header can be completely determined on the basis of field metadata.
   */
  protected boolean headerCanBeDeterminedImmediately( BaseSupervisedPMIStepMeta stepMeta ) {

    List<ArffMeta> arffFields = stepMeta.getFieldMetadata();
    boolean result = true;

    for ( int i = 0; i < arffFields.size(); i++ ) {
      ArffMeta current = arffFields.get( i );
      if ( !current.getFieldName().equals( m_stratificationFieldName ) ) {
        if ( current.getArffType() == ArffMeta.NOMINAL ) {
          if ( Const.isEmpty( current.getNominalVals() ) ) {
            result = false;
            break;
          }
        }
      }
    }

    return result;
  }

  protected Instances determineHeader( List<Object[]> trainingRows, String relationName,
      BaseSupervisedPMIStepMeta stepMeta ) throws KettleException {
    ArrayList<Attribute> atts = new ArrayList<>();

    List<ArffMeta> arffFields = stepMeta.getFieldMetadata();
    for ( int i = 0; i < arffFields.size(); i++ ) {
      ArffMeta current = arffFields.get( i );
      if ( current != m_classArffMeta && !current.getFieldName().equals( m_stratificationFieldName ) ) {
        atts.add( constructAttribute( current, trainingRows ) );
      }
    }

    // class as the last attribute
    atts.add( constructAttribute( m_classArffMeta, trainingRows ) );

    Instances result = new Instances( relationName, atts, 0 );
    result.setClassIndex( result.numAttributes() - 1 );

    return result;
  }

  protected Attribute constructAttribute( ArffMeta current, List<Object[]> trainingRows ) throws KettleException {

    Attribute result = null;
    if ( current.getArffType() == ArffMeta.DATE || current.getArffType() == ArffMeta.NUMERIC ) {
      result = new Attribute( current.getFieldName() );
    } else if ( current.getArffType() == ArffMeta.STRING ) {
      result = new Attribute( current.getFieldName(), (List<String>) null );
    } else if ( current.getArffType() == ArffMeta.NOMINAL ) {
      String legalVals = current.getNominalVals();
      if ( !Const.isEmpty( legalVals ) ) {
        TreeSet<String> ts = new TreeSet<>( ArffMeta.stringToVals( legalVals ) );
        ArrayList<String> sortedVals = new ArrayList<>( ts );
        result = new Attribute( current.getFieldName(), sortedVals );
      } else {
        // check to see if the incoming field has indexed values
        ValueMetaInterface
            inField =
            m_trainingRowMeta.getValueMeta( m_trainingFieldIndexes.get( current.getFieldName() ) );
        if ( inField.getStorageType() == ValueMetaInterface.STORAGE_TYPE_INDEXED ) {
          TreeSet<String> ts = new TreeSet<>();
          for ( Object o : inField.getIndex() ) {
            ts.add( o.toString() );
          }
          ArrayList<String> sortedVals = new ArrayList<>( ts );
          result = new Attribute( current.getFieldName(), sortedVals );
        } else {
          // we have to iterate over the actual data and collect values
          result =
              new Attribute( current.getFieldName(), getNominalValsFromData( trainingRows, m_trainingRowMeta,
                  m_trainingFieldIndexes.get( current.getFieldName() ) ) );
        }
      }
    } else {
      throw new KettleException( BaseMessages.getString( PKG, "BasePMIStepData.Error.UnsupportedAttributeType",
          BaseSupervisedPMIStepData.typeToString( current.getArffType() ) ) );
    }

    return result;
  }

  protected ArrayList<String> getNominalValsFromData( List<Object[]> data, RowMetaInterface rowMetaInterface,
      int fieldIndex ) throws KettleValueException {
    TreeSet<String> sortedVals = new TreeSet<>();

    ValueMetaInterface vm = rowMetaInterface.getValueMeta( fieldIndex );
    for ( Object[] row : data ) {
      if ( !vm.isNull( row[fieldIndex] ) ) {
        sortedVals.add( vm.getString( row[fieldIndex] ) );
      }
    }

    return new ArrayList<String>( sortedVals );
  }

  public static List<ArffMeta> fieldsToArffMetas( RowMetaInterface rmi ) {
    List<ArffMeta> arffMetas = new ArrayList<>();
    if ( rmi != null ) {
      for ( ValueMetaInterface inField : rmi.getValueMetaList() ) {
        int fieldType = inField.getType();
        ArffMeta newArffMeta = null;
        switch ( fieldType ) {
          case ValueMetaInterface.TYPE_NUMBER:
          case ValueMetaInterface.TYPE_INTEGER:
          case ValueMetaInterface.TYPE_BOOLEAN:
            arffMetas.add( new ArffMeta( inField.getName(), fieldType, ArffMeta.NUMERIC ) );
            break;
          case ValueMetaInterface.TYPE_STRING:
            newArffMeta = new ArffMeta( inField.getName(), fieldType, ArffMeta.NOMINAL );

            // check for indexed values
            if ( inField.getStorageType() == ValueMetaInterface.STORAGE_TYPE_INDEXED ) {
              Object[] legalVals = inField.getIndex();
              StringBuilder temp = new StringBuilder();
              boolean first = true;
              for ( Object l : legalVals ) {
                if ( first ) {
                  temp.append( l.toString().trim() );
                  first = false;
                } else {
                  temp.append( "," ).append( l.toString().trim() ); //$NON-NLS-1$
                }
              }
              newArffMeta.setNominalVals( temp.toString() );
            }
            arffMetas.add( newArffMeta );
            break;
          case ValueMetaInterface.TYPE_DATE:
            newArffMeta = new ArffMeta( inField.getName(), fieldType, ArffMeta.DATE );
            newArffMeta.setDateFormat( inField.getDateFormat().toPattern() );
            arffMetas.add( newArffMeta );
            break;
        }
      }
    }
    return arffMetas;
  }

  protected static void establishOutputRowMeta( RowMetaInterface outRowMeta, VariableSpace vars,
      BaseSupervisedPMIStepMeta stepMeta ) throws KettlePluginException {
    outRowMeta.clear();
    if ( stepMeta.getEvalMode() == Evaluator.EvalMode.NONE ) {
      // TODO add an option to just pass input rows through?

      if ( stepMeta.getRowsToProcess().equals( Stratified.toString() ) ) {
        outRowMeta.addValueMeta( ValueMetaFactory
            .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.StratificationFieldName" ),
                ValueMetaInterface.TYPE_STRING ) );
      }

      outRowMeta.addValueMeta( ValueMetaFactory
          .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.ModelTextOutputFieldName" ),
              ValueMetaInterface.TYPE_STRING ) );
    } else {
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

      if ( stepMeta.getRowsToProcess().equals( Stratified.toString() ) ) {
        vm =
            ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.StratificationFieldName" ),
                ValueMetaInterface.TYPE_STRING );
        outRowMeta.addValueMeta( vm );
      }

      if ( stepMeta.getFieldMetadata().size() == 0 ) {
        return;
      }
      // basic evaluation fields
      ArffMeta
          classArffMeta =
          Const.isEmpty( stepMeta.getClassField() ) ?
              stepMeta.getFieldMetadata().get( stepMeta.getFieldMetadata().size() - 1 ) : null;
      if ( classArffMeta == null ) {
        String classFieldName = vars.environmentSubstitute( stepMeta.getClassField() );
        for ( ArffMeta m : stepMeta.getFieldMetadata() ) {
          if ( m.getFieldName().equals( classFieldName ) ) {
            classArffMeta = m;
            break;
          }
        }
      }
      if ( classArffMeta == null ) {
        return;
      }
      vm =
          ValueMetaFactory
              .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.UnclassifiedInstancesFieldName" ),
                  ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );

      if ( classArffMeta.getArffType() == ArffMeta.NOMINAL ) {
        vm =
            ValueMetaFactory
                .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.CorrectInstancesFieldName" ),
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
            ValueMetaFactory
                .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.PercentIncorrectFieldName" ),
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

      if ( classArffMeta.getArffType() == ArffMeta.NUMERIC ) {
        vm =
            ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.CorrCoeffFieldName" ),
                ValueMetaInterface.TYPE_NUMBER );
        outRowMeta.addValueMeta( vm );
      }

      if ( stepMeta.getEvalMode() != Evaluator.EvalMode.PREQUENTIAL ) {
        vm =
            ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.RAEFieldName" ),
                ValueMetaInterface.TYPE_NUMBER );
        outRowMeta.addValueMeta( vm );
        vm =
            ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.RRSEFieldName" ),
                ValueMetaInterface.TYPE_NUMBER );
        outRowMeta.addValueMeta( vm );
      }
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.TotalNumInstancesFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );

      if ( classArffMeta.getArffType() == ArffMeta.NOMINAL ) {
        vm =
            ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.KappaStatisticFieldName" ),
                ValueMetaInterface.TYPE_NUMBER );
        outRowMeta.addValueMeta( vm );
      }

      // Per-class IR statistics
      if ( stepMeta.getOutputIRMetrics() && classArffMeta.getArffType() == ArffMeta.NOMINAL ) {
        String classLabels = classArffMeta.getNominalVals();
        if ( !Const.isEmpty( classLabels ) ) {
          TreeSet<String> ts = new TreeSet<>( ArffMeta.stringToVals( classLabels ) );
          //String[] labels = classLabels.split( "," );
          for ( String label : ts ) {
            label = label.trim();
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
      }

      if ( stepMeta.getOutputAUCMetrics() && classArffMeta.getArffType() == ArffMeta.NOMINAL ) {
        String classLabels = classArffMeta.getNominalVals();
        if ( !Const.isEmpty( classLabels ) ) {
          TreeSet<String> ts = new TreeSet<>( ArffMeta.stringToVals( classLabels ) );
          // String[] labels = classLabels.split( "," );
          for ( String label : ts ) {
            label = label.trim();

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

      // TODO - handle plugin evaluation metrics!!
    }
  }

  /**
   * Static utility method to convert Arff type to string representation
   *
   * @param type the integer type code
   * @return a string representation
   */
  public static String typeToString( int type ) {
    String result = "Unknown";
    switch ( type ) {
      case 0:
        result = "Numeric";
        break;
      case 1:
        result = "Nominal";
        break;
      case 2:
        result = "Date";
        break;
      case 3:
        result = "String";
        break;
    }

    return result;
  }
}
