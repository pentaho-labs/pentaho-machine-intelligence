/*******************************************************************************
 * Pentaho Data Science
 * <p/>
 * Copyright (c) 2002-2018 Hitachi Vantara. All rights reserved.
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

package org.pentaho.di.ui.trans.steps.pmi.weka;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Props;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.steps.pmi.weka.PMIForecastingData;
import org.pentaho.di.trans.steps.pmi.weka.PMIForecastingMeta;

import org.pentaho.di.trans.steps.pmi.weka.WekaForecastingModel;
import org.pentaho.di.ui.core.widget.TextVar;
import org.pentaho.di.ui.trans.step.BaseStepDialog;

import weka.classifiers.timeseries.core.OverlayForecaster;
import weka.filters.supervised.attribute.TSLagMaker;
import weka.classifiers.timeseries.core.TSLagUser;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * The UI class for the PMIForecasting step
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision$
 */
public class PMIForecastingDialog extends BaseStepDialog implements StepDialogInterface {

  /**
   * various UI bits and pieces for the dialog
   */
  private Label m_wlStepname;
  private Text m_wStepname;
  private FormData m_fdlStepname;
  private FormData m_fdStepname;

  private FormData m_fdTabFolder;
  private FormData m_fdFileComp, m_fdFieldsComp, m_fdModelComp;

  // The tabs of the dialog
  private CTabFolder m_wTabFolder;
  private CTabItem m_wFileTab, m_wFieldsTab, m_wModelTab;

  // label for the file name field
  private Label m_wlFilename;

  // file name field
  private FormData m_fdlFilename, m_fdbFilename, m_fdFilename;

  // Browse file button
  private Button m_wbFilename;

  // Combines text field with widget to insert environment variable
  private TextVar m_wFilename;

  // Num steps stuff
  private Label m_numStepsLab;
  private TextVar m_numStepsText;

  // Artificial offset stuff
  private Label m_artificialTimeOffsetLab;
  private TextVar m_artificialTimeOffsetText;

  // Rebuild model check box
  private Label m_rebuildForecasterLab;
  private Button m_rebuildForecasterCheckBox;

  // Save forecaster stuff
  private Label m_saveForecasterLab;
  private Button m_saveForecasterBut;
  private TextVar m_saveForecasterField;

  // file extension stuff
  /*
   * private Label m_wlExtension; private Text m_wExtension; private FormData
   * m_fdlExtension, m_fdExtension;
   */

  // the text area for the model
  private Text m_wModelText;
  private FormData m_fdModelText;

  // the text area for the fields mapping
  private Text m_wMappingText;
  private FormData m_fdMappingText;

  /**
   * meta data for the step. A copy is made so that changes, in terms of choices
   * made by the user, can be detected.
   */
  private final PMIForecastingMeta m_currentMeta;
  private final PMIForecastingMeta m_originalMeta;

  public PMIForecastingDialog( Shell parent, Object in, TransMeta tr, String sname ) {

    super( parent, (BaseStepMeta) in, tr, sname );

    // The order here is important...
    // m_currentMeta is looked at for changes
    m_currentMeta = (PMIForecastingMeta) in;
    m_originalMeta = (PMIForecastingMeta) m_currentMeta.clone();
  }

