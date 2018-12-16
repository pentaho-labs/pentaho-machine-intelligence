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

package org.pentaho.di.ui.trans.steps.pmi;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.util.Utils;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.trans.steps.pmi.BaseSupervisedPMIStepMeta;
import org.pentaho.di.trans.steps.pmi.SupervisedEvaluatorMeta;
import org.pentaho.di.ui.core.widget.ComboVar;
import org.pentaho.di.ui.trans.step.BaseStepDialog;

import static org.pentaho.di.core.Const.MARGIN;
import static org.pentaho.di.ui.trans.steps.pmi.BaseSupervisedPMIStepDialog.FIRST_LABEL_RIGHT_PERCENTAGE;
import static org.pentaho.di.ui.trans.steps.pmi.BaseSupervisedPMIStepDialog.FIRST_PROMPT_RIGHT_PERCENTAGE;

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
public class SupervisedEvaluatorDialog extends BaseStepDialog implements StepDialogInterface {
  private Class<?> PKG = BaseSupervisedPMIStepMeta.PKG;

  protected static int MIDDLE;

  protected SupervisedEvaluatorMeta m_inputMeta;
  protected SupervisedEvaluatorMeta m_originalMeta;

  private Control lastControl;

  protected ComboVar m_classDropDown;

  // protected TextVar m_nominalLabelsField;

  protected Button m_outputIRStatsBut;

  protected Button m_outputAUCBut;

  protected ModifyListener m_simpleModifyListener = new ModifyListener() {
    @Override public void modifyText( ModifyEvent modifyEvent ) {
      m_inputMeta.setChanged();
    }
  };

  protected SelectionAdapter m_simpleSelectionListener = new SelectionAdapter() {
    @Override public void widgetSelected( SelectionEvent selectionEvent ) {
      super.widgetSelected( selectionEvent );
      m_inputMeta.setChanged();
    }
  };

  public SupervisedEvaluatorDialog( Shell parent, Object inMeta, TransMeta tr, String stepName ) {
    super( parent, (BaseStepMeta) inMeta, tr, stepName );

    m_inputMeta = (SupervisedEvaluatorMeta) inMeta;
    m_originalMeta = (SupervisedEvaluatorMeta) m_inputMeta.clone();
  }

