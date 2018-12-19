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

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.core.util.Utils;
import org.pentaho.di.i18n.BaseMessages;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility routines for establishing output row format and computing evaluation metrics for the SupervisedEvaluator
 * step.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class GeneralSupervisedEvaluatorUtil {

  protected static Class<?> PKG = GeneralSupervisedEvaluatorUtil.class;

  public static final String PREDICTED_FIELD_PREFIX = "predicted_";

  protected boolean m_classIsNumeric;
  protected int m_indexOfPredictedNumericClass = -1;
  protected List<Integer> m_predictedLabelIndexes;
  protected Evaluation m_eval;
  protected Instances m_header;
  protected Attribute m_classAtt;
  protected int m_indexOfClass = -1;
  protected double[] m_preds;

  protected Instance m_dummyInstance;
  protected double[] m_dummyInstanceBackingArray;

  public GeneralSupervisedEvaluatorUtil() {
  }

  public GeneralSupervisedEvaluatorUtil( RowMetaInterface rowMetaInterface, String className, String nominalVals )
      throws KettleException {
    if ( rowMetaInterface.size() == 0 ) {
      throw new KettleException( "No incoming row structure!" );
    }

    if ( Utils.isEmpty( className ) ) {
      throw new KettleException( "No class column specified" );
    }

    Attribute classAtt = createClassAttribute( className, nominalVals );
    ArrayList<Attribute> atts = new ArrayList<>( Arrays.asList( classAtt ) );
    Instances header = new Instances( "dummyHeader", atts, 0 );
    header.setClassIndex( 0 );
    try {
      Evaluation eval = new Evaluation( header );
      // TODO for now - no priors
      eval.useNoPriors();

      init( rowMetaInterface, eval );
    } catch ( Exception ex ) {
      throw new KettleException( ex );
    }
  }

  public GeneralSupervisedEvaluatorUtil( RowMetaInterface rowMetaInterface, String className ) throws KettleException {
    if ( rowMetaInterface.size() == 0 ) {
      throw new KettleException( "No incoming row structure!" );
    }

    if ( Utils.isEmpty( className ) ) {
      throw new KettleException( "No class column specified" );
    }

    Attribute classAtt = createClassAttribute( rowMetaInterface, className );
    ArrayList<Attribute> atts = new ArrayList<>( Arrays.asList( classAtt ) );
    Instances header = new Instances( "dummyHeader", atts, 0 );
    header.setClassIndex( 0 );
    try {
      Evaluation eval = new Evaluation( header );
      // TODO for now - no priors
      eval.useNoPriors();

      init( rowMetaInterface, eval );
    } catch ( Exception ex ) {
      throw new KettleException( ex );
    }
  }

  public GeneralSupervisedEvaluatorUtil( RowMetaInterface rowMetaInterface, Evaluation eval ) throws KettleException {
    init( rowMetaInterface, eval );
  }

  protected static Attribute createClassAttribute( String className, String nominalVals ) {
    Attribute classA = null;
    if ( !Utils.isEmpty( nominalVals ) ) {
      String[] labels = nominalVals.split( "," );
      for ( int i = 0; i < labels.length; i++ ) {
        labels[i] = labels[i].trim();
      }
      classA = new Attribute( className, Arrays.asList( labels ) );
    } else {
      // assume numeric class
      classA = new Attribute( className );
    }
    return classA;
  }

  protected static Attribute createClassAttribute( RowMetaInterface rowMeta, String className ) throws KettleException {
    Attribute classA = null;
    ValueMetaInterface classMeta = null;

    // Search for actual class first in incoming fields
    for ( ValueMetaInterface vm : rowMeta.getValueMetaList() ) {
      if ( vm.getName().equals( className ) ) {
        classMeta = vm;
        break;
      }
    }

    if ( classMeta == null || classMeta.isNumber() ) {
      // just return a numeric Attribute in this case, as init() will complain later if null...
      classA = new Attribute( className );
    } else {
      if ( classMeta.isString() ) {
        // Nominal class
        ArrayList<String> labels = new ArrayList<>();
        // scan meta for predicted label fields
        for ( ValueMetaInterface vm : rowMeta.getValueMetaList() ) {
          if ( vm.getName().startsWith( PREDICTED_FIELD_PREFIX + className ) ) {
            String label = vm.getName().replace( PREDICTED_FIELD_PREFIX + className + "_", "" );
            if ( label.length() > 0 ) {
              labels.add( label );
            }
          }
        }
        if ( labels.size() == 0 ) {
          throw new KettleException( "No predicted label fields in incoming row data for class '" + className + "'" );
        }
        classA = new Attribute( className, labels );
      }
    }

    return classA;
  }

  public void init( RowMetaInterface rowMeta, Evaluation eval ) throws KettleException {
    m_eval = eval;
    m_header = eval.getHeader();
    m_classAtt = m_header.classAttribute();
    if ( m_classAtt == null ) {
      throw new KettleException( "Class attribute is not set!" );
    }

    String classAttName = m_classAtt.name();
    m_indexOfClass = rowMeta.indexOfValue( classAttName );
    if ( m_indexOfClass < 0 ) {
      throw new KettleException( "Unable to find class attribute '" + classAttName + "' in incoming row data" );
    }

    ValueMetaInterface classMeta = rowMeta.getValueMeta( m_indexOfClass );
    if ( m_classAtt.isNominal() ) {
      if ( !classMeta.isString() ) {
        throw new KettleException( "Class is nominal but incoming metadata for class is not of type String" );
      }

      // Look for fields corresponding to the predicted class distribution - i.e. there should
      // be one incoming field for each legal nominal value of the class
      m_predictedLabelIndexes = new ArrayList<>();
      m_preds = new double[m_classAtt.numValues()];
      for ( int i = 0; i < m_classAtt.numValues(); i++ ) {
        String label = m_classAtt.value( i );
        String predictedLabelFieldName = PREDICTED_FIELD_PREFIX + classAttName + "_" + label;
        int labelIndex = rowMeta.indexOfValue( predictedLabelFieldName );
        if ( labelIndex < 0 ) {
          throw new KettleException( "Was unable to find a field (" + predictedLabelFieldName
              + ") for the predicted probability of class label '" + label + "' in the incoming row structure" );
        }
        m_predictedLabelIndexes.add( labelIndex );
      }
    }

    if ( m_classAtt.isNumeric() ) {
      if ( !classMeta.isNumeric() ) {
        throw new KettleException( "Class is numeric but incoming metadata for class is not numeric" );
      }
      m_classIsNumeric = true;
      m_preds = new double[1];

      // Look for predicted class field in incoming row structure
      String predictedClassFieldName = PREDICTED_FIELD_PREFIX + classAttName;
      m_indexOfPredictedNumericClass = rowMeta.indexOfValue( predictedClassFieldName );
      if ( m_indexOfPredictedNumericClass < 0 ) {
        throw new KettleException( "Was unable to find the predicted numeric class '" + predictedClassFieldName
            + "' in the incoming row structure" );
      }
    }

    Attribute dummyClass = m_classAtt.copy( m_classAtt.name() );
    Instances insts = new Instances( "Dummy", new ArrayList<Attribute>( Arrays.asList( dummyClass ) ), 0 );
    m_dummyInstanceBackingArray = new double[1];
    m_dummyInstance = new DenseInstance( 1.0, m_dummyInstanceBackingArray );
    m_dummyInstance.setDataset( insts );
    insts.setClassIndex( 0 );
  }

  public void evaluateForRow( RowMetaInterface rowMeta, Object[] row, boolean storePredsForAUC,
      LogChannelInterface log ) throws KettleException {
    if ( m_eval == null ) {
      throw new KettleException( "Has not been initialized yet" );
    }

    if ( m_classIsNumeric ) {
      if ( row[m_indexOfPredictedNumericClass] != null && row[m_indexOfClass] != null ) {
        m_preds[0] =
            rowMeta.getValueMeta( m_indexOfPredictedNumericClass ).getNumber( row[m_indexOfPredictedNumericClass] );
        m_dummyInstanceBackingArray[0] = rowMeta.getValueMeta( m_indexOfClass ).getNumber( row[m_indexOfClass] );
        try {
          m_eval.evaluationForSingleInstance( m_preds, m_dummyInstance, storePredsForAUC );
        } catch ( Exception e ) {
          throw new KettleException( e );
        }
      }
    } else {
      ValueMetaInterface classMeta = rowMeta.getValueMeta( m_indexOfClass );
      String classValue = classMeta.getString( row[m_indexOfClass] );
      if ( classValue != null ) {
        if ( m_classAtt.indexOfValue( classValue ) < 0 ) {
          log.logBasic( "Unknown class value '" + classValue + "'. Skipping..." );
        } else {
          int zeroCount = 0;
          for ( int i = 0; i < m_classAtt.numValues(); i++ ) {
            int labIndex = m_predictedLabelIndexes.get( i );
            if ( row[labIndex] != null ) {
              m_preds[i] = rowMeta.getValueMeta( labIndex ).getNumber( row[labIndex] );
              if ( m_preds[i] == 0 ) {
                zeroCount++;
              }
            } else {
              zeroCount++;
            }
          }
          m_dummyInstanceBackingArray[0] = m_classAtt.indexOfValue( classValue );
          try {
            m_eval.evaluationForSingleInstance( m_preds, m_dummyInstance, storePredsForAUC );
          } catch ( Exception e ) {
            throw new KettleException( e );
          }
        }
      } else {
        log.logDetailed( "Class value is null. Skipping..." );
      }
    }
  }

  public void getOutputFields( RowMetaInterface outRowMeta, boolean outputPerClassIR, boolean outputAUC )
      throws KettlePluginException {
    // TODO. Assumes this object has been configured
    outRowMeta.clear();
    ValueMetaInterface
        vm =
        ValueMetaFactory
            .createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.UnclassifiedInstancesFieldName" ),
                ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );

    if ( !m_classIsNumeric ) {
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

      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.MAEFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.RMSEFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
    } else {
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.CorrCoeffFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );
    }

    // TODO add relative metrics here... Need user-specified training data class priors

    vm =
        ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.TotalNumInstancesFieldName" ),
            ValueMetaInterface.TYPE_NUMBER );
    outRowMeta.addValueMeta( vm );

    if ( !m_classIsNumeric ) {
      vm =
          ValueMetaFactory.createValueMeta( BaseMessages.getString( PKG, "BasePMIStepData.KappaStatisticFieldName" ),
              ValueMetaInterface.TYPE_NUMBER );
      outRowMeta.addValueMeta( vm );

      // Per-class IR statistics
      if ( outputPerClassIR ) {
        for ( int i = 0; i < m_classAtt.numValues(); i++ ) {
          String label = m_classAtt.value( i );

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

      if ( outputAUC ) {
        for ( int i = 0; i < m_classAtt.numValues(); i++ ) {
          String label = m_classAtt.value( i );
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

  public Object[] getEvalRow( RowMetaInterface outRowMeta, boolean outputPerClassIR, boolean outputAUC ) {
    Object[] outputRow = RowDataUtil.allocateRowData( outRowMeta.size() );

    int i = 0;
    outputRow[i++] = m_eval.unclassified();
    if ( !m_classIsNumeric ) {
      outputRow[i++] = m_eval.correct();
      outputRow[i++] = m_eval.incorrect();
      outputRow[i++] = m_eval.pctCorrect();
      outputRow[i++] = m_eval.pctIncorrect();
    }

    outputRow[i++] = m_eval.meanAbsoluteError();
    outputRow[i++] = m_eval.rootMeanSquaredError();

    if ( m_classIsNumeric ) {
      try {
        outputRow[i++] = m_eval.correlationCoefficient();
      } catch ( Exception e ) {
        e.printStackTrace();
      }
    }

    // TODO relative measures go here

    outputRow[i++] = m_eval.numInstances();
    if ( !m_classIsNumeric ) {
      outputRow[i++] = m_eval.kappa();

      if ( outputPerClassIR ) {
        for ( int j = 0; j < m_classAtt.numValues(); j++ ) {
          outputRow[i++] = m_eval.truePositiveRate( j );
          outputRow[i++] = m_eval.falsePositiveRate( j );
          outputRow[i++] = m_eval.precision( j );
          outputRow[i++] = m_eval.recall( j );
          outputRow[i++] = m_eval.fMeasure( j );
          outputRow[i++] = m_eval.matthewsCorrelationCoefficient( j );
        }
      }

      if ( outputAUC ) {
        for ( int j = 0; j < m_classAtt.numValues(); j++ ) {
          outputRow[i++] = m_eval.areaUnderROC( j );
          outputRow[i++] = m_eval.areaUnderPRC( j );
        }
      }
    }

    return outputRow;
  }
}
