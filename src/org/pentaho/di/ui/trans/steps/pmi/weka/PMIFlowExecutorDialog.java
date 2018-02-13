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

import org.eclipse.swt.SWT;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
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
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.util.Utils;
import org.pentaho.di.core.Props;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.steps.pmi.weka.PMIFlowExecutorData;
import org.pentaho.di.trans.steps.pmi.weka.PMIFlowExecutorMeta;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.core.widget.ColumnInfo;
import org.pentaho.di.ui.core.widget.TableView;
import org.pentaho.di.ui.core.widget.TextVar;
import org.pentaho.di.ui.trans.step.BaseStepDialog;
import org.pentaho.dm.commons.ArffMeta;
import weka.core.Environment;
import weka.core.PluginManager;
import weka.core.WekaException;
import weka.gui.knowledgeflow.KFGUIConsts;
import weka.gui.knowledgeflow.KnowledgeFlowApp;
import weka.gui.knowledgeflow.MainKFPerspective;
import weka.knowledgeflow.Flow;
import weka.knowledgeflow.FlowLoader;
import weka.knowledgeflow.JSONFlowUtils;
import weka.knowledgeflow.StepManager;

import java.awt.BorderLayout;
import java.io.File;
import java.io.StringReader;
import java.util.List;

/**
 * Dialog class for the Knowledge Flow step
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 */
public class PMIFlowExecutorDialog extends BaseStepDialog implements StepDialogInterface {

  // various UI bits and pieces for the dialog

  private FormData m_fdTabFolder;

  // the tabs of the dialog
  private CTabFolder m_wTabFolder;
  private CTabItem m_wFileTab, m_wFieldsTab, m_wFlowTab;

  // Browse file button
  private Button m_wbFilename;

  // Combines text field with widget to insert environment variable
  private TextVar m_wFilename;

  private Button m_storeFlowInStepMetaData;

  // Status of where the current flow is being sourced from
  private Label m_sourceStatusLab;

  // inject step stuff (checkbox)
  private Button m_wInjectStepCheckBox;

  // inject step stuff (combo box)
  private CCombo m_wInjectStepComboBox;

  // inject event stuff (combo box)
  private CCombo m_wInjectEventComboBox;

  // pass rows through check box
  private Button m_wPassRowsThroughCheckBox;

  // output step stuff
  private CCombo m_wOutputStepComboBox;

  // output event stuff
  private CCombo m_wOutputEventComboBox;

  // Show KF editor button
  private Button m_wbKFeditor;

  // Get changes from editor button
  private Button m_wbGetEditorChanges;

  // Fields stuff
  // Fields table stuff
  private TableView m_wFields;

  // Sampling stuff
  private TextVar m_wRelationName;
  private TextVar m_wSampleSize;
  private TextVar m_wSeed;

  // Class attribute
  private Button m_wSetClassCheckBox;
  private CCombo m_wClassAttributeComboBox;

  protected final PMIFlowExecutorMeta m_currentMeta;
  protected final PMIFlowExecutorMeta m_originalMeta;

  protected final Environment m_env = Environment.getSystemWide();

  /**
   * The embedded KnowledgeFlow perspective
   */
  protected MainKFPerspective m_kfPerspective;

  protected KnowledgeFlowApp m_kfApp;

  /**
   * The currently loaded flow
   */
  protected Flow m_currentFlow;

  public PMIFlowExecutorDialog( Shell parent, Object in, TransMeta tr, String sname ) {
    super( parent, (BaseStepMeta) in, tr, sname );

    m_currentMeta = (PMIFlowExecutorMeta) in;
    m_originalMeta = (PMIFlowExecutorMeta) m_currentMeta.clone();
    // check to see if the KettleInject KF component has been registered in Weka's PluginManager
    checkKFInjectRegistered();
    m_kfApp = new KnowledgeFlowApp();

    m_kfPerspective = (MainKFPerspective) m_kfApp.getMainPerspective();
    m_kfApp.hidePerspectivesToolBar();

    // only allow one tab/flow
    m_kfPerspective.setAllowMultipleTabs( false );

    List<String> transVarsInUse = transMeta.getUsedVariables();
    for ( String varName : transVarsInUse ) {
      String varValue = transMeta.getVariable( varName, "" );
      m_env.addVariable( varName, varValue );
    }
    // pass the transformation directory through (if defined)
    String internalTransDir = transMeta.getVariable( "Internal.Transformation.Filename.Directory" );
    if ( !Utils.isEmpty( internalTransDir ) ) {
      if ( internalTransDir.contains( "://" ) ) {
        internalTransDir = internalTransDir.substring( internalTransDir.indexOf( "://" ) + 3 );
      }
      m_env.addVariable( "Internal.Transformation.Filename.Directory", internalTransDir );
    }
  }

  protected void checkKFInjectRegistered() {
    if ( !PluginManager.pluginRegistered( "weka.knowledgeflow.steps.Step", "weka.knowledgeflow.steps.KettleInject" ) ) {
      PluginManager.addPlugin( "weka.knowledgeflow.steps.Step", "weka.knowledgeflow.steps.KettleInject",
          "weka.knowledgeflow.steps.KettleInject" );
    }
  }

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
    shell.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Shell.Title" ) ); //$NON-NLS-1$

    final int middle = props.getMiddlePct();
    final int margin = Const.MARGIN;

    // Stepname line
    wlStepname = new Label( shell, SWT.RIGHT );
    wlStepname
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StepName.Label" ) ); //$NON-NLS-1$
    props.setLook( wlStepname );

    FormData fdlStepname = new FormData();
    fdlStepname.left = new FormAttachment( 0, 0 );
    fdlStepname.right = new FormAttachment( middle, -margin );
    fdlStepname.top = new FormAttachment( 0, margin );
    wlStepname.setLayoutData( fdlStepname );
    wStepname = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    wStepname.setText( stepname );
    props.setLook( wStepname );
    wStepname.addModifyListener( lsMod );

    // format the text field
    fdStepname = new FormData();
    fdStepname.left = new FormAttachment( middle, 0 );
    fdStepname.top = new FormAttachment( 0, margin );
    fdStepname.right = new FormAttachment( 100, 0 );
    wStepname.setLayoutData( fdStepname );

    m_wTabFolder = new CTabFolder( shell, SWT.BORDER );
    props.setLook( m_wTabFolder, Props.WIDGET_STYLE_TAB );
    m_wTabFolder.setSimple( false );

    // Setup the file tab
    setUpFileTab( middle, margin, lsMod );

    // Setup fields tab
    setupFieldsTab( middle, margin, lsMod );

    m_fdTabFolder = new FormData();
    m_fdTabFolder.left = new FormAttachment( 0, 0 );
    m_fdTabFolder.top = new FormAttachment( wStepname, margin );
    m_fdTabFolder.right = new FormAttachment( 100, 0 );
    m_fdTabFolder.bottom = new FormAttachment( 100, -50 );
    m_wTabFolder.setLayoutData( m_fdTabFolder );

