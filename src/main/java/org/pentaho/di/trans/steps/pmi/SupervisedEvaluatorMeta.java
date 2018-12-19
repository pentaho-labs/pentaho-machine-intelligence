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

import org.pentaho.MetaHelper;
import org.pentaho.SimpleStepOption;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.util.Utils;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.di.ui.trans.steps.pmi.SupervisedEvaluatorDialog;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;
import weka.core.Attribute;

import java.util.Arrays;
import java.util.List;

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
@Step( id = "SupervisedEvaluator", image = "WEKAS.svg", name = "Supervised Evaluator", description = "Compute supervised evaluation metrics for incoming row data that contains predictions from a learning scheme", categoryDescription = "Data Mining" )
public class SupervisedEvaluatorMeta extends BaseStepMeta implements StepMetaInterface {

  protected String m_className = "";

  protected boolean m_outputIRStats;

  protected boolean m_outputAUC;

  @SimpleStepOption public void setClassName( String name ) {
    m_className = name;
  }

  public String getClassName() {
    return m_className;
  }

  @SimpleStepOption public void setOutputIRStats( boolean output ) {
    m_outputIRStats = output;
  }

  public boolean getOutputIRStats() {
    return m_outputIRStats;
  }

  @SimpleStepOption public void setOutputAUC( boolean output ) {
    m_outputAUC = output;
  }

  public boolean getOutputAUC() {
    return m_outputAUC;
  }

  @Override public void setDefault() {
    m_outputIRStats = false;
    m_outputAUC = false;
  }

  @Override public String getXML() {
    try {
      return MetaHelper.getXMLForTarget( this ).toString();
    } catch ( Exception ex ) {
      ex.printStackTrace();
      return "";
    }
  }

  @Override public void loadXML( Node stepnode, List<DatabaseMeta> dbs, IMetaStore metaStore ) {
    try {
      MetaHelper.loadXMLForTarget( stepnode, this );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
  }

  @Override public void saveRep( Repository rep, IMetaStore metaStore, ObjectId id_transformation, ObjectId id_step ) {
    try {
      MetaHelper.saveRepForTarget( rep, id_transformation, id_step, this );
    } catch ( Exception ex ) {
      ex.printStackTrace();
    }
  }

  public void readRep( Repository rep, IMetaStore metaStore, ObjectId id_step, List<DatabaseMeta> dbs ) {
    try {
      MetaHelper.readRepForTarget( rep, id_step, this );
    } catch ( Exception ex ) {
      ex.printStackTrace();
    }
  }

  @Override
  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int i, TransMeta transMeta,
      Trans trans ) {
    return new SupervisedEvaluator( stepMeta, stepDataInterface, i, transMeta, trans );
  }

  @Override public StepDataInterface getStepData() {
    return new SupervisedEvaluatorData();
  }

  public String getDialogClassName() {
    return SupervisedEvaluatorDialog.class.getCanonicalName();
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

  @Override public void getFields( RowMetaInterface rowMeta, String stepName, RowMetaInterface[] info, StepMeta nextStep,
      VariableSpace space, Repository repo, IMetaStore metaStore ) throws KettleStepException {

    if ( rowMeta != null && rowMeta.size() > 0 && !Utils.isEmpty( getClassName() ) ) {
      // String nominalVals = space.environmentSubstitute( getNominalLabelList() );
      String className = space.environmentSubstitute( getClassName() );
      try {
        // GeneralSupervisedEvaluatorUtil eval = new GeneralSupervisedEvaluatorUtil( rowMeta, className, nominalVals );
        // trans.getPrevStepFiel;
        GeneralSupervisedEvaluatorUtil eval = new GeneralSupervisedEvaluatorUtil( rowMeta, className );
        eval.getOutputFields( rowMeta, getOutputIRStats(), getOutputAUC() );
      } catch ( KettleException e ) {
        throw new KettleStepException( e );
      }
    } else if ( rowMeta != null ) {
      rowMeta.clear();
    }
  }
}