  /**
   * Open the dialog
   *
   * @return the step name
   */
  @Override public String open() {
    Shell parent = getParent();
    Display display = parent.getDisplay();

    shell = new Shell( parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );

    props.setLook( shell );
    setShellImage( shell, m_currentMeta );

    // used to listen to a text field (m_wStepname)
    ModifyListener lsMod = new ModifyListener() {
      @Override public void modifyText( ModifyEvent e ) {
        m_currentMeta.setChanged();
      }
    };

    changed = m_currentMeta.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = Const.FORM_MARGIN;
    formLayout.marginHeight = Const.FORM_MARGIN;

    shell.setLayout( formLayout );
    shell.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.Shell.Title" ) );

    int middle = 50;
    int margin = Const.MARGIN;

    // Stepname line
    m_wlStepname = new Label( shell, SWT.RIGHT );
    m_wlStepname.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.StepName.Label" ) );
    props.setLook( m_wlStepname );

    m_fdlStepname = new FormData();
    m_fdlStepname.left = new FormAttachment( 0, 0 );
    m_fdlStepname.right = new FormAttachment( middle, -margin );
    m_fdlStepname.top = new FormAttachment( 0, margin );
    m_wlStepname.setLayoutData( m_fdlStepname );
    m_wStepname = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    m_wStepname.setText( stepname );
    props.setLook( m_wStepname );
    m_wStepname.addModifyListener( lsMod );

    // format the text field
    m_fdStepname = new FormData();
    m_fdStepname.left = new FormAttachment( middle, 0 );
    m_fdStepname.top = new FormAttachment( 0, margin );
    m_fdStepname.right = new FormAttachment( 100, 0 );
    m_wStepname.setLayoutData( m_fdStepname );

    m_wTabFolder = new CTabFolder( shell, SWT.BORDER );
    props.setLook( m_wTabFolder, Props.WIDGET_STYLE_TAB );
    m_wTabFolder.setSimple( false );

    // Start of the file tab
    m_wFileTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wFileTab.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.FileTab.TabTitle" ) );

    Composite wFileComp = new Composite( m_wTabFolder, SWT.NONE );
    props.setLook( wFileComp );

    FormLayout fileLayout = new FormLayout();
    fileLayout.marginWidth = 3;
    fileLayout.marginHeight = 3;
    wFileComp.setLayout( fileLayout );

    // Filename line
    m_wlFilename = new Label( wFileComp, SWT.RIGHT );
    m_wlFilename.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.Filename.Label" ) );
    props.setLook( m_wlFilename );
    m_fdlFilename = new FormData();
    m_fdlFilename.left = new FormAttachment( 0, 0 );
    m_fdlFilename.top = new FormAttachment( 0, margin );
    m_fdlFilename.right = new FormAttachment( middle, -margin );
    m_wlFilename.setLayoutData( m_fdlFilename );

    // file browse button
    m_wbFilename = new Button( wFileComp, SWT.PUSH | SWT.CENTER );
    props.setLook( m_wbFilename );
    m_wbFilename.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "System.Button.Browse" ) );
    m_fdbFilename = new FormData();
    m_fdbFilename.right = new FormAttachment( 100, 0 );
    m_fdbFilename.top = new FormAttachment( 0, 0 );
    m_wbFilename.setLayoutData( m_fdbFilename );

    // combined text field and env variable widget
    m_wFilename = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_wFilename );
    m_wFilename.addModifyListener( lsMod );
    m_fdFilename = new FormData();
    m_fdFilename.left = new FormAttachment( middle, 0 );
    m_fdFilename.top = new FormAttachment( 0, margin );
    m_fdFilename.right = new FormAttachment( m_wbFilename, -margin );
    m_wFilename.setLayoutData( m_fdFilename );

    // steps text field
    m_numStepsLab = new Label( wFileComp, SWT.RIGHT );
    m_numStepsLab.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.NumSteps.Label" ) );
    props.setLook( m_numStepsLab );
    FormData fmd = new FormData();
    fmd.left = new FormAttachment( 0, 0 );
    fmd.right = new FormAttachment( middle, -margin );
    fmd.top = new FormAttachment( m_wFilename, margin );
    m_numStepsLab.setLayoutData( fmd );

    m_numStepsText = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    m_numStepsText
        .setToolTipText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.NumSteps.ToolTip" ) );
    m_numStepsLab
        .setToolTipText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.NumSteps.ToolTip" ) );
    props.setLook( m_numStepsText );
    m_numStepsText.addModifyListener( lsMod );
    m_numStepsText.setText( "" + m_originalMeta.getNumStepsToForecast() );
    fmd = new FormData();
    fmd.left = new FormAttachment( m_numStepsLab, margin );
    fmd.right = new FormAttachment( 100, -margin );
    fmd.top = new FormAttachment( m_wFilename, margin );
    m_numStepsText.setLayoutData( fmd );

    // Artificial time stamp offset field
    m_artificialTimeOffsetLab = new Label( wFileComp, SWT.RIGHT );
    m_artificialTimeOffsetLab
        .setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.ArtificialTimeOffset.Label" ) );
    props.setLook( m_artificialTimeOffsetLab );
    fmd = new FormData();
    fmd.left = new FormAttachment( 0, 0 );
    fmd.left = new FormAttachment( 0, 0 );
    fmd.right = new FormAttachment( middle, -margin );
    fmd.top = new FormAttachment( m_numStepsText, margin );
    m_artificialTimeOffsetLab.setLayoutData( fmd );

    m_artificialTimeOffsetText = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    m_artificialTimeOffsetText.setToolTipText(
        BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.ArtificialTimeOffset.ToolTip" ) );
    m_artificialTimeOffsetLab.setToolTipText(
        BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.ArtificialTimeOffset.ToolTip" ) );
    props.setLook( m_artificialTimeOffsetText );
    m_artificialTimeOffsetText.addModifyListener( lsMod );
    m_artificialTimeOffsetText.setText( "" + m_originalMeta.getArtificialTimeStartOffset() );
    fmd = new FormData();
    fmd.left = new FormAttachment( m_artificialTimeOffsetLab, margin );
    fmd.right = new FormAttachment( 100, -margin );
    fmd.top = new FormAttachment( m_numStepsText, margin );
    m_artificialTimeOffsetText.setLayoutData( fmd );

    m_rebuildForecasterLab = new Label( wFileComp, SWT.RIGHT );
    m_rebuildForecasterLab
        .setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.RebuildForecaster.Label" ) );
    props.setLook( m_rebuildForecasterLab );
    fmd = new FormData();
    fmd.left = new FormAttachment( 0, 0 );
    fmd.left = new FormAttachment( 0, 0 );
    fmd.right = new FormAttachment( middle, -margin );
    fmd.top = new FormAttachment( m_artificialTimeOffsetText, margin );
    m_rebuildForecasterLab.setLayoutData( fmd );

    m_rebuildForecasterCheckBox = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_rebuildForecasterCheckBox );
    fmd = new FormData();
    fmd.left = new FormAttachment( 0, 0 );
    fmd.left = new FormAttachment( m_rebuildForecasterLab, margin );
    fmd.right = new FormAttachment( 100, -margin );
    fmd.top = new FormAttachment( m_artificialTimeOffsetText, margin );
    m_rebuildForecasterCheckBox.setLayoutData( fmd );

    // -----
    m_saveForecasterLab = new Label( wFileComp, SWT.RIGHT );
    m_saveForecasterLab
        .setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.SaveFilename.Label" ) );
    props.setLook( m_saveForecasterLab );
    fmd = new FormData();
    fmd.left = new FormAttachment( 0, 0 );
    fmd.top = new FormAttachment( m_rebuildForecasterCheckBox, margin );
    fmd.right = new FormAttachment( middle, -margin );
    m_saveForecasterLab.setLayoutData( fmd );

    m_saveForecasterBut = new Button( wFileComp, SWT.PUSH | SWT.CENTER );
    props.setLook( m_saveForecasterBut );
    m_saveForecasterBut.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "System.Button.Browse" ) );
    fmd = new FormData();
    fmd.right = new FormAttachment( 100, 0 );
    fmd.top = new FormAttachment( m_rebuildForecasterCheckBox, 0 );
    m_saveForecasterBut.setLayoutData( fmd );

    // combined text field and env variable widget
    m_saveForecasterField = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_saveForecasterField );
    m_saveForecasterField.addModifyListener( lsMod );
    fmd = new FormData();
    fmd.left = new FormAttachment( middle, 0 );
    fmd.top = new FormAttachment( m_rebuildForecasterCheckBox, margin );
    fmd.right = new FormAttachment( m_saveForecasterBut, -margin );
    m_saveForecasterField.setLayoutData( fmd );

    m_fdFileComp = new FormData();
    m_fdFileComp.left = new FormAttachment( 0, 0 );
    m_fdFileComp.top = new FormAttachment( 0, 0 );
    m_fdFileComp.right = new FormAttachment( 100, 0 );
    m_fdFileComp.bottom = new FormAttachment( 100, 0 );
    wFileComp.setLayoutData( m_fdFileComp );

    wFileComp.layout();
    m_wFileTab.setControl( wFileComp );

    // ----------------------------

    // Fields mapping tab
    m_wFieldsTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wFieldsTab.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.FieldsTab.TabTitle" ) );

    FormLayout fieldsLayout = new FormLayout();
    fieldsLayout.marginWidth = 3;
    fieldsLayout.marginHeight = 3;

    Composite wFieldsComp = new Composite( m_wTabFolder, SWT.NONE );
    props.setLook( wFieldsComp );
    wFieldsComp.setLayout( fieldsLayout );

    // body of tab to be a scrolling text area
    // to display the mapping
    m_wMappingText = new Text( wFieldsComp, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL );
    m_wMappingText.setEditable( false );
    FontData fd = new FontData( "Courier New", 10, SWT.NORMAL );
    m_wMappingText.setFont( new Font( getParent().getDisplay(), fd ) );
    // m_wModelText.setText(stepname);
    props.setLook( m_wMappingText );
    // format the fields mapping text area
    m_fdMappingText = new FormData();
    m_fdMappingText.left = new FormAttachment( 0, 0 );
    m_fdMappingText.top = new FormAttachment( 0, margin );
    m_fdMappingText.right = new FormAttachment( 100, 0 );
    m_fdMappingText.bottom = new FormAttachment( 100, 0 );
    m_wMappingText.setLayoutData( m_fdMappingText );

    m_fdFieldsComp = new FormData();
    m_fdFieldsComp.left = new FormAttachment( 0, 0 );
    m_fdFieldsComp.top = new FormAttachment( 0, 0 );
    m_fdFieldsComp.right = new FormAttachment( 100, 0 );
    m_fdFieldsComp.bottom = new FormAttachment( 100, 0 );
    wFieldsComp.setLayoutData( m_fdFieldsComp );

    wFieldsComp.layout();
    m_wFieldsTab.setControl( wFieldsComp );

    // Model display tab
    m_wModelTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wModelTab.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.ModelTab.TabTitle" ) );

    FormLayout modelLayout = new FormLayout();
    modelLayout.marginWidth = 3;
    modelLayout.marginHeight = 3;

    Composite wModelComp = new Composite( m_wTabFolder, SWT.NONE );
    props.setLook( wModelComp );
    wModelComp.setLayout( modelLayout );

    // body of tab to be a scrolling text area
    // to display the pre-learned model

    m_wModelText = new Text( wModelComp, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL );
    m_wModelText.setEditable( false );
    fd = new FontData( "Courier New", 10, SWT.NORMAL );
    m_wModelText.setFont( new Font( getParent().getDisplay(), fd ) );

    // m_wModelText.setText(stepname);
    props.setLook( m_wModelText );
    // format the model text area
    m_fdModelText = new FormData();
    m_fdModelText.left = new FormAttachment( 0, 0 );
    m_fdModelText.top = new FormAttachment( 0, margin );
    m_fdModelText.right = new FormAttachment( 100, 0 );
    m_fdModelText.bottom = new FormAttachment( 100, 0 );
    m_wModelText.setLayoutData( m_fdModelText );

    m_fdModelComp = new FormData();
    m_fdModelComp.left = new FormAttachment( 0, 0 );
    m_fdModelComp.top = new FormAttachment( 0, 0 );
    m_fdModelComp.right = new FormAttachment( 100, 0 );
    m_fdModelComp.bottom = new FormAttachment( 100, 0 );
    wModelComp.setLayoutData( m_fdModelComp );

    wModelComp.layout();
    m_wModelTab.setControl( wModelComp );
    int tempF = m_wModelText.getStyle();
    /*
     * if ((tempF & (SWT.WRAP)) > 0) {
     * System.err.println("Wrap is turned on!!!!"); } else {
     * System.err.println("Wrap turned off"); }
     */

    m_fdTabFolder = new FormData();
    m_fdTabFolder.left = new FormAttachment( 0, 0 );
    m_fdTabFolder.top = new FormAttachment( m_wStepname, margin );
    m_fdTabFolder.right = new FormAttachment( 100, 0 );
    m_fdTabFolder.bottom = new FormAttachment( 100, -50 );
    m_wTabFolder.setLayoutData( m_fdTabFolder );

    // Buttons inherited from BaseStepDialog
    wOK = new Button( shell, SWT.PUSH );
    wOK.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "System.Button.OK" ) );

    wCancel = new Button( shell, SWT.PUSH );
    wCancel.setText( BaseMessages.getString( PMIForecastingMeta.PKG, "System.Button.Cancel" ) );

    setButtonPositions( new Button[] { wOK, wCancel }, margin, m_wTabFolder );

    // Add listeners
    lsCancel = new Listener() {
      @Override public void handleEvent( Event e ) {
        cancel();
      }
    };
    lsOK = new Listener() {
      @Override public void handleEvent( Event e ) {
        ok();
      }
    };

    wCancel.addListener( SWT.Selection, lsCancel );
    wOK.addListener( SWT.Selection, lsOK );

    lsDef = new SelectionAdapter() {
      @Override public void widgetDefaultSelected( SelectionEvent e ) {
        ok();
      }
    };

    m_wStepname.addSelectionListener( lsDef );

    // Detect X or ALT-F4 or something that kills this window...
    shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent e ) {
        cancel();
      }
    } );

    m_rebuildForecasterCheckBox.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        m_currentMeta.setChanged();

        m_saveForecasterLab.setEnabled( m_rebuildForecasterCheckBox.getSelection() );
        m_saveForecasterField.setEnabled( m_rebuildForecasterCheckBox.getSelection() );
        m_saveForecasterBut.setEnabled( m_rebuildForecasterCheckBox.getSelection() );

        m_artificialTimeOffsetLab.setEnabled( !m_rebuildForecasterCheckBox.getSelection() );
        m_artificialTimeOffsetText.setEnabled( !m_rebuildForecasterCheckBox.getSelection() );

        if ( !m_rebuildForecasterCheckBox.getSelection() ) {
          checkIfModelIsUsingArtificialTimeStamp( m_currentMeta.getModel() );
        }
      }
    } );

    // Whenever something changes, set the tooltip to the expanded version:
    m_wFilename.addModifyListener( new ModifyListener() {
      @Override public void modifyText( ModifyEvent e ) {
        m_wFilename.setToolTipText( transMeta.environmentSubstitute( m_wFilename.getText() ) );
      }
    } );

    // listen to the file name text box and try to load a model
    // if the user presses enter
    m_wFilename.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetDefaultSelected( SelectionEvent e ) {
        if ( !loadModel() ) {
          log.logError( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.Log.FileLoadingError" ) );
          // System.err.println("Problem loading model file!");
        } else {
          checkIfModelIsUsingArtificialTimeStamp( m_currentMeta.getModel() );
          checkIfModelIsUsingOverlayData( m_currentMeta.getModel() );
        }
      }
    } );

    m_saveForecasterBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        FileDialog dialog = new FileDialog( shell, SWT.SAVE );
        String[] extensions = null;
        String[] filterNames = null;

        extensions = new String[2];
        filterNames = new String[2];
        extensions[0] = "*.model";
        filterNames[0] =
            BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.FileType.ModelFileBinary" );
        extensions[1] = "*";
        filterNames[1] = BaseMessages.getString( PMIForecastingMeta.PKG, "System.FileType.AllFiles" );

        dialog.setFilterExtensions( extensions );

        if ( m_saveForecasterField.getText() != null ) {
          dialog.setFileName( transMeta.environmentSubstitute( m_saveForecasterField.getText() ) );
        }
        dialog.setFilterNames( filterNames );

        if ( dialog.open() != null ) {
          m_saveForecasterField
              .setText( dialog.getFileName() + System.getProperty( "file.separator" ) + dialog.getFileName() );
        }
      }
    } );

    m_wbFilename.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        FileDialog dialog = new FileDialog( shell, SWT.OPEN );
        String[] extensions = null;
        String[] filterNames = null;

        extensions = new String[2];
        filterNames = new String[2];
        extensions[0] = "*.model";
        filterNames[0] =
            BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.FileType.ModelFileBinary" );
        extensions[1] = "*";
        filterNames[1] = BaseMessages.getString( PMIForecastingMeta.PKG, "System.FileType.AllFiles" );

        dialog.setFilterExtensions( extensions );
        if ( m_wFilename.getText() != null ) {
          dialog.setFileName( transMeta.environmentSubstitute( m_wFilename.getText() ) );
        }
        dialog.setFilterNames( filterNames );

        if ( dialog.open() != null ) {
          /*
           * String extension = m_wExtension.getText(); if (extension != null &&
           * dialog.getFileName() != null && dialog.getFileName().endsWith("." +
           * extension)) { // The extension is filled in and matches the end //
           * of the selected file => Strip off the extension.
           */
          // String fileName = dialog.getFileName();
          /*
           * m_wFilename. setText(dialog.getFilterPath() +
           * System.getProperty("file.separator") + fileName.substring(0,
           * fileName.length() - (extension.length() + 1))); } else {
           */
          m_wFilename.setText( dialog.getFilterPath() + System.getProperty( "file.separator" ) + dialog.getFileName() );
          // }

          // try to load model file and display model
          if ( !loadModel() ) {
            log.logError(
                BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.Log.FileLoadingError" ) );
            // System.err.println("Problem loading model file!");
          } else {
            checkIfModelIsUsingArtificialTimeStamp( m_currentMeta.getModel() );
            checkIfModelIsUsingOverlayData( m_currentMeta.getModel() );
          }
        }
      }
    } );

    m_wTabFolder.setSelection( 0 );

    // Set the shell size, based upon previous time...
    setSize();

    getData();

    shell.open();
    while ( !shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }

    return stepname;
  }

  /**
   * Load the model.
   */
  private boolean loadModel() {
    String filename = m_wFilename.getText();
    if ( Const.isEmpty( filename ) ) {
      return false;
    }
    String modName = transMeta.environmentSubstitute( filename );
    File modelFile = null;
    if ( modName.startsWith( "file:" ) ) {
      try {
        modName = modName.replace( " ", "%20" );
        modelFile = new File( new java.net.URI( modName ) );
      } catch ( Exception ex ) {
        // System.err.println("Malformed URI");
        log.logError( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.Log.MalformedURI" ) );
        return false;
      }
    } else {
      modelFile = new File( modName );
    }
    boolean success = false;

    if ( !Const.isEmpty( filename ) && modelFile.exists() ) {
      try {
        WekaForecastingModel tempM = PMIForecastingData.loadSerializedModel( modelFile, log );
        m_wModelText.setText( tempM.toString() );

        m_currentMeta.setModel( tempM );

        /*
         * checkAbilityToProduceProbabilities(tempM);
         * checkAbilityToUpdateModelIncrementally(tempM);
         */

        // see if we can find a previous step and set up the
        // mappings
        mappingString( tempM );
        success = true;
      } catch ( Exception ex ) {
        log.logError( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.Log.FileLoadingError" ) );
        // System.err.println("Problem loading model file...");
      }
    }

    return success;
  }

  /**
   * Build a string that shows the mappings between Weka attributes and incoming
   * Kettle fields.
   *
   * @param model a <code>WekaForecastingModel</code> value
   */
  private void mappingString( WekaForecastingModel model ) {

    try {
      StepMeta stepMeta = transMeta.findStep( stepname );
      if ( stepMeta != null ) {
        RowMetaInterface rowM = transMeta.getPrevStepFields( stepMeta );
        Instances header = model.getHeader();
        m_currentMeta.mapIncomingRowMetaData( header, rowM );
        int[] mappings = m_currentMeta.getMappingIndexes();

        StringBuffer result = new StringBuffer( header.numAttributes() * 10 );

        int maxLength = 0;
        for ( int i = 0; i < header.numAttributes(); i++ ) {
          if ( header.attribute( i ).name().length() > maxLength ) {
            maxLength = header.attribute( i ).name().length();
          }
        }
        maxLength += 12; // length of " (nominal)"/" (numeric)"

        int minLength = 16; // "Model attributes".length()
        String headerS = "Model attributes";
        String sep = "----------------";

        if ( maxLength < minLength ) {
          maxLength = minLength;
        }
        headerS = getFixedLengthString( headerS, ' ', maxLength );
        sep = getFixedLengthString( sep, '-', maxLength );
        sep += "\t    ----------------\n";
        headerS += "\t    Incoming fields\n";
        result.append( headerS );
        result.append( sep );

        for ( int i = 0; i < header.numAttributes(); i++ ) {
          Attribute temp = header.attribute( i );
          String attName = "(";
          if ( temp.isNumeric() && !temp.isDate() ) {
            attName += "numeric)";
          } else if ( temp.isNominal() ) {
            attName += "nominal)";
          } else if ( temp.isDate() ) {
            attName += "date)";
          }

          attName += ( " " + temp.name() );
          attName = getFixedLengthString( attName, ' ', maxLength );
          attName += "\t--> ";
          result.append( attName );
          String inFieldNum = "";
          if ( mappings[i] == PMIForecastingData.NO_MATCH ) {
            inFieldNum += "- ";
            result.append( inFieldNum + "missing (no match)\n" );
          } else if ( mappings[i] == PMIForecastingData.TYPE_MISMATCH ) {
            inFieldNum += ( rowM.indexOfValue( temp.name() ) + 1 ) + " ";
            result.append( inFieldNum + "missing (type mis-match)\n" );
          } else {
            ValueMetaInterface tempField = rowM.getValueMeta( mappings[i] );
            String fieldName = "" + ( mappings[i] + 1 ) + " (";
            if ( tempField.isBoolean() ) {
              fieldName += "boolean)";
            } else if ( tempField.isNumeric() ) {
              fieldName += "numeric)";
            } else if ( tempField.isString() ) {
              fieldName += "string)";
            } else if ( tempField.isDate() ) {
              fieldName += "date)";
            }
            fieldName += " " + tempField.getName();
            result.append( fieldName + "\n" );
          }
        }

        // set the text of the text area in the Mappings tab
        m_wMappingText.setText( result.toString() );
      }
    } catch ( KettleException e ) {
      log.logError( BaseMessages.getString( PMIForecastingMeta.PKG, "PMIForecastingDialog.Log.UnableToFindInput" ) );
      return;
    }
  }

  /**
   * Grab data out of the step meta object
   */
  public void getData() {

    if ( m_currentMeta.getSerializedModelFileName() != null ) {
      m_wFilename.setText( m_currentMeta.getSerializedModelFileName() );
    }

    m_numStepsText.setText( "" + m_currentMeta.getNumStepsToForecast() );
    m_artificialTimeOffsetText.setText( "" + m_currentMeta.getArtificialTimeStartOffset() );

    // Grab model if it is available (and we are not reading model file
    // names from a field in the incoming data
    // if (!m_wAcceptFileNameFromFieldCheckBox.getSelection()) {
    WekaForecastingModel tempM = m_currentMeta.getModel();
    if ( tempM != null ) {
      m_wModelText.setText( tempM.toString() );

      // Grab mappings if available
      mappingString( tempM );
    } else {
      // try loading the model
      loadModel();
    }
    // }

    tempM = m_currentMeta.getModel();
    checkIfModelIsUsingArtificialTimeStamp( tempM );
    checkIfModelIsUsingOverlayData( tempM );

    m_rebuildForecasterCheckBox.setSelection( m_currentMeta.getRebuildForecaster() );
    m_saveForecasterLab.setEnabled( m_currentMeta.getRebuildForecaster() );
    m_saveForecasterField.setEnabled( m_currentMeta.getRebuildForecaster() );
    m_saveForecasterBut.setEnabled( m_currentMeta.getRebuildForecaster() );
    if ( !Const.isEmpty( m_currentMeta.getSavedForecasterFileName() ) ) {
      m_saveForecasterField.setText( m_currentMeta.getSavedForecasterFileName() );
    } else {
      m_saveForecasterField.setText( "" );
    }
  }

  private void checkIfModelIsUsingArtificialTimeStamp( WekaForecastingModel tempM ) {

    if ( tempM != null && tempM.getModel() instanceof TSLagUser ) {
      TSLagMaker lagMaker = ( (TSLagUser) tempM.getModel() ).getTSLagMaker();
      if ( lagMaker.isUsingAnArtificialTimeIndex() ) {
        m_artificialTimeOffsetText.setEnabled( true );
        m_artificialTimeOffsetLab.setEnabled( true );
        return;
      }
    }

    m_artificialTimeOffsetText.setEnabled( false );
    m_artificialTimeOffsetLab.setEnabled( false );
  }

  private void checkIfModelIsUsingOverlayData( WekaForecastingModel tempM ) {
    if ( tempM != null && tempM.getModel() instanceof OverlayForecaster ) {
      if ( ( (OverlayForecaster) tempM.getModel() ).isUsingOverlayData() ) {
        m_numStepsText.setEnabled( false );
        m_numStepsLab.setEnabled( false );
        // remove any number set here since size of the overlay data (with
        // missing
        // targets set) determines the number of steps that will be forecast
        m_numStepsText.setText( "" );
        return;
      }
    }

    m_numStepsText.setEnabled( true );
    m_numStepsLab.setEnabled( true );
  }

  private void cancel() {
    stepname = null;
    m_currentMeta.setChanged( changed );
    // m_currentMeta.setModel(null);
    // revert to original model
    WekaForecastingModel temp = m_originalMeta.getModel();
    m_currentMeta.setModel( temp );
    dispose();
  }

  private void ok() {
    if ( Const.isEmpty( m_wStepname.getText() ) ) {
      return;
    }

    stepname = m_wStepname.getText(); // return value

    if ( !Const.isEmpty( m_wFilename.getText() ) ) {
      m_currentMeta.setSerializedModelFileName( m_wFilename.getText() );
    } else {
      m_currentMeta.setSerializedModelFileName( null );
    }

    if ( !Const.isEmpty( m_numStepsText.getText() ) ) {
      try {
        m_currentMeta.setNumStepsToForecast( m_numStepsText.getText() );
      } catch ( NumberFormatException ex ) {
        // ignore
      }
    }

    if ( !Const.isEmpty( m_artificialTimeOffsetText.getText() ) ) {
      try {
        m_currentMeta.setArtificialTimeStartOffset( m_artificialTimeOffsetText.getText() );
      } catch ( NumberFormatException ex ) {
        // ignore
      }
    }

    m_currentMeta.setRebuildForecaster( m_rebuildForecasterCheckBox.getSelection() );
    if ( !Const.isEmpty( m_saveForecasterField.getText() ) ) {
      m_currentMeta.setSavedForecasterFileName( m_saveForecasterField.getText() );
    }

    if ( !m_originalMeta.equals( m_currentMeta ) ) {
      m_currentMeta.setChanged();
      changed = m_currentMeta.hasChanged();
    }

    dispose();
  }

  /**
   * Helper method to pad/truncate strings
   *
   * @param s   String to modify
   * @param pad character to pad with
   * @param len length of final string
   * @return final String
   */
  private String getFixedLengthString( String s, char pad, int len ) {

    String padded = null;
    if ( len <= 0 ) {
      return s;
    }
    // truncate?
    if ( s.length() >= len ) {
      return s.substring( 0, len );
    } else {
      char[] buf = new char[len - s.length()];
      for ( int j = 0; j < len - s.length(); j++ ) {
        buf[j] = pad;
      }
      padded = s + new String( buf );
    }

    return padded;
  }
}