    // Buttons inherited from BaseStepDialog
    wOK = new Button( shell, SWT.PUSH );
    wOK.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "System.Button.OK" ) );

    wCancel = new Button( shell, SWT.PUSH );
    wCancel.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "System.Button.Cancel" ) );

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

    wStepname.addSelectionListener( lsDef );

    // Detect X or ALT-F4 or something that kills this window...
    shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent e ) {
        cancel();
      }
    } );

    // Listen for the closing of the KF tab so that we can enable the
    // show KF button again
    m_wTabFolder.addCTabFolder2Listener( new CTabFolder2Adapter() {
      @Override public void close( CTabFolderEvent event ) {
        if ( event.item.equals( m_wFlowTab ) ) {
          m_wbKFeditor.setEnabled( true );
          m_wbGetEditorChanges.setEnabled( false );
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

  private void getData() {
    if ( !Utils.isEmpty( m_currentMeta.getSerializedFlowFileName() ) ) {
      m_wFilename.setText( m_currentMeta.getSerializedFlowFileName() );
      m_sourceStatusLab
          .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedFromFile" ) );
    } else {
      if ( !Utils.isEmpty( m_currentMeta.getFlow() ) ) {
        m_sourceStatusLab.setText(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedInternally" ) );
      } else {
        m_sourceStatusLab.setText(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.NoFlowAvailable" ) );
      }
    }

    m_storeFlowInStepMetaData.setSelection( m_currentMeta.getStoreFlowInStepMetaData() );
    boolean flowOK = false;
    if ( !Utils.isEmpty( m_currentMeta.getSerializedFlowFileName() ) ) {
      // try loading the flow
      flowOK = loadFlow();
    }

    String flowJSON = m_currentMeta.getFlow();
    if ( !Utils.isEmpty( flowJSON ) ) {
      try {
        m_currentFlow = JSONFlowUtils.JSONToFlow( flowJSON, false );
      } catch ( WekaException e ) {
        // try legacy flow
        try {
          FlowLoader legacyLoader = Flow.getFlowLoader( "kfml", null );
          m_currentFlow = Flow.loadFlow( new StringReader( flowJSON ), legacyLoader );
        } catch ( WekaException ex ) {
          new ErrorDialog( shell, stepname,
              BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Error.ErrorDeserializingFlow" ), ex );
          return;
        }
      }

      m_kfPerspective.getCurrentLayout().setFlow( m_currentFlow );
      flowOK = true;
    }

    // all other kf and connection stuff
    boolean checksAndCombosEnabled = false;
    if ( Utils.isEmpty( m_currentMeta.getInjectStepName() ) ) {
      m_wInjectStepCheckBox.setSelection( false );
    } else {
      m_wInjectStepCheckBox.setSelection( true );
      m_wInjectStepComboBox.setEnabled( true );
      m_wInjectEventComboBox.setEnabled( true );
      m_wInjectStepCheckBox.setEnabled( true );
      checksAndCombosEnabled = true;
      setupInjectStepNames();
      if ( !Utils.isEmpty( m_currentMeta.getInjectStepName() ) ) {
        m_wInjectStepComboBox.setText( m_currentMeta.getInjectStepName() );
      }

      if ( !Utils.isEmpty( m_currentMeta.getInjectConnectionName() ) ) {
        m_wInjectEventComboBox.setText( m_currentMeta.getInjectConnectionName() );
      }
    }

    if ( m_currentMeta.getPassRowsThrough() ) {
      m_wPassRowsThroughCheckBox.setSelection( true );
    } else {
      m_wPassRowsThroughCheckBox.setSelection( false );
      m_wOutputStepComboBox.setEnabled( true );
      m_wOutputEventComboBox.setEnabled( true );
      setupOutputStepNames();
      if ( !Utils.isEmpty( m_currentMeta.getOutputStepName() ) ) {
        m_wOutputStepComboBox.setText( m_currentMeta.getOutputStepName() );
      }

      if ( !Utils.isEmpty( m_currentMeta.getOutputConnectionName() ) ) {
        m_wOutputEventComboBox.setText( m_currentMeta.getOutputConnectionName() );
      }
    }

    if ( flowOK && !checksAndCombosEnabled ) {
      m_wInjectStepComboBox.setEnabled( true );
      m_wInjectEventComboBox.setEnabled( true );
      m_wInjectStepCheckBox.setEnabled( true );
      setupInjectStepNames();
    }

    arffMetasToFields( m_currentMeta.getInjectFields() );

    // fields stuff
    m_wRelationName.setText( m_currentMeta.getSampleRelationName() );
    m_wSampleSize.setText( m_currentMeta.getSampleSize() );
    m_wSeed.setText( m_currentMeta.getRandomSeed() );
    m_wRelationName.setEnabled( m_wInjectStepCheckBox.getSelection() );
    m_wSampleSize.setEnabled( m_wInjectStepCheckBox.getSelection() );
    m_wSeed.setEnabled( m_wInjectStepCheckBox.getSelection() );
    if ( m_wInjectStepCheckBox.getSelection() ) {
      m_wSetClassCheckBox.setEnabled( true );
    } else {
      m_wSetClassCheckBox.setEnabled( false );
      m_wSetClassCheckBox.setSelection( false );
    }

    m_wSetClassCheckBox.setSelection( m_currentMeta.getSetClass() );
    if ( m_wInjectStepCheckBox.getSelection() && m_currentMeta.getSetClass() ) {
      ArffMeta[] tempAM = fieldsToArffMetas();
      setUpClassAttributeNames( tempAM );
      m_wClassAttributeComboBox.setEnabled( true );
      if ( m_currentMeta.getClassAttributeName() != null ) {
        m_wClassAttributeComboBox.setText( m_currentMeta.getClassAttributeName() );
      }
    }
  }

  private void ok() {
    stepname = wStepname.getText(); // return value

    m_currentMeta.setStoreFlowInStepMetaData( m_storeFlowInStepMetaData.getSelection() );

    // Filename stuff
    if ( !org.pentaho.di.core.util.Utils.isEmpty( m_wFilename.getText() ) && !m_currentMeta
        .getStoreFlowInStepMetaData() ) {
      m_currentMeta.setSerializedFlowFileName( m_wFilename.getText() );
    } else {
      m_currentMeta.setSerializedFlowFileName( null );
    }

    // KF step names and events
    if ( m_wInjectStepCheckBox.getSelection() ) {
      m_currentMeta.setInjectStepName( m_wInjectStepComboBox.getText() );
      m_currentMeta.setInjectConnectionName( m_wInjectEventComboBox.getText() );
      m_currentMeta.setStreamData( m_currentMeta.getInjectConnectionName().equals( StepManager.CON_INSTANCE ) );
    } else {
      m_currentMeta.setInjectStepName( null );
      m_currentMeta.setInjectConnectionName( null );
    }

    m_currentMeta.setPassRowsThrough( m_wPassRowsThroughCheckBox.getSelection() );
    if ( !m_currentMeta.getPassRowsThrough() ) {
      m_currentMeta.setOutputStepName( m_wOutputStepComboBox.getText() );
      m_currentMeta.setOutputConnectionName( m_wOutputEventComboBox.getText() );
    } else {
      m_currentMeta.setOutputStepName( null );
      m_currentMeta.setOutputConnectionName( null );
    }

    m_currentMeta.setInjectFields( fieldsToArffMetas() );

    // Reservoir sampling stuff
    if ( !org.pentaho.di.core.util.Utils.isEmpty( m_wRelationName.getText() ) ) {
      m_currentMeta.setSampleRelationName( transMeta.environmentSubstitute( m_wRelationName.getText() ) );
    }

    m_currentMeta.setSampleSize( m_wSampleSize.getText() );
    m_currentMeta.setRandomSeed( m_wSeed.getText() );

    // Class attribute
    m_currentMeta.setSetClass( m_wSetClassCheckBox.getSelection() );
    if ( m_wSetClassCheckBox.getSelection() ) {
      m_currentMeta.setClassAttributeName( m_wClassAttributeComboBox.getText() );
    }

    // if there is no filename then
    // see if we can get the flow from the embedded KF editor
    if ( org.pentaho.di.core.util.Utils.isEmpty( m_currentMeta.getSerializedFlowFileName() ) && m_currentMeta
        .getStoreFlowInStepMetaData() ) {
      try {
        if ( m_currentFlow != null ) {
          String flowJson = m_currentFlow.toJSON();
          m_currentMeta.setFlow( flowJson );
        }

      } catch ( Exception ex ) {
        log.logError( BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Error.ProblemExtractingKFFromEmbeddedEditor" ) );
        m_currentMeta.setFlow( null );
      }
    }

    if ( !m_originalMeta.equals( m_currentMeta ) ) {
      m_currentMeta.setChanged();
      changed = m_currentMeta.hasChanged();
    }

    dispose();
  }

  private void cancel() {
    stepname = null;
    m_currentMeta.setChanged( changed );
    // m_currentMeta.setModel(null);
    // revert to original model
    m_currentMeta.setFlow( m_originalMeta.getFlow() );
    m_currentMeta.setInjectFields( m_originalMeta.getInjectFields() );
    m_currentMeta.setSerializedFlowFileName( m_originalMeta.getSerializedFlowFileName() );
    dispose();
  }

  private void setUpFileTab( final int middle, final int margin, ModifyListener lsMod ) {
    // Start of the file tab
    m_wFileTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wFileTab.setText(
        BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.FileTab.TabTitle" ) ); //$NON-NLS-1$

    Composite wFileComp = new Composite( m_wTabFolder, SWT.NONE );
    props.setLook( wFileComp );

    FormLayout fileLayout = new FormLayout();
    fileLayout.marginWidth = 3;
    fileLayout.marginHeight = 3;
    wFileComp.setLayout( fileLayout );

    // Filename line
    Label wlFilename = new Label( wFileComp, SWT.RIGHT );
    wlFilename
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Filename.Label" ) ); //$NON-NLS-1$
    props.setLook( wlFilename );
    FormData fdlFilename = new FormData();
    fdlFilename.left = new FormAttachment( 0, 0 );
    fdlFilename.top = new FormAttachment( 0, margin );
    fdlFilename.right = new FormAttachment( middle, -margin );
    wlFilename.setLayoutData( fdlFilename );

    // file browse button
    m_wbFilename = new Button( wFileComp, SWT.PUSH | SWT.CENTER );
    props.setLook( m_wbFilename );
    m_wbFilename.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "System.Button.Browse" ) ); //$NON-NLS-1$
    FormData fdbFilename = new FormData();
    fdbFilename.right = new FormAttachment( 100, 0 );
    fdbFilename.top = new FormAttachment( 0, 0 );
    m_wbFilename.setLayoutData( fdbFilename );

    // combined text field and env variable widget
    m_wFilename = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_wFilename );
    m_wFilename.addModifyListener( lsMod );
    FormData fdFilename = new FormData();
    fdFilename.left = new FormAttachment( middle, 0 );
    fdFilename.top = new FormAttachment( 0, margin );
    fdFilename.right = new FormAttachment( m_wbFilename, -margin );
    m_wFilename.setLayoutData( fdFilename );

    FormData fdFileComp = new FormData();
    fdFileComp.left = new FormAttachment( 0, 0 );
    fdFileComp.top = new FormAttachment( 0, 0 );
    fdFileComp.right = new FormAttachment( 100, 0 );
    fdFileComp.bottom = new FormAttachment( 100, 0 );
    wFileComp.setLayoutData( fdFileComp );

    wFileComp.layout();
    m_wFileTab.setControl( wFileComp );

    Label storeFlowInMetaLab = new Label( wFileComp, SWT.RIGHT );
    // TODO - shift this to messages
    storeFlowInMetaLab.setText( "Store flow in step meta data" );
    props.setLook( storeFlowInMetaLab );
    FormData fd = new FormData();
    fd.top = new FormAttachment( m_wFilename, margin );
    fd.right = new FormAttachment( middle, -margin );
    fd.left = new FormAttachment( 0, 0 );
    storeFlowInMetaLab.setLayoutData( fd );

    m_storeFlowInStepMetaData = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_storeFlowInStepMetaData );
    fd = new FormData();
    fd.top = new FormAttachment( m_wFilename, margin );
    fd.left = new FormAttachment( middle, 0 );
    fd.right = new FormAttachment( 100, 0 );
    m_storeFlowInStepMetaData.setLayoutData( fd );

    m_storeFlowInStepMetaData.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        if ( m_storeFlowInStepMetaData.getSelection() ) {
          m_sourceStatusLab.setText(
              BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedInternally" ) );
          m_wInjectStepCheckBox.setEnabled( true );

          if ( !m_wbKFeditor.isEnabled() ) {
            m_wbGetEditorChanges.setEnabled( true );
          }
        } else {
          if ( !org.pentaho.di.core.util.Utils.isEmpty( m_wFilename.getText() ) ) {
            m_sourceStatusLab.setText(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedFromFile" ) );
            m_wInjectStepCheckBox.setEnabled( true );
          } else {
            m_sourceStatusLab.setText(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.NoFlowAvailable" ) );
            m_wInjectStepCheckBox.setEnabled( false );
          }

          m_wbGetEditorChanges.setEnabled( false );
        }
      }
    } );

    Group statusG = new Group( wFileComp, SWT.SHADOW_ETCHED_IN );
    statusG.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Status.Title" ) );
    FormLayout statusGLayout = new FormLayout();
    statusGLayout.marginWidth = 3;
    statusGLayout.marginHeight = 3;
    statusG.setLayout( statusGLayout );
    props.setLook( statusG );
    m_sourceStatusLab = new Label( statusG, SWT.RIGHT );
    m_sourceStatusLab.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.Label" ) );
    props.setLook( m_sourceStatusLab );
    FormData fdlSourceStatusLab = new FormData();
    fdlSourceStatusLab.top = new FormAttachment( 0, margin );
    fdlSourceStatusLab.left = new FormAttachment( 0, 0 );
    fdlSourceStatusLab.right = new FormAttachment( 100, -margin );
    m_sourceStatusLab.setLayoutData( fdlSourceStatusLab );

    FormData statusGD = new FormData();
    statusGD.top = new FormAttachment( m_storeFlowInStepMetaData, margin );
    statusGD.right = new FormAttachment( 100, 0 );
    statusGD.left = new FormAttachment( middle, 0 );
    statusG.setLayoutData( statusGD );

    Label wInjectStepCheckBoxLab = new Label( wFileComp, SWT.RIGHT );
    wInjectStepCheckBoxLab
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.InjectStepCheckBox.Label" ) );
    props.setLook( wInjectStepCheckBoxLab );
    FormData fdlInjectStepCheckBoxLab = new FormData();
    fdlInjectStepCheckBoxLab.left = new FormAttachment( 0, 0 );
    fdlInjectStepCheckBoxLab.top = new FormAttachment( statusG, margin );
    fdlInjectStepCheckBoxLab.right = new FormAttachment( middle, -margin );
    wInjectStepCheckBoxLab.setLayoutData( fdlInjectStepCheckBoxLab );

    m_wInjectStepCheckBox = new Button( wFileComp, SWT.CHECK );
    m_wInjectStepCheckBox.setEnabled( false );
    props.setLook( m_wInjectStepCheckBox );
    FormData fdInjectStepCheckBox = new FormData();
    fdInjectStepCheckBox.left = new FormAttachment( middle, 0 );
    fdInjectStepCheckBox.top = new FormAttachment( statusG, margin );
    fdInjectStepCheckBox.right = new FormAttachment( 100, 0 );
    m_wInjectStepCheckBox.setLayoutData( fdInjectStepCheckBox );

    Label wInjectStepComboBoxLab = new Label( wFileComp, SWT.RIGHT );
    wInjectStepComboBoxLab
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.InjectStepCombo.Label" ) );
    props.setLook( wInjectStepComboBoxLab );
    FormData fdlInjectStepComboBoxLab = new FormData();
    fdlInjectStepComboBoxLab.left = new FormAttachment( 0, 0 );
    fdlInjectStepComboBoxLab.top = new FormAttachment( m_wInjectStepCheckBox, margin );
    fdlInjectStepComboBoxLab.right = new FormAttachment( middle, -margin );
    wInjectStepComboBoxLab.setLayoutData( fdlInjectStepComboBoxLab );

    m_wInjectStepComboBox = new CCombo( wFileComp, SWT.BORDER | SWT.READ_ONLY );
    m_wInjectStepComboBox
        .setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.InjectStep.ToolTip" ) );
    props.setLook( m_wInjectStepComboBox );

    m_wInjectStepComboBox.addModifyListener( new ModifyListener() {
      @Override public void modifyText( ModifyEvent e ) {
        m_currentMeta.setChanged();
        setupInjectConnNames( m_wInjectStepComboBox.getText() );
      }
    } );
    FormData fdInjectStepComboBox = new FormData();
    fdInjectStepComboBox.left = new FormAttachment( middle, 0 );
    fdInjectStepComboBox.top = new FormAttachment( m_wInjectStepCheckBox, margin );
    fdInjectStepComboBox.right = new FormAttachment( 100, 0 );
    m_wInjectStepComboBox.setLayoutData( fdInjectStepComboBox );
    m_wInjectStepComboBox.setEnabled( false );

    Label wInjectEventComboBoxLab = new Label( wFileComp, SWT.RIGHT );
    wInjectEventComboBoxLab
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.InjectConnCombo.Label" ) );
    props.setLook( wInjectEventComboBoxLab );
    FormData fdlInjectEventComboBoxLab = new FormData();
    fdlInjectEventComboBoxLab.left = new FormAttachment( 0, 0 );
    fdlInjectEventComboBoxLab.top = new FormAttachment( m_wInjectStepComboBox, margin );
    fdlInjectEventComboBoxLab.right = new FormAttachment( middle, -margin );
    wInjectEventComboBoxLab.setLayoutData( fdlInjectEventComboBoxLab );

    m_wInjectEventComboBox = new CCombo( wFileComp, SWT.BORDER | SWT.READ_ONLY );
    m_wInjectEventComboBox
        .setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.InjectConn.ToolTip" ) );
    props.setLook( m_wInjectEventComboBox );
    m_wInjectEventComboBox.addModifyListener( lsMod );
    FormData fdInjectEventComboBox = new FormData();
    fdInjectEventComboBox.left = new FormAttachment( middle, 0 );
    fdInjectEventComboBox.top = new FormAttachment( m_wInjectStepComboBox, margin );
    fdInjectEventComboBox.right = new FormAttachment( 100, 0 );
    m_wInjectEventComboBox.setLayoutData( fdInjectEventComboBox );
    m_wInjectEventComboBox.setEnabled( false );

    m_wInjectStepCheckBox.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        setupInjectStepNames();
        if ( !m_wPassRowsThroughCheckBox.getSelection() ) {
          setupOutputStepNames();
        }
        m_currentMeta.setChanged();
        m_wInjectStepComboBox.setEnabled( m_wInjectStepCheckBox.getSelection() );
        m_wInjectEventComboBox.setEnabled( m_wInjectStepCheckBox.getSelection() );

        m_wOutputStepComboBox
            .setEnabled( m_wInjectStepCheckBox.getSelection() && !m_wPassRowsThroughCheckBox.getSelection() );
        m_wOutputEventComboBox
            .setEnabled( m_wInjectStepCheckBox.getSelection() && !m_wPassRowsThroughCheckBox.getSelection() );
        // Sampling stuff
        m_wRelationName.setEnabled( m_wInjectStepCheckBox.getSelection() );
        m_wSampleSize.setEnabled( m_wInjectStepCheckBox.getSelection() );
        m_wSeed.setEnabled( m_wInjectStepCheckBox.getSelection() );
        m_wSetClassCheckBox.setEnabled( m_wInjectStepCheckBox.getSelection() );
        if ( !m_wInjectStepCheckBox.getSelection() ) {
          m_wSetClassCheckBox.setSelection( false );
          m_wClassAttributeComboBox.setEnabled( false );
          // m_wClassAttributeComboBox.setText("");
        }
      }
    } );

    Label wPassRowsThroughLab = new Label( wFileComp, SWT.RIGHT );
    wPassRowsThroughLab
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.PassRowsThrough.Label" ) );
    props.setLook( wPassRowsThroughLab );
    FormData fdlPassRowsThroughLab = new FormData();
    fdlPassRowsThroughLab.left = new FormAttachment( 0, 0 );
    fdlPassRowsThroughLab.top = new FormAttachment( m_wInjectEventComboBox, margin );
    fdlPassRowsThroughLab.right = new FormAttachment( middle, -margin );
    wPassRowsThroughLab.setLayoutData( fdlPassRowsThroughLab );

    m_wPassRowsThroughCheckBox = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_wPassRowsThroughCheckBox );
    FormData fdPassRowsThroughCheckBox = new FormData();
    fdPassRowsThroughCheckBox.left = new FormAttachment( middle, 0 );
    fdPassRowsThroughCheckBox.top = new FormAttachment( m_wInjectEventComboBox, margin );
    fdPassRowsThroughCheckBox.right = new FormAttachment( 100, 0 );
    m_wPassRowsThroughCheckBox.setLayoutData( fdPassRowsThroughCheckBox );
    m_wPassRowsThroughCheckBox.setSelection( true );

    Label wOutputStepComboBoxLab = new Label( wFileComp, SWT.RIGHT );
    wOutputStepComboBoxLab
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputStepCombo.Label" ) );
    props.setLook( wOutputStepComboBoxLab );
    FormData fdlOutputStepComboBoxLab = new FormData();
    fdlOutputStepComboBoxLab.left = new FormAttachment( 0, 0 );
    fdlOutputStepComboBoxLab.top = new FormAttachment( m_wPassRowsThroughCheckBox, margin );
    fdlOutputStepComboBoxLab.right = new FormAttachment( middle, -margin );
    wOutputStepComboBoxLab.setLayoutData( fdlOutputStepComboBoxLab );

    m_wOutputStepComboBox = new CCombo( wFileComp, SWT.BORDER | SWT.READ_ONLY );
    m_wOutputStepComboBox
        .setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputStep.ToolTip" ) );
    props.setLook( m_wOutputStepComboBox );
    m_wOutputStepComboBox.addModifyListener( new ModifyListener() {
      @Override public void modifyText( ModifyEvent e ) {
        m_currentMeta.setChanged();
        if ( !org.pentaho.di.core.util.Utils.isEmpty( m_wOutputStepComboBox.getText() ) ) {
          setupOutputConnNames( m_wOutputStepComboBox.getText() );
        }
      }
    } );

    FormData fdOutputStepComboBox = new FormData();
    fdOutputStepComboBox.left = new FormAttachment( middle, 0 );
    fdOutputStepComboBox.top = new FormAttachment( m_wPassRowsThroughCheckBox, margin );
    fdOutputStepComboBox.right = new FormAttachment( 100, 0 );
    m_wOutputStepComboBox.setLayoutData( fdOutputStepComboBox );
    m_wOutputStepComboBox.setEnabled( false );

    Label wOutputEventComboBoxLab = new Label( wFileComp, SWT.RIGHT );
    wOutputEventComboBoxLab
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputConnCombo.Label" ) );
    props.setLook( wOutputEventComboBoxLab );
    FormData fdlOutputEventComboBoxLab = new FormData();
    fdlOutputEventComboBoxLab.left = new FormAttachment( 0, 0 );
    fdlOutputEventComboBoxLab.top = new FormAttachment( m_wOutputStepComboBox, margin );
    fdlOutputEventComboBoxLab.right = new FormAttachment( middle, -margin );
    wOutputEventComboBoxLab.setLayoutData( fdlOutputEventComboBoxLab );

    m_wOutputEventComboBox = new CCombo( wFileComp, SWT.BORDER | SWT.READ_ONLY );
    m_wOutputEventComboBox
        .setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputConn.ToolTip" ) );
    props.setLook( m_wOutputEventComboBox );
    m_wOutputEventComboBox.addModifyListener( lsMod );
    FormData fdOutputEventComboBox = new FormData();
    fdOutputEventComboBox.left = new FormAttachment( middle, 0 );
    fdOutputEventComboBox.top = new FormAttachment( m_wOutputStepComboBox, margin );
    fdOutputEventComboBox.right = new FormAttachment( 100, 0 );
    m_wOutputEventComboBox.setLayoutData( fdOutputEventComboBox );
    m_wOutputEventComboBox.setEnabled( false );

    m_wPassRowsThroughCheckBox.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        m_currentMeta.setChanged();
        m_wOutputStepComboBox.setEnabled( !m_wPassRowsThroughCheckBox.getSelection() );
        m_wOutputEventComboBox.setEnabled( !m_wPassRowsThroughCheckBox.getSelection() );
        if ( !m_wPassRowsThroughCheckBox.getSelection() ) {
          setupOutputStepNames();
        }
      }
    } );

    m_wbKFeditor = new Button( wFileComp, SWT.PUSH | SWT.CENTER );
    props.setLook( m_wbKFeditor );
    m_wbKFeditor.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.KFeditor.Label" ) );
    FormData fdbKFeditor = new FormData();
    fdbKFeditor.left = new FormAttachment( middle, 0 );
    fdbKFeditor.right = new FormAttachment( 100, 0 );
    fdbKFeditor.top = new FormAttachment( m_wOutputEventComboBox, margin );
    m_wbKFeditor.setLayoutData( fdbKFeditor );

    m_wbGetEditorChanges = new Button( wFileComp, SWT.PUSH | SWT.CENTER );
    props.setLook( m_wbGetEditorChanges );
    m_wbGetEditorChanges
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.GetEditorChanges.Label" ) );
    FormData fdbGetEditorChanges = new FormData();
    fdbGetEditorChanges.left = new FormAttachment( middle, 0 );
    fdbGetEditorChanges.right = new FormAttachment( 100, 0 );
    fdbGetEditorChanges.top = new FormAttachment( m_wbKFeditor, margin );
    m_wbGetEditorChanges.setLayoutData( fdbGetEditorChanges );
    m_wbGetEditorChanges.setEnabled( false );

    // Some more listeners ----------------

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
        if ( !loadFlow() ) {
          if ( !org.pentaho.di.core.util.Utils.isEmpty( m_wFilename.getText() ) ) {
            log.logError(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.ProblemLoadingFile" ) );
            m_sourceStatusLab.setText(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.ProblemLoadingFile" ) );
          } else {
            m_sourceStatusLab.setText(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedInternally" ) );
            m_wInjectStepCheckBox.setEnabled( true );
          }
        } else {
          m_sourceStatusLab.setText(
              BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedFromFile" ) );
          m_wInjectStepCheckBox.setEnabled( true );
        }
      }
    } );

    m_wbFilename.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        FileDialog dialog = new FileDialog( shell, SWT.OPEN );
        String[] extensions = null;
        String[] filterNames = null;

        extensions = new String[3];
        filterNames = new String[3];

        extensions[0] = "*.kf"; //$NON-NLS-1$
        filterNames[0] = BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.FileType.FlowFileJSON" );
        extensions[1] = "*.kfml";
        filterNames[01] =
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.FileType.FlowFileLegacyXML" );
        extensions[2] = "*";
        filterNames[2] = BaseMessages.getString( PMIFlowExecutorMeta.PKG, "System.FileType.AllFiles" );

        dialog.setFilterExtensions( extensions );
        if ( m_wFilename.getText() != null ) {
          dialog.setFileName( transMeta.environmentSubstitute( m_wFilename.getText() ) );
        }
        dialog.setFilterNames( filterNames );

        if ( dialog.open() != null ) {

          m_wFilename.setText(
              dialog.getFilterPath() + System.getProperty( "file.separator" ) + dialog.getFileName() ); //$NON-NLS-1$

          // try to load model file and display model
          if ( !loadFlow() ) {
            if ( !org.pentaho.di.core.util.Utils.isEmpty( m_wFilename.getText() ) ) {
              log.logError(
                  BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.ProblemLoadingFile" ) );
              m_sourceStatusLab.setText(
                  BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.ProblemLoadingFile" ) );
            } else {
              m_sourceStatusLab.setText(
                  BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedInternally" ) );
              m_wInjectStepCheckBox.setEnabled( true );
            }
          } else {
            m_sourceStatusLab.setText(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedFromFile" ) );
            m_wInjectStepCheckBox.setEnabled( true );
          }
        }
      }
    } );

    m_wbKFeditor.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        m_wbKFeditor.setEnabled( false );

        setupFlowTab( middle, margin );

        if ( m_currentFlow != null ) {
          m_kfPerspective.getCurrentLayout().setEnvironment( m_env );
          m_kfPerspective.getCurrentLayout().setFlow( m_currentFlow );
        }

        // enable the get changes from editor button (if there is nothing in
        // the filename box or the store flow in meta check box is selected)
        if ( org.pentaho.di.core.util.Utils.isEmpty( m_wFilename.getText() ) || m_storeFlowInStepMetaData
            .getSelection() ) {
          m_wbGetEditorChanges.setEnabled( true );
        }
      }
    } );

    m_wbGetEditorChanges.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        try {
          m_currentFlow = m_kfPerspective.getCurrentLayout().getFlow();
          if ( m_currentFlow != null && m_currentFlow.size() > 0 ) {
            setupInjectStepNames();

            if ( !m_wPassRowsThroughCheckBox.getSelection() ) {
              setupOutputStepNames();
            } else {
              m_wOutputStepComboBox.removeAll();
              m_wOutputEventComboBox.removeAll();
            }
            m_sourceStatusLab.setText(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.SourcedInternally" ) );
            m_wInjectStepCheckBox.setEnabled( true );
          } else {
            m_currentMeta.setFlow( null );
            m_sourceStatusLab.setText(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.NoFlowAvailable" ) );
          }

          /* KnowledgeFlowApp embedded = KnowledgeFlowApp.getSingleton();
          String xml = embedded.getFlowXML();
          Vector<Vector<?>> flow = KFData.xmlToFlow( xml );
          Vector<?> beans = flow.elementAt( 0 );
          if ( beans.size() > 0 ) {
            // m_currentMeta.setFlow(flow);
            m_currentFlow = flow;

            embedded.setFlow( flow );
            setUpInjectStepNames();
            if ( !m_wPassRowsThroughCheckBox.getSelection() ) {
              setUpOutputStepNames();
            } else {
              m_wOutputStepComboBox.removeAll();
              m_wOutputEventComboBox.removeAll();
            }
            m_sourceStatusLab.setText( BaseMessages.getString( KFMeta.PKG, "KnowledgeFlowDialog.StatusLab
            .SourcedInternally" ) ); //$NON-NLS-1$
            m_wInjectStepCheckBox.setEnabled( true );
          } else {
            m_currentMeta.setFlow( null );
            m_sourceStatusLab.setText( BaseMessages.getString( KFMeta.PKG, "KnowledgeFlowDialog.StatusLab
            .NoFlowAvailable" ) ); //$NON-NLS-1$
          } */
        } catch ( Exception ex ) {
          log.logError( BaseMessages
              .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Error.ProblemExtractingKFFromEmbeddedEditor" ) );
          m_sourceStatusLab.setText(
              BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.StatusLab.NoFlowAvailable" ) );
        }
      }
    } );
  }

  private void setupInjectConnNames( String stepName ) {
    if ( m_currentFlow != null ) {
      String old = m_wInjectEventComboBox.getText();
      m_wInjectEventComboBox.removeAll();
      List<String> connNames = PMIFlowExecutorData.getInputConnectionsForNamedStep( m_currentFlow, stepName );
      for ( String conn : connNames ) {
        m_wInjectEventComboBox.add( conn );
      }
      m_wInjectEventComboBox.select( 0 );
      if ( !org.pentaho.di.core.util.Utils.isEmpty( old ) && m_wInjectEventComboBox.indexOf( old ) >= 0 ) {
        m_wInjectEventComboBox.setText( old );
      }
    }
  }

  private void setupInjectStepNames() {
    String old = m_wInjectStepComboBox.getText();
    List<String> stepNames = PMIFlowExecutorData.getAllInjectStepNames( m_currentFlow );
    m_wInjectStepComboBox.removeAll();
    for ( String stepN : stepNames ) {
      m_wInjectStepComboBox.add( stepN );
    }

    // restore the old value (if still valid)
    if ( !org.pentaho.di.core.util.Utils.isEmpty( old ) && m_wInjectStepComboBox.indexOf( old ) >= 0 ) {
      m_wInjectStepComboBox.setText( old );
    }
  }

  private void setupOutputStepNames() {
    String old = m_wOutputStepComboBox.getText();
    if ( m_currentFlow != null ) {
      List<String> stepNames = PMIFlowExecutorData.getAllAllowableOutputStepNames( m_currentFlow );
      m_wOutputStepComboBox.removeAll();
      for ( String stepN : stepNames ) {
        m_wOutputStepComboBox.add( stepN );
      }
    }

    // restore the old value (if still valid)
    if ( !org.pentaho.di.core.util.Utils.isEmpty( old ) && m_wOutputStepComboBox.indexOf( old ) >= 0 ) {
      m_wOutputStepComboBox.setText( old );
    }
  }

  private void setupOutputConnNames( String stepName ) {
    String old = m_wOutputEventComboBox.getText();
    if ( m_currentFlow != null ) {
      m_wOutputEventComboBox.removeAll();
      List<String> outputConnNames = PMIFlowExecutorData.getAllAllowableConnNamesForStep( m_currentFlow, stepName );
      for ( String connName : outputConnNames ) {
        m_wOutputEventComboBox.add( connName );
      }
    }

    // restore the old value (if still valid)
    if ( !org.pentaho.di.core.util.Utils.isEmpty( old ) && m_wOutputEventComboBox.indexOf( old ) >= 0 ) {
      m_wOutputEventComboBox.setText( old );
    }
  }

  private void setupFlowTab( int middle, int margin ) {
    m_wFlowTab = new CTabItem( m_wTabFolder, SWT.CLOSE );
    m_wFlowTab.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.FlowTab.TabTitle" ) );

    FormLayout flowLayout = new FormLayout();
    flowLayout.marginWidth = 3;
    flowLayout.marginHeight = 3;

    Composite wFlowComp = new Composite( m_wTabFolder, SWT.EMBEDDED );
    props.setLook( wFlowComp );
    wFlowComp.setLayout( flowLayout );

    java.awt.Dimension d = new java.awt.Dimension( 900, 600 );
    m_kfApp.setMinimumSize( d );

    java.awt.Panel tempPanel = new java.awt.Panel();
    tempPanel.setLayout( new BorderLayout() );
    tempPanel.add( m_kfApp, BorderLayout.CENTER );
    tempPanel.setMinimumSize( d );

    java.awt.Frame embedded = SWT_AWT.new_Frame( wFlowComp );
    embedded.setLayout( new BorderLayout() );
    embedded.add( tempPanel, BorderLayout.CENTER );

    FormData fdFlowComp = new FormData();
    fdFlowComp.left = new FormAttachment( 0, 0 );
    fdFlowComp.top = new FormAttachment( 0, 0 );
    fdFlowComp.right = new FormAttachment( 100, 0 );
    fdFlowComp.bottom = new FormAttachment( 100, 0 );
    wFlowComp.setLayoutData( fdFlowComp );
    wFlowComp.layout();
    m_wFlowTab.setControl( wFlowComp );
  }

  private void setupFieldsTab( int middle, int margin, ModifyListener lsMod ) {
    m_wFieldsTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wFieldsTab.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.FieldsTab.TabTitle" ) );

    FormLayout fieldsLayout = new FormLayout();
    fieldsLayout.marginWidth = 3;
    fieldsLayout.marginHeight = 3;

    Composite wFieldsComp = new Composite( m_wTabFolder, SWT.NONE );
    props.setLook( wFieldsComp );
    wFieldsComp.setLayout( fieldsLayout );

    Label wlFields = new Label( wFieldsComp, SWT.NONE );
    wlFields.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.FieldsTab.Label" ) );
    props.setLook( wlFields );
    FormData fdlFields = new FormData();
    fdlFields.left = new FormAttachment( 0, 0 );
    fdlFields.top = new FormAttachment( 0, margin );
    wlFields.setLayoutData( fdlFields );

    final int fieldsRows = 5;

    ColumnInfo[]
        colinf =
        new ColumnInfo[] { new ColumnInfo(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputFieldsColumn.Name" ),
            ColumnInfo.COLUMN_TYPE_TEXT, false ), new ColumnInfo(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputFieldsColumn.KettleType" ),
            ColumnInfo.COLUMN_TYPE_TEXT, false ), new ColumnInfo(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputFieldsColumn.ArffType" ),
            ColumnInfo.COLUMN_TYPE_CCOMBO, true ), new ColumnInfo( BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.OutputFieldsColumn.NomValsOrDateFormat" ),
            ColumnInfo.COLUMN_TYPE_TEXT, false ) };
    colinf[0].setReadOnly( true );
    colinf[1].setReadOnly( true );
    colinf[2].setReadOnly( false );
    colinf[3].setReadOnly( false );

    colinf[2].setComboValues( new String[] { "Numeric", "Nominal", "Date", "String" } );

    m_wFields =
        new TableView( transMeta, wFieldsComp, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI, colinf, fieldsRows, lsMod,
            props );

    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment( 0, 0 );
    fdFields.top = new FormAttachment( wlFields, margin );
    fdFields.right = new FormAttachment( 100, 0 );
    fdFields.bottom = new FormAttachment( 100, -200 );
    m_wFields.setLayoutData( fdFields );

    wGet = new Button( wFieldsComp, SWT.PUSH );
    wGet.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "System.Button.GetFields" ) );
    wGet.setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "System.Tooltip.GetFields" ) );
    FormData temp = new FormData();
    temp.left = new FormAttachment( 0, 0 );
    temp.right = new FormAttachment( middle, -margin );
    temp.top = new FormAttachment( m_wFields, margin );
    wGet.setLayoutData( temp );

    lsGet = new Listener() {
      @Override public void handleEvent( Event e ) {
        ArffMeta[] tempAM = setupArffMetas();
        arffMetasToFields( tempAM );
        // ArffMeta[] tempAM = fieldsToArffMe
        setUpClassAttributeNames( tempAM );
      }
    };
    wGet.addListener( SWT.Selection, lsGet );

    wGet.addListener( SWT.Selection, lsGet );

    Label wlRelationName = new Label( wFieldsComp, SWT.RIGHT );
    wlRelationName.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.RelationName.Label" ) );
    props.setLook( wlRelationName );

    FormData fdlRelationName = new FormData();
    fdlRelationName.left = new FormAttachment( 0, 0 );
    fdlRelationName.right = new FormAttachment( middle, -margin );
    fdlRelationName.top = new FormAttachment( wGet, margin );
    wlRelationName.setLayoutData( fdlRelationName );

    m_wRelationName = new TextVar( transMeta, wFieldsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_wRelationName );
    m_wRelationName.addModifyListener( lsMod );
    m_wRelationName.setText( "" + m_originalMeta.getSampleRelationName() ); //$NON-NLS-1$
    FormData fdRelationName = new FormData();
    fdRelationName.left = new FormAttachment( wlRelationName, margin );
    fdRelationName.right = new FormAttachment( 100, -margin );
    fdRelationName.top = new FormAttachment( wGet, margin );
    m_wRelationName.setLayoutData( fdRelationName );

    Label wlSampleSize = new Label( wFieldsComp, SWT.RIGHT );
    wlSampleSize.setText(
        BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.SampleSize.Label" ) ); //$NON-NLS-1$
    props.setLook( wlSampleSize );

    FormData fdlSampleSize = new FormData();
    fdlSampleSize.left = new FormAttachment( 0, 0 );
    fdlSampleSize.right = new FormAttachment( middle, -margin );
    fdlSampleSize.top = new FormAttachment( m_wRelationName, margin );
    wlSampleSize.setLayoutData( fdlSampleSize );

    m_wSampleSize = new TextVar( transMeta, wFieldsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    m_wSampleSize
        .setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.SampleSize.ToolTip" ) );
    props.setLook( m_wSampleSize );
    m_wSampleSize.addModifyListener( lsMod );
    m_wSampleSize.setText( "" + m_originalMeta.getSampleSize() );
    FormData fdSampleSize = new FormData();
    fdSampleSize.left = new FormAttachment( wlSampleSize, margin );
    fdSampleSize.right = new FormAttachment( 100, -margin );
    fdSampleSize.top = new FormAttachment( m_wRelationName, margin );
    m_wSampleSize.setLayoutData( fdSampleSize );

    // Seed text field
    Label wlSeed = new Label( wFieldsComp, SWT.RIGHT );
    wlSeed.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Seed.Label" ) );
    props.setLook( wlSeed );

    FormData fdlSeed = new FormData();
    fdlSeed.left = new FormAttachment( 0, 0 );
    fdlSeed.right = new FormAttachment( middle, -margin );
    fdlSeed.top = new FormAttachment( m_wSampleSize, margin );
    wlSeed.setLayoutData( fdlSeed );

    m_wSeed = new TextVar( transMeta, wFieldsComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    m_wSeed.setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.RandomSeed.ToolTip" ) );
    props.setLook( m_wSeed );
    m_wSeed.addModifyListener( lsMod );
    m_wSeed.setText( "" + m_originalMeta.getRandomSeed() ); //$NON-NLS-1$
    FormData fdSeed = new FormData();
    fdSeed.left = new FormAttachment( wlSeed, margin );
    fdSeed.right = new FormAttachment( 100, -margin );
    fdSeed.top = new FormAttachment( m_wSampleSize, margin );
    m_wSeed.setLayoutData( fdSeed );

    // Set class check box
    Label wSetClassLab = new Label( wFieldsComp, SWT.RIGHT );
    wSetClassLab.setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.SetClass.Label" ) );
    props.setLook( wSetClassLab );
    FormData fdlSetClassLab = new FormData();
    fdlSetClassLab.left = new FormAttachment( 0, 0 );
    fdlSetClassLab.right = new FormAttachment( middle, -margin );
    fdlSetClassLab.top = new FormAttachment( m_wSeed, margin );
    wSetClassLab.setLayoutData( fdlSetClassLab );

    m_wSetClassCheckBox = new Button( wFieldsComp, SWT.CHECK );
    props.setLook( m_wSetClassCheckBox );
    FormData fdSetClassCheckBox = new FormData();
    fdSetClassCheckBox.left = new FormAttachment( middle, 0 );
    fdSetClassCheckBox.top = new FormAttachment( m_wSeed, margin );
    fdSetClassCheckBox.right = new FormAttachment( 100, 0 );
    m_wSetClassCheckBox.setLayoutData( fdSetClassCheckBox );

    // Class attribute combo box
    Label wClassAttributeComboBoxLab = new Label( wFieldsComp, SWT.RIGHT );
    wClassAttributeComboBoxLab
        .setText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.ClassAttribute.Label" ) );
    props.setLook( wClassAttributeComboBoxLab );
    FormData fdlClassAttributeComboBoxLab = new FormData();
    fdlClassAttributeComboBoxLab.left = new FormAttachment( 0, 0 );
    fdlClassAttributeComboBoxLab.right = new FormAttachment( middle, -margin );
    fdlClassAttributeComboBoxLab.top = new FormAttachment( m_wSetClassCheckBox, margin );
    wClassAttributeComboBoxLab.setLayoutData( fdlClassAttributeComboBoxLab );

    m_wClassAttributeComboBox = new CCombo( wFieldsComp, SWT.BORDER | SWT.READ_ONLY );
    m_wClassAttributeComboBox
        .setToolTipText( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.SetClass.ToolTip" ) );
    props.setLook( m_wClassAttributeComboBox );
    FormData fdClassAttributeComboBox = new FormData();
    fdClassAttributeComboBox.left = new FormAttachment( middle, 0 );
    fdClassAttributeComboBox.top = new FormAttachment( m_wSetClassCheckBox, margin );
    fdClassAttributeComboBox.right = new FormAttachment( 100, 0 );
    m_wClassAttributeComboBox.setLayoutData( fdClassAttributeComboBox );
    m_wClassAttributeComboBox.setEnabled( false );

    m_wSetClassCheckBox.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        ArffMeta[] tempAM = fieldsToArffMetas();
        setUpClassAttributeNames( tempAM );
        m_wClassAttributeComboBox.setEnabled( m_wSetClassCheckBox.getSelection() );
      }
    } );

    FormData fdFieldsComp = new FormData();
    fdFieldsComp.left = new FormAttachment( 0, 0 );
    fdFieldsComp.top = new FormAttachment( 0, 0 );
    fdFieldsComp.right = new FormAttachment( 100, 0 );
    fdFieldsComp.bottom = new FormAttachment( 100, 0 );
    wFieldsComp.setLayoutData( fdFieldsComp );
    wFieldsComp.layout();
    m_wFieldsTab.setControl( wFieldsComp );
  }

  private void arffMetasToFields( ArffMeta[] fields ) {
    // ArffMeta[] fields = m_currentMeta.getInjectFields();
    if ( fields == null || fields.length == 0 ) {
      fields = setupArffMetas();
    }

    if ( fields != null ) {
      m_wFields.clearAll( false );
      Table table = m_wFields.table;

      for ( int i = 0; i < fields.length; i++ ) {
        if ( fields[i] != null ) {

          TableItem item = new TableItem( table, SWT.NONE );
          item.setText( 1, Const.NVL( fields[i].getFieldName(), "" ) ); //$NON-NLS-1$
          item.setText( 2, Const.NVL( ValueMetaFactory.getValueMetaName( fields[i].getKettleType() ), "" ) );
          item.setText( 3, Const.NVL( getArffTypeString( fields[i].getArffType() ), "" ) );
          if ( !org.pentaho.di.core.util.Utils.isEmpty( fields[i].getDateFormat() ) ) {
            item.setText( 4, fields[i].getDateFormat() );
          } else if ( !org.pentaho.di.core.util.Utils.isEmpty( fields[i].getNominalVals() ) ) {
            item.setText( 4, fields[i].getNominalVals() );
          }
        }
      }
      m_wFields.removeEmptyRows();
      m_wFields.setRowNums();
      m_wFields.optWidth( true );
    }
  }

  private void setUpClassAttributeNames( ArffMeta[] tempAM ) {
    String oldClass = m_wClassAttributeComboBox.getText();
    m_wClassAttributeComboBox.removeAll();
    ArffMeta[] fields = tempAM;
    if ( fields == null ) {
      fields = m_currentMeta.getInjectFields();
    }
    if ( fields == null || fields.length == 0 ) {
      return;
    }

    for ( ArffMeta field : fields ) {
      if ( field != null ) {
        m_wClassAttributeComboBox.add( field.getFieldName() );
      }
    }

    // restore old value if possible
    if ( !org.pentaho.di.core.util.Utils.isEmpty( oldClass ) ) {
      if ( m_wClassAttributeComboBox.indexOf( oldClass ) >= 0 ) {
        m_wClassAttributeComboBox.setText( oldClass );
      }
    }
  }

  /**
   * Convert ARFF type to a descriptive String
   *
   * @param arffType the ARFF data type as defined in ArffMeta
   * @return the ARFF data type as a String
   */
  private static String getArffTypeString( int arffType ) {
    if ( arffType == ArffMeta.NUMERIC ) {
      return "Numeric"; //$NON-NLS-1$
    }
    if ( arffType == ArffMeta.NOMINAL ) {
      return "Nominal"; //$NON-NLS-1$
    }
    if ( arffType == ArffMeta.DATE ) {
      return "Date"; //$NON-NLS-1$
    }

    return "String"; //$NON-NLS-1$
  }

  /**
   * Setup meta data for the fields based on row structure coming from previous step (if any)
   *
   * @return an array of ArffMeta
   */
  private ArffMeta[] setupArffMetas() {
    // try and set up from incoming fields from previous step
    StepMeta thisStepMeta = transMeta.findStep( stepname );

    if ( thisStepMeta != null ) {
      try {
        RowMetaInterface row = transMeta.getPrevStepFields( thisStepMeta );
        PMIFlowExecutorData data = new PMIFlowExecutorData();
        data.setupArffMeta( row );

        return data.getInjectFields();
      } catch ( KettleException ex ) {
        log.logError( toString(), BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.Log.UnableToFindInput" ) ); //$NON-NLS-1$
      }
    }
    return null;
  }

  private ArffMeta[] fieldsToArffMetas() {
    // Fields being converted to arff
    int nrNonEmptyFields = m_wFields.nrNonEmpty();
    ArffMeta[] tempAM = new ArffMeta[nrNonEmptyFields];

    // m_currentMeta.allocate(nrNonEmptyFields);

    for ( int i = 0; i < nrNonEmptyFields; i++ ) {
      TableItem item = m_wFields.getNonEmpty( i );

      String fieldName = item.getText( 1 );
      int kettleType = ValueMetaFactory.getIdForValueMeta( item.getText( 2 ) );
      int arffType = getArffTypeInt( item.getText( 3 ) );
      tempAM[i] = new ArffMeta( fieldName, kettleType, arffType );
      String dateForNomVals = item.getText( 4 );
      if ( !org.pentaho.di.core.util.Utils.isEmpty( dateForNomVals ) ) {
        if ( arffType == ArffMeta.DATE ) {
          tempAM[i].setDateFormat( dateForNomVals );
        } else if ( arffType == ArffMeta.NOMINAL ) {
          tempAM[i].setNominalVals( dateForNomVals );
        }
      }
    }
    return tempAM;
  }

  /**
   * Convert ARFF type to an integer code
   *
   * @param arffType the ARFF data type as a String
   * @return the ARFF data type as an integer (as defined in ArffMeta
   */
  private static int getArffTypeInt( String arffType ) {
    if ( arffType.equalsIgnoreCase( "Numeric" ) ) { //$NON-NLS-1$
      return ArffMeta.NUMERIC;
    }
    if ( arffType.equalsIgnoreCase( "Nominal" ) ) { //$NON-NLS-1$
      return ArffMeta.NOMINAL;
    }
    if ( arffType.equalsIgnoreCase( "String" ) ) { //$NON-NLS-1$
      return ArffMeta.STRING;
    }
    return ArffMeta.DATE;
  }

  private boolean loadFlow() {
    String filename = m_wFilename.getText();
    boolean success = false;
    try {
      Flow loadedFlow = PMIFlowExecutorData.getFlowFromFileVFS( filename, transMeta, m_env );
      m_kfPerspective.getCurrentLayout().setFlow( loadedFlow );
      m_currentFlow = loadedFlow;
      filename = transMeta.environmentSubstitute( filename );
      File flowF = PMIFlowExecutorData.pathToURI( filename, transMeta );
      if ( flowF != null ) {
        m_env.addVariable( KFGUIConsts.FLOW_DIRECTORY_KEY, flowF.getParent() );
      }
      success = true;
    } catch ( Exception ex ) {
      new ErrorDialog( shell, stepname,
          BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowDialog.ErrorLoadingFlow" ), ex );
      success = false;
    }

    if ( org.pentaho.di.core.util.Utils.isEmpty( filename ) && !m_wbKFeditor.isEnabled() ) {
      m_wbGetEditorChanges.setEnabled( true );
    }

    return success;
  }
}
