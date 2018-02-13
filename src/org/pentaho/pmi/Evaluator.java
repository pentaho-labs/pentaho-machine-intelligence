/*******************************************************************************
 * Pentaho Data Science
 * <p/>
 * Copyright (c) 2002-2017 Hitachi Vantara. All rights reserved.
 * <p/>
 * ******************************************************************************
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.pentaho.pmi;

import org.pentaho.di.core.Const;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.steps.pmi.BaseSupervisedPMIStepMeta;
import weka.classifiers.Classifier;
import weka.classifiers.evaluation.Evaluation;
import weka.core.BatchPredictor;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.Utils;

import java.util.Random;

/**
 * Class that handles evaluating a PMI model.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class Evaluator {

  /**
   * The evaluation mode being used
   */
  protected EvalMode m_evaluationMode = EvalMode.NONE;

  /**
   * Default of 10
   */
  protected int m_xValFolds = 10;

  /**
   * Default 2/3 1/3
   */
  protected int m_percentageSplit = 66;

  protected int m_randomSeed = 1;

  /**
   * True to compute area under the curve metrics (requires predictions to be retained in memory)
   */
  protected boolean m_computeAUC;

  /**
   * True if IR metrics are to be recorded in the output row (if class is nominal)
   */
  protected boolean m_outputIRMetrics;

  /**
   * Preserve order (rather than randomly shuffling) for % split and x-val
   */
  protected boolean m_preserveOrder;

  /**
   * The actual evaluation object
   */
  protected Evaluation m_eval;

  /**
   * Template for instantiating a new classifier
   */
  protected Classifier m_templateClassifier;

  /**
   * Holds the classifier built on all the training data
   */
  protected Classifier m_classifier;

  /**
   * Holds the training data
   */
  protected Instances m_trainingData;

  /**
   * True if the last evaluation was successfully performed. Evaluation will not be performed if there are fewer
   * training instances that x-val folds, or fewer than 10 instances for a percentage split.
   */
  protected boolean m_evalWasPerformed;

  /**
   * Construct a new Evaluator.
   *
   * @param mode            the evaluation mode to use
   * @param randomSeed      a random seed for shuffling/stratification
   * @param computeAUC      true if area under the curve metrics are to be computed
   * @param outputIRMetrics true if information retrieval metrics are to be computed
   */
  public Evaluator( EvalMode mode, int randomSeed, boolean computeAUC, boolean outputIRMetrics ) {
    m_evaluationMode = mode;
    m_randomSeed = randomSeed;
    m_computeAUC = computeAUC;
    m_outputIRMetrics = outputIRMetrics;
  }

  /**
   * Set the percentage for training data in a percentage split evaluation
   *
   * @param percentageSplit the percentage for the training data
   */
  public void setPercentageSplit( int percentageSplit ) {
    m_percentageSplit = percentageSplit;
  }

  /**
   * Get the percentage for training data in a percentage split evaluation
   *
   * @return the percentage for the training data
   */
  public int getPercentageSplit() {
    return m_percentageSplit;
  }

  /**
   * Set the number of folds to use for a cross-validation evaluation
   *
   * @param folds the number of folds to use
   */
  public void setXValFolds( int folds ) {
    m_xValFolds = folds;
  }

  /**
   * Get the number of folds to use for a cross-validation evaluation
   *
   * @return the number of folds to use
   */
  public int getXValFolds() {
    return m_xValFolds;
  }

  /**
   * Set the random seed to use
   *
   * @param randomSeed the random seed value to use
   */
  public void setRandomSeed( int randomSeed ) {
    m_randomSeed = randomSeed;
  }

  /**
   * Get the random seed to use
   *
   * @return the random seed value to use
   */
  public int getRandomSeed() {
    return m_randomSeed;
  }

  /**
   * Initialize the evaluator
   *
   * @param trainingData        the training data
   * @param untrainedClassifier the untrained classifier to use
   * @throws Exception if a problem occurs
   */
  public void initialize( Instances trainingData, Classifier untrainedClassifier ) throws Exception {
    m_eval = new Evaluation( trainingData );
    m_trainingData = trainingData;
    m_templateClassifier = untrainedClassifier;
  }

  /**
   * Initialize the evaluator. No prior class probabilities are/can be computed because all that is available at
   * initialization time is the training metadata (i.e. attribute information).
   *
   * @param trainingHeader the training metadata (i.e. an instances object containing only attribute information and no actual instances)
   * @param trainedModel   the trained model to evaluate
   * @throws Exception if a problem occurs
   */
  public void initializeNoPriors( Instances trainingHeader, Classifier trainedModel ) throws Exception {
    m_eval = new Evaluation( trainingHeader );
    m_eval.useNoPriors();
    m_classifier = trainedModel;
    m_templateClassifier = trainedModel;
    m_templateClassifier = copyClassifierTemplate(); // untrained template
    m_trainingData = trainingHeader;
  }

  /**
   * Get the classifier template object
   *
   * @return the classifier template object
   */
  public Classifier getClassifierTemplate() {
    return m_templateClassifier;
  }

  /**
   * Set an already trained classifier to evaluate.
   *
   * @param trainedClassifier the trained classifier to evaluate
   */
  public void setTrainedClassifier( Classifier trainedClassifier ) {
    m_classifier = trainedClassifier;
  }

  /**
   * Returns true if the last evaluation was performed. Evaluation will not be performed if there are fewer than
   * 10 instances for a percentage split or fewer instances than folds for a cross-validation.
   *
   * @return true if the last evaluation was performed.
   */
  public boolean wasEvaluationPerformed() {
    return m_evalWasPerformed;
  }

  /**
   * Perform an evaluation.
   *
   * @param separateTestData optional separate test data (used in separate test set evaluation)
   * @param log              the logging object to use
   * @throws Exception if a problem occurs
   */
  public void performEvaluation( Instances separateTestData, LogChannelInterface log ) throws Exception {
    if ( m_trainingData == null ) {
      throw new IllegalStateException(
          BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Error.EvaluatorNotInitialized" ) );
    }

    m_evalWasPerformed = true;
    Random r = new Random( m_randomSeed );
    // shuffle the training data
    if ( !m_preserveOrder ) {
      m_trainingData.randomize( r );
    }
    if ( m_evaluationMode == EvalMode.PERCENTAGE_SPLIT ) {
      if ( m_trainingData.numInstances() < 10 ) {
        log.logBasic(
            BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Message.UnableToPerformPercentageSplit" ) );
        m_evalWasPerformed = false;
        return;
      }
      log.logBasic( BaseMessages
          .getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Message.PerformingPercentageSplit", m_percentageSplit ) );
      int trainSize = (int) Math.round( m_trainingData.numInstances() * m_percentageSplit / 100 );
      int testSize = m_trainingData.numInstances() - trainSize;

      Instances train = new Instances( m_trainingData, 0, trainSize );
      Instances test = new Instances( m_trainingData, trainSize, testSize );
      Classifier classifierCopy = copyClassifierTemplate();
      classifierCopy.buildClassifier( train );
      if ( m_computeAUC || ( m_templateClassifier instanceof BatchPredictor && ( (BatchPredictor) m_templateClassifier )
          .implementsMoreEfficientBatchPrediction() ) ) {
        m_eval.evaluateModel( classifierCopy, test );
      } else {
        for ( int i = 0; i < test.numInstances(); i++ ) {
          m_eval.evaluateModelOnce( classifierCopy, test.instance( i ) );
        }
      }
    } else if ( m_evaluationMode == EvalMode.CROSS_VALIDATION ) {
      if ( m_trainingData.numInstances() < m_xValFolds ) {
        log.logBasic( BaseMessages
            .getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Message.UnableToPerformCrossValidation", m_xValFolds,
                m_trainingData.numInstances() ) );
        m_evalWasPerformed = false;
        return;
      }
      log.logBasic(
          BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Message.PerformingCrossValidation", m_xValFolds ) );
      if ( !m_preserveOrder && m_trainingData.classAttribute().isNominal() ) {
        m_trainingData.stratify( m_xValFolds );
      }
      for ( int i = 0; i < m_xValFolds; i++ ) {
        log.logDetailed(
            BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Message.TrainingModelForFold", ( i + 1 ) ) );
        Classifier foldClassifier = copyClassifierTemplate();
        Instances train = m_trainingData.trainCV( 10, i, r );
        m_eval.setPriors( train );
        foldClassifier.buildClassifier( train );
        Instances test = m_trainingData.testCV( 10, i );
        log.logDetailed(
            BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Message.TestingModelForFold", ( i + 1 ) ) );

        if ( m_templateClassifier instanceof BatchPredictor && ( (BatchPredictor) m_templateClassifier )
            .implementsMoreEfficientBatchPrediction() ) {
          Instances testCopy = new Instances( test );
          for ( int j = 0; j < testCopy.numInstances(); j++ ) {
            testCopy.instance( j ).setClassMissing();
          }
          double[][] preds = ( (BatchPredictor) foldClassifier ).distributionsForInstances( testCopy );
          for ( int j = 0; j < test.numInstances(); j++ ) {
            if ( m_computeAUC ) {
              m_eval.evaluateModelOnceAndRecordPrediction( preds[j], test.instance( j ) );
            } else {
              m_eval.evaluateModelOnce( preds[j], test.instance( j ) );
            }
          }
        } else {
          for ( int j = 0; j < test.numInstances(); j++ ) {
            if ( m_computeAUC ) {
              m_eval.evaluateModelOnceAndRecordPrediction( foldClassifier, test.instance( j ) );
            } else {
              m_eval.evaluateModelOnce( foldClassifier, test.instance( j ) );
            }
          }
        }
      }
    } else if ( m_evaluationMode == EvalMode.SEPARATE_TEST_SET && separateTestData != null ) {
      if ( separateTestData.numInstances() == 0 ) {
        log.logBasic(
            BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Message.UnableToPerformSeparateTestSetEval" ) );
        m_evalWasPerformed = false;
        return;
      }
      if ( m_classifier == null ) {
        throw new IllegalStateException(
            BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Error.FinalClassifierHasNotBeenTrainedYet" ) );
      }
      // log.logBasic( "Performing separate test set evaluation..." );
      if ( m_computeAUC || ( m_templateClassifier instanceof BatchPredictor && ( (BatchPredictor) m_templateClassifier )
          .implementsMoreEfficientBatchPrediction() ) ) {
        m_eval.evaluateModel( m_classifier, separateTestData );
      } else {
        for ( int i = 0; i < separateTestData.numInstances(); i++ ) {
          m_eval.evaluateModelOnce( m_classifier, separateTestData.instance( i ) );
        }
      }
    }
  }

  /**
   * Performs incremental evaluation. Only applicable to separate test set mode and non-BatchPredictors
   *
   * @param testInstance the test instance to process
   * @param log          the logging object
   * @throws Exception if a problem occurs
   */
  public void performEvaluationIncremental( Instance testInstance, LogChannelInterface log ) throws Exception {
    if ( m_evaluationMode != EvalMode.SEPARATE_TEST_SET && m_evaluationMode != EvalMode.PREQUENTIAL ) {
      throw new IllegalStateException( "Incremental evaluation can only be performed on a separate test set or "
          + "on the training data for incremental schemes (prequential evaluation)." );
    }

    if ( m_templateClassifier instanceof BatchPredictor && ( (BatchPredictor) m_templateClassifier )
        .implementsMoreEfficientBatchPrediction() ) {
      throw new Exception(
          BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Error.IncrementalEvalOnlyOnTestOrTrainingData" ) );
    }

    if ( m_computeAUC ) {
      m_eval.evaluateModelOnceAndRecordPrediction( m_classifier, testInstance );
    } else {
      m_eval.evaluateModelOnce( m_classifier, testInstance );
    }
  }

  /**
   * Build a final model using all of the available training data.
   *
   * @return a final model
   * @throws Exception if a problem occurs
   */
  public Classifier buildFinalModel() throws Exception {

    if ( m_trainingData == null ) {
      throw new IllegalStateException(
          BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Error.EvaluatorNotInitialized" ) );
    }

    m_classifier = copyClassifierTemplate();
    m_classifier.buildClassifier( m_trainingData );

    return m_classifier;
  }

  /**
   * Return a row containing evaluation metrics
   *
   * @param stratificationValue optional stratification value
   * @param outputRowMeta       the metadata of the output row structure
   * @param batchNumber         the current batch number (if applicable)
   * @return a row of evaluation metrics
   */
  public Object[] getEvalRow( String stratificationValue, RowMetaInterface outputRowMeta, int batchNumber ) {
    if ( m_trainingData == null ) {
      throw new IllegalStateException(
          BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Error.EvaluatorNotInitialized" ) );
    }
    if ( m_evaluationMode == EvalMode.NONE || m_eval.numInstances() == 0 ) {
      return null;
    } else {
      Object[] outputRow = RowDataUtil.allocateRowData( outputRowMeta.size() );
      int i = 0;
      String schemeName = m_templateClassifier.getClass().getCanonicalName();
      schemeName = schemeName.substring( schemeName.lastIndexOf( "." ) + 1 );
      if ( batchNumber > 0 ) {
        schemeName = "" + batchNumber + "_" + schemeName;
      }
      outputRow[i++] = schemeName;
      String schemeOptions = Utils.joinOptions( ( (OptionHandler) m_templateClassifier ).getOptions() );
      outputRow[i++] = schemeOptions;

      String evalMode = m_evaluationMode.toString().toLowerCase();
      if ( m_evaluationMode == EvalMode.PERCENTAGE_SPLIT ) {
        evalMode += " " + m_percentageSplit + "% seed " + m_randomSeed;
      } else if ( m_evaluationMode == EvalMode.CROSS_VALIDATION ) {
        evalMode += " folds " + m_xValFolds + " seed " + m_randomSeed;
      }
      outputRow[i++] = evalMode;

      if ( !Const.isEmpty( stratificationValue ) ) {
        outputRow[i++] = stratificationValue;
      }

      outputRow[i++] = m_eval.unclassified();

      if ( m_trainingData.classAttribute().isNominal() ) {
        outputRow[i++] = m_eval.correct();
        outputRow[i++] = m_eval.incorrect();
        outputRow[i++] = m_eval.pctCorrect();
        outputRow[i++] = m_eval.pctIncorrect();
      }
      outputRow[i++] = m_eval.meanAbsoluteError();
      outputRow[i++] = m_eval.rootMeanSquaredError();

      if ( m_trainingData.classAttribute().isNumeric() ) {
        try {
          outputRow[i++] = m_eval.correlationCoefficient();
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      }

      if ( m_evaluationMode != EvalMode.PREQUENTIAL ) {
        try {
          outputRow[i++] = m_eval.relativeAbsoluteError();
        } catch ( Exception e ) {
          e.printStackTrace();
        }
        outputRow[i++] = m_eval.rootRelativeSquaredError();
      }

      outputRow[i++] = m_eval.numInstances();

      if ( m_trainingData.classAttribute().isNominal() ) {
        outputRow[i++] = m_eval.kappa();

        if ( m_outputIRMetrics ) {
          for ( int j = 0; j < m_trainingData.classAttribute().numValues(); j++ ) {
            outputRow[i++] = m_eval.truePositiveRate( j );
            outputRow[i++] = m_eval.falsePositiveRate( j );
            outputRow[i++] = m_eval.precision( j );
            outputRow[i++] = m_eval.recall( j );
            outputRow[i++] = m_eval.fMeasure( j );
            outputRow[i++] = m_eval.matthewsCorrelationCoefficient( j );
          }
        }

        if ( m_computeAUC ) {
          for ( int j = 0; j < m_trainingData.classAttribute().numValues(); j++ ) {
            outputRow[i++] = m_eval.areaUnderROC( j );
            outputRow[i++] = m_eval.areaUnderPRC( j );
          }
        }
      }
      return outputRow;
    }
  }

  /**
   * Get the underlying Weka Evaluation object
   *
   * @return the underlying Weka evaluation object
   */
  public Evaluation getEvaluation() {
    if ( m_trainingData == null ) {
      throw new IllegalStateException(
          BaseMessages.getString( BaseSupervisedPMIStepMeta.PKG, "Evaluator.Error.EvaluatorNotInitialized" ) );
    }

    return m_eval;
  }

  /**
   * Create a copy of the classifier template
   *
   * @return a copy of the classifier template
   * @throws Exception if a problem occurs
   */
  protected Classifier copyClassifierTemplate() throws Exception {
    return (Classifier) Utils.forName( Classifier.class, m_templateClassifier.getClass().getCanonicalName(),
        ( (OptionHandler) m_templateClassifier ).getOptions() );
  }

  /**
   * Enum for evaluation modes
   */
  public enum EvalMode {
    NONE, PERCENTAGE_SPLIT, CROSS_VALIDATION, SEPARATE_TEST_SET, PREQUENTIAL;
  }
}
