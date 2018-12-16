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
import org.pentaho.di.core.util.Utils;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;

/**
 * Simple step that computes supervised evaluation metrics from incoming ground truth class values and predicted
 * class values (as produced as output from a machine learning scheme). Can handle both numeric and nominal classes.
 * When the class is nominal, it is assumed that the predicted values are in the form of a probability distribution for
 * each row. If the class column is called "class", and it is numeric, then the step will look for a incoming field
 * called "predicted_class". If the class is nominal, then the step will determine which values it can take on by looking
 * for fields called "predicted_class_&ltlabel1&gt", "predicted_class_&ltlabel2&gt"..., where "label1", "label2" etc. are
 * the legal values that the class can assume, and the values of these fields are the predicted probabilites associated with
 * each label for the given instance (row).
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class SupervisedEvaluator extends BaseStep implements StepInterface {

  private static Class<?> PKG = SupervisedEvaluator.class;

  protected SupervisedEvaluatorMeta m_meta;
  protected SupervisedEvaluatorData m_data;

  /**
   * Constructor
   *
   * @param stepMeta
   * @param stepDataInterface
   * @param copyNr
   * @param transMeta
   * @param trans
   */
  public SupervisedEvaluator( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta,
      Trans trans ) {
    super( stepMeta, stepDataInterface, copyNr, transMeta, trans );
  }

  /**
   * Initializes the step and performs configuration checks
   *
   * @param stepMeta the step metadata
   * @param stepData the setep data
   * @return true if all is good.
   */
  public boolean init( StepMetaInterface stepMeta, StepDataInterface stepData ) {

    if ( super.init( stepMeta, stepData ) ) {
      m_meta = (SupervisedEvaluatorMeta) stepMeta;
      m_data = (SupervisedEvaluatorData) stepData;

      try {
        if ( Utils.isEmpty( m_meta.getClassName() ) ) {
          throw new KettleException( "No class field specified!" );
        }
      } catch ( Exception ex ) {
        logError( ex.getMessage(), ex );
        ex.printStackTrace();
        return false;
      }
      return true;
    }
    return false;
  }

  /**
   * Row processing logic
   *
   * @param smi step metadata
   * @param sdi step data
   * @return false if there is no more processing to be done
   * @throws KettleException if a problem occurs
   */
  @Override public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {
    Object[] inputRow = getRow();

    if ( first ) {
      first = false;
      m_data.m_outputRowMeta = getInputRowMeta().clone();
      m_data.m_evaluatorUtil =
          new GeneralSupervisedEvaluatorUtil( m_data.m_outputRowMeta, environmentSubstitute( m_meta.getClassName() ) );
      m_data.m_evaluatorUtil
          .getOutputFields( m_data.m_outputRowMeta, m_meta.getOutputIRStats(), m_meta.getOutputAUC() );
    }

    if ( isStopped() ) {
      return false;
    }

    if ( inputRow == null ) {
      // finished - compute eval statistics and output
      putRow( m_data.m_outputRowMeta, m_data.m_evaluatorUtil
          .getEvalRow( m_data.m_outputRowMeta, m_meta.getOutputIRStats(), m_meta.getOutputAUC() ) );
      setOutputDone();
      return false;
    } else {
      m_data.m_evaluatorUtil.evaluateForRow( getInputRowMeta(), inputRow, m_meta.getOutputAUC(), log );
    }

    if ( checkFeedback( getLinesRead() ) ) {
      logBasic( BaseMessages.getString( PKG, "BasePMIStep.Message.LineNumber", getLinesRead() ) );
    }

    return true;
  }
}