  @Override public String open() {

    initialDialogSetup();

    Label classDropDownLab = new Label( shell, SWT.RIGHT );
    props.setLook( classDropDownLab );
    classDropDownLab.setText( BaseMessages.getString( PKG, "SupervisedEvaluator.ClassDropDown.Label" ) );
    classDropDownLab.setLayoutData( getFirstLabelFormData() );

    m_classDropDown = new ComboVar( transMeta, shell, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_classDropDown );
    m_classDropDown.setEditable( true );
    m_classDropDown.addSelectionListener( m_simpleSelectionListener );

    m_classDropDown.setLayoutData( getFirstPromptFormData( classDropDownLab ) );
    // TODO populate with incoming fields
    lastControl = m_classDropDown;

    /*Label nominalValsLab = new Label( shell, SWT.RIGHT );
    props.setLook( nominalValsLab );
    nominalValsLab.setText( BaseMessages.getString( PKG, "SupervisedEvaluator.NominalValues.Label" ) );
    nominalValsLab.setLayoutData( getFirstLabelFormData() );

    m_nominalLabelsField = new TextVar( transMeta, shell, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_nominalLabelsField );
    m_nominalLabelsField.addModifyListener( m_simpleModifyListener );
    m_nominalLabelsField.setLayoutData( getFirstPromptFormData( nominalValsLab ) );
    lastControl = m_nominalLabelsField; */

    Label aucStatsLabel = new Label( shell, SWT.RIGHT );
    props.setLook( aucStatsLabel );
    aucStatsLabel.setText( BaseMessages.getString( PKG, "SupervisedEvaluator.OutputAUCStats.Label" ) );
    aucStatsLabel.setLayoutData( getFirstLabelFormData() );

    m_outputAUCBut = new Button( shell, SWT.CHECK );
    props.setLook( m_outputAUCBut );
    m_outputAUCBut.setLayoutData( getFirstPromptFormData( aucStatsLabel ) );
    m_outputAUCBut.addSelectionListener( m_simpleSelectionListener );
    lastControl = m_outputAUCBut;

    Label irStatsLabel = new Label( shell, SWT.RIGHT );
    props.setLook( irStatsLabel );
    irStatsLabel.setText( BaseMessages.getString( PKG, "SupervisedEvaluator.OutputIRStats.Label" ) );
    irStatsLabel.setLayoutData( getFirstLabelFormData() );

    m_outputIRStatsBut = new Button( shell, SWT.CHECK );
    props.setLook( m_outputIRStatsBut );
    m_outputIRStatsBut.setLayoutData( getFirstPromptFormData( irStatsLabel ) );
    m_outputIRStatsBut.addSelectionListener( m_simpleSelectionListener );
    lastControl = m_outputIRStatsBut;

    // some buttons
    wOK = new Button( shell, SWT.PUSH );
    wOK.setText( BaseMessages.getString( PKG, "System.Button.OK" ) ); //$NON-NLS-1$
    wOK.addListener( SWT.Selection, new Listener() {
      @Override public void handleEvent( Event e ) {
        ok();
      }
    } );
    wCancel = new Button( shell, SWT.PUSH );
    wCancel.setText( BaseMessages.getString( PKG, "System.Button.Cancel" ) ); //$NON-NLS-1$
    wCancel.addListener( SWT.Selection, new Listener() {
      @Override public void handleEvent( Event e ) {
        cancel();
      }
    } );
    setButtonPositions( new Button[] { wOK, wCancel }, MARGIN, null );

    lsDef = new SelectionAdapter() {
      @Override public void widgetDefaultSelected( SelectionEvent e ) {
        ok();
      }
    };

    getData( m_inputMeta );

    wStepname.addSelectionListener( lsDef );

    // Detect X or ALT-F4 or something that kills this window...
    shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent e ) {
        cancel();
      }
    } );

    m_inputMeta.setChanged( changed );

    setSize();

    shell.open();

    Shell parent = getParent();
    Display display = parent.getDisplay();
    while ( !shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }
    return stepname;
  }

  protected void getData( SupervisedEvaluatorMeta meta ) {
    m_classDropDown.setText( meta.getClassName() );
    // m_nominalLabelsField.setText( meta.getNominalLabelList() );
    m_outputAUCBut.setSelection( meta.getOutputAUC() );
    m_outputIRStatsBut.setSelection( meta.getOutputIRStats() );
    try {
      populateClassDropDown();
    } catch ( KettleStepException e ) {
      e.printStackTrace();
    }
  }

  protected void setData( SupervisedEvaluatorMeta meta ) {
    meta.setClassName( m_classDropDown.getText() );
    meta.setOutputAUC( m_outputAUCBut.getSelection() );
    meta.setOutputIRStats( m_outputIRStatsBut.getSelection() );
  }

  protected void initialDialogSetup() {
    Shell parent = getParent();
    Display display = parent.getDisplay();

    shell = new Shell( parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN );
    props.setLook( shell );
    setShellImage( shell, m_inputMeta );

    changed = m_inputMeta.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = Const.FORM_MARGIN;
    formLayout.marginHeight = Const.FORM_MARGIN;
    shell.setLayout( formLayout );
    shell.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Shell.Title", "Supervised evaluator" ) );

    MIDDLE = props.getMiddlePct();

    wlStepname = new Label( shell, SWT.RIGHT );
    wlStepname.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Stepname.Label" ) ); //$NON-NLS-1$
    props.setLook( wlStepname );
    fdlStepname = new FormData();
    fdlStepname.left = new FormAttachment( 0, 0 );
    fdlStepname.right = new FormAttachment( MIDDLE, -MARGIN );
    fdlStepname.top = new FormAttachment( 0, MARGIN );
    wlStepname.setLayoutData( fdlStepname );
    wStepname = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    wStepname.setText( stepname );
    props.setLook( wStepname );
    wStepname.addModifyListener( m_simpleModifyListener );
    fdStepname = new FormData();
    fdStepname.left = new FormAttachment( MIDDLE, 0 );
    fdStepname.top = new FormAttachment( 0, MARGIN );
    fdStepname.right = new FormAttachment( 100, 0 );
    wStepname.setLayoutData( fdStepname );
    lastControl = wStepname;
  }

  private FormData getFirstLabelFormData() {
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( FIRST_LABEL_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );
    return fd;
  }

  private FormData getFirstPromptFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, MARGIN );
    fd.right = new FormAttachment( FIRST_PROMPT_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );
    return fd;
  }

  protected void populateClassDropDown() throws KettleStepException {
    RowMetaInterface rowMeta = transMeta.getPrevStepFields( stepname );
    String existingC = m_classDropDown.getText();
    m_classDropDown.removeAll();
    if ( rowMeta != null ) {
      for ( ValueMetaInterface vm : rowMeta.getValueMetaList() ) {
        if ( vm.isString() || vm.isNumber() ) {
          m_classDropDown.add( vm.getName() );
        }
      }
    }
    if ( !Utils.isEmpty( existingC ) ) {
      m_classDropDown.setText( existingC );
    }
  }

  protected void ok() {
    if ( Const.isEmpty( wStepname.getText() ) ) {
      return;
    }

    stepname = wStepname.getText(); // return value

    setData( m_inputMeta );
    if ( !m_originalMeta.equals( m_inputMeta ) ) {
      m_inputMeta.setChanged();
      changed = m_inputMeta.hasChanged();
    }

    dispose();
  }

  protected void cancel() {
    stepname = null;
    m_inputMeta.setChanged( changed );
    dispose();
  }
}
