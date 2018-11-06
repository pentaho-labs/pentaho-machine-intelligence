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

import org.apache.commons.vfs2.FileObject;
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Props;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.steps.pmi.PMIScoringClusterer;
import org.pentaho.di.trans.steps.pmi.PMIScoringData;
import org.pentaho.di.trans.steps.pmi.PMIScoringMeta;
import org.pentaho.di.trans.steps.pmi.PMIScoringModel;
import org.pentaho.di.ui.core.widget.TextVar;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.trans.step.BaseStepDialog;
import org.pentaho.vfs.ui.VfsFileChooserDialog;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.xml.XStream;

/**
 * Dialog class for PMIScoring
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class PMIScoringDialog extends BaseStepDialog implements StepDialogInterface {

  private Text m_stepnameText;

  /**
   * The tabs of the dialog
   */
  private CTabFolder m_wTabFolder;
  private CTabItem m_wFileTab, m_wFieldsTab, m_wModelTab;

  /**
   * Checkbox for serializing model into step meta data
   */
  // private Button m_storeModelInStepMetaData;

  /**
   * Checkbox for accept filename from field
   */
  private Button m_wAcceptFileNameFromFieldCheckBox;

  /**
   * TextVar for file name field
   */
  private TextVar m_wAcceptFileNameFromFieldText;

  /**
   * Check box for caching models in memory
   */
  private Button m_wCacheModelsCheckBox;

  /**
   * check box for output probabilities
   */
  private Button m_wOutputProbs;

  /**
   * Update model checkbox
   */
  private Button m_wUpdateModel;

  /**
   * Browse file button
   */
  private Button m_wbFilename;

  /**
   * Combines text field with widget to insert environment variable
   */
  private TextVar m_wFilename;

  /**
   * Browse file button for saving incrementally updated model
   */
  private Button m_wbSaveFilename;

  /**
   * Combines text field with widget to insert environment variable for saving
   * incrementally updated models
   */
  private TextVar m_wSaveFilename;

  /**
   * TextVar for batch sizes to be pushed to BatchPredictors
   */
  private TextVar m_batchScoringBatchSizeText;

  /**
   * the text area for the model
   */
  private Text m_wModelText;

  /**
   * the text area for the fields mapping
   */
  private Text m_wMappingText;

  /**
   * Checkbox for performing evaluation
   */
  private Button m_wPerformEvaluation;

  /**
   * Checkbox for outputting IR metrics when evaluating
   */
  private Button m_wOutputIRMetrics;

  /**
   * Checkbox for outputting area under the curve metrics when evaluating
   */
  private Button m_wOutputAUCMetrics;

  /**
   * meta data for the step. A copy is made so that changes, in terms of choices
   * made by the user, can be detected.
   */
  private final PMIScoringMeta m_inputMeta;
  private final PMIScoringMeta m_originalMeta;

  public PMIScoringDialog( Shell parent, Object inMeta, TransMeta tr, String stepName ) {
    super( parent, (BaseStepMeta) inMeta, tr, stepName );

    m_inputMeta = (PMIScoringMeta) inMeta;
    m_originalMeta = (PMIScoringMeta) m_inputMeta.clone();
  }

  public String open() {

    Shell parent = getParent();
    Display display = parent.getDisplay();

    shell = new Shell( parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );

    props.setLook( shell );
    setShellImage( shell, m_originalMeta );

    // used to listen to a text field (m_wStepname)
    ModifyListener lsMod = new ModifyListener() {
      public void modifyText( ModifyEvent e ) {
        m_inputMeta.setChanged();
      }
    };

    changed = m_inputMeta.hasChanged();

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = Const.FORM_MARGIN;
    formLayout.marginHeight = Const.FORM_MARGIN;

    shell.setLayout( formLayout );
    shell.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Shell.Title" ) );

    int middle = props.getMiddlePct();
    int margin = Const.MARGIN;

    // Stepname line
    Label stepnameLab = new Label( shell, SWT.RIGHT );
    stepnameLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.StepName.Label" ) );
    props.setLook( stepnameLab );

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( middle, -margin );
    fd.top = new FormAttachment( 0, margin );
    stepnameLab.setLayoutData( fd );
    m_stepnameText = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    m_stepnameText.setText( stepname );
    props.setLook( m_stepnameText );
    m_stepnameText.addModifyListener( lsMod );

    // format the text field
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( 0, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_stepnameText.setLayoutData( fd );

    m_wTabFolder = new CTabFolder( shell, SWT.BORDER );
    props.setLook( m_wTabFolder, Props.WIDGET_STYLE_TAB );
    m_wTabFolder.setSimple( false );

    // setup tabs
    setupFileTab( middle, margin, lsMod );
    setupFieldsMappingTab( middle, margin, lsMod );
    setupModelDisplayTab( middle, margin, lsMod );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( m_stepnameText, margin );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, -50 );
    m_wTabFolder.setLayoutData( fd );

    // Buttons inherited from BaseStepDialog
    wOK = new Button( shell, SWT.PUSH );
    wOK.setText( BaseMessages.getString( PMIScoringMeta.PKG, "System.Button.OK" ) );

    wCancel = new Button( shell, SWT.PUSH );
    wCancel.setText( BaseMessages.getString( PMIScoringMeta.PKG, "System.Button.Cancel" ) );

    setButtonPositions( new Button[] { wOK, wCancel }, margin, m_wTabFolder );

    // Add listeners
    lsCancel = new Listener() {
      public void handleEvent( Event e ) {
        cancel();
      }
    };
    lsOK = new Listener() {
      public void handleEvent( Event e ) {
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

    m_stepnameText.addSelectionListener( lsDef );

    // Detect X or ALT-F4 or something that kills this window...
    shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent e ) {
        cancel();
      }
    } );

    // listen to the file name text box and try to load a model
    // if the user presses enter
    m_wFilename.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetDefaultSelected( SelectionEvent e ) {
        if ( !loadModel() ) {
          log.logError( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Log.FileLoadingError" ) );
        }
      }
    } );

    m_wbFilename.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        String[] extensions = null;
        String[] filterNames = null;
        if ( XStream.isPresent() ) {
          extensions = new String[4];
          filterNames = new String[4];
          extensions[0] = "*.model";
          filterNames[0] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFileBinary" );
          extensions[1] = "*.xstreammodel"; //$NON-NLS-1$
          filterNames[1] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFileXML" );
          extensions[2] = "*.xml";
          filterNames[2] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFilePMML" );
          extensions[3] = "*";
          filterNames[3] = BaseMessages.getString( PMIScoringMeta.PKG, "System.FileType.AllFiles" );
        } else {
          extensions = new String[3];
          filterNames = new String[3];
          extensions[0] = "*.model";
          filterNames[0] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFileBinary" );
          extensions[1] = "*.xml";
          filterNames[1] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFilePMML" );
          extensions[2] = "*";
          filterNames[2] = BaseMessages.getString( PMIScoringMeta.PKG, "System.FileType.AllFiles" );
        }

        // get current file
        FileObject rootFile = null;
        FileObject initialFile = null;
        FileObject defaultInitialFile = null;

        try {
          if ( m_wFilename.getText() != null ) {
            String fname = transMeta.environmentSubstitute( m_wFilename.getText() );

            if ( !Const.isEmpty( fname ) ) {
              initialFile = KettleVFS.getFileObject( fname );
              rootFile = initialFile.getFileSystem().getRoot();
            } else {
              defaultInitialFile = KettleVFS.getFileObject( Spoon.getInstance().getLastFileOpened() );
            }
          } else {
            defaultInitialFile = KettleVFS.getFileObject( "file:///c:/" );
          }

          if ( rootFile == null ) {
            rootFile = defaultInitialFile.getFileSystem().getRoot();
          }

          VfsFileChooserDialog fileChooserDialog = Spoon.getInstance().getVfsFileChooserDialog( rootFile, initialFile );
          fileChooserDialog.setRootFile( rootFile );
          fileChooserDialog.setInitialFile( initialFile );
          fileChooserDialog.defaultInitialFile = rootFile;

          String in = ( !Const.isEmpty( m_wFilename.getText() ) ) ? initialFile.getName().getPath() : null;
          FileObject
              selectedFile =
              fileChooserDialog.open( shell, null, "file", true, in, extensions, filterNames,
                  VfsFileChooserDialog.VFS_DIALOG_OPEN_FILE );

          if ( selectedFile != null ) {
            m_wFilename.setText( selectedFile.getURL().toString() );
          }

          // try to load model file and display model
          if ( !loadModel() ) {
            log.logError( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Log.FileLoadingError" ) );
          }
        } catch ( Exception ex ) {
          logError( "A problem occurred", ex );
        }
      }
    } );

    m_wbSaveFilename.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        FileDialog dialog = new FileDialog( shell, SWT.SAVE );
        String[] extensions = null;
        String[] filterNames = null;
        if ( XStream.isPresent() ) {
          extensions = new String[3];
          filterNames = new String[3];
          extensions[0] = "*.model";
          filterNames[0] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFileBinary" );
          extensions[1] = "*.xstreammodel";
          filterNames[1] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFileXML" );
          extensions[2] = "*"; //$NON-NLS-1$
          filterNames[2] = BaseMessages.getString( PMIScoringMeta.PKG, "System.FileType.AllFiles" );
        } else {
          extensions = new String[2];
          filterNames = new String[2];
          extensions[0] = "*.model";
          filterNames[0] = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileType.ModelFileBinary" );
          extensions[1] = "*";
          filterNames[1] = BaseMessages.getString( PMIScoringMeta.PKG, "System.FileType.AllFiles" );
        }
        dialog.setFilterExtensions( extensions );
        if ( m_wSaveFilename.getText() != null ) {
          dialog.setFileName( transMeta.environmentSubstitute( m_wSaveFilename.getText() ) );
        }
        dialog.setFilterNames( filterNames );

        if ( dialog.open() != null ) {

          m_wSaveFilename
              .setText( dialog.getFilterPath() + System.getProperty( "file.separator" ) + dialog.getFileName() );
        }
      }
    } );

    m_wTabFolder.setSelection( 0 );

    // Set the shell size, based upon previous time...
    setSize();

    getData();
    if ( m_inputMeta.getModel() == null ) {
      loadModel();
    }

    shell.open();
    while ( !shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }

    return stepname;
  }

  private void cancel() {
    stepname = null;
    m_inputMeta.setChanged( changed );

    // revert to original model
    PMIScoringModel
        temp =
        ( m_originalMeta.getFileNameFromField() ) ? m_originalMeta.getDefaultModel() : m_originalMeta.getModel();
    m_inputMeta.setModel( temp );
    dispose();
  }

  private void ok() {
    if ( Const.isEmpty( m_stepnameText.getText() ) ) {
      return;
    }

    stepname = m_stepnameText.getText(); // return value

    m_inputMeta.setFileNameFromField( m_wAcceptFileNameFromFieldCheckBox.getSelection() );

    // m_inputMeta.setStoreModelInStepMetaData( m_storeModelInStepMetaData.getSelection() );

    if ( !Const.isEmpty( m_wFilename.getText() ) && !m_inputMeta.getStoreModelInStepMetaData() ) {
      m_inputMeta.setSerializedModelFileName( m_wFilename.getText() );
    } else {
      if ( !Const.isEmpty( m_wFilename.getText() ) ) {
        // need to load model and set in meta data here
        loadModel();
      }

      m_inputMeta.setSerializedModelFileName( null );
    }

    if ( !Const.isEmpty( m_wAcceptFileNameFromFieldText.getText() ) ) {
      m_inputMeta.setFieldNameToLoadModelFrom( m_wAcceptFileNameFromFieldText.getText() );
    }
    m_inputMeta.setCacheLoadedModels( m_wCacheModelsCheckBox.getSelection() );

    m_inputMeta.setOutputProbabilities( m_wOutputProbs.getSelection() );
    m_inputMeta.setUpdateIncrementalModel( m_wUpdateModel.getSelection() );

    m_inputMeta.setEvaluateRatherThanScore( m_wPerformEvaluation.getSelection() );
    m_inputMeta.setOutputIRMetrics( m_wOutputIRMetrics.getSelection() );
    m_inputMeta.setOutputAUCMetrics( m_wOutputAUCMetrics.getSelection() );

    if ( m_inputMeta.getUpdateIncrementalModel() ) {
      if ( !Const.isEmpty( m_wSaveFilename.getText() ) ) {
        m_inputMeta.setSavedModelFileName( m_wSaveFilename.getText() );
      } else {
        // make sure that save filename is empty
        m_inputMeta.setSavedModelFileName( "" );
      }
    }

    if ( !Const.isEmpty( m_batchScoringBatchSizeText.getText() ) ) {
      m_inputMeta.setBatchScoringSize( m_batchScoringBatchSizeText.getText() );
    }

    if ( !m_originalMeta.equals( m_inputMeta ) ) {
      m_inputMeta.setChanged();
      changed = m_inputMeta.hasChanged();
    }

    dispose();
  }

  public void getData() {
    if ( m_inputMeta.getFileNameFromField() ) {
      m_wAcceptFileNameFromFieldCheckBox.setSelection( true );
      m_wCacheModelsCheckBox.setEnabled( true );
      m_wSaveFilename.setEnabled( false );
      m_wbSaveFilename.setEnabled( false );
      m_wSaveFilename.setText( "" );
      m_wUpdateModel.setEnabled( false );
      if ( !Const.isEmpty( m_inputMeta.getFieldNameToLoadModelFrom() ) ) {
        m_wAcceptFileNameFromFieldText.setText( m_inputMeta.getFieldNameToLoadModelFrom() );
      }
      m_wAcceptFileNameFromFieldText.setEnabled( true );

      m_wCacheModelsCheckBox.setSelection( m_inputMeta.getCacheLoadedModels() );
      m_wFilename.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Default.Label" ) );
    }

    if ( m_inputMeta.getSerializedModelFileName() != null ) {
      m_wFilename.setText( m_inputMeta.getSerializedModelFileName() );
    }

    m_wOutputProbs.setSelection( m_inputMeta.getOutputProbabilities() );

    if ( !m_inputMeta.getFileNameFromField() ) {
      m_wUpdateModel.setSelection( m_inputMeta.getUpdateIncrementalModel() );
    }

    if ( m_wUpdateModel.getSelection() ) {
      if ( m_inputMeta.getSavedModelFileName() != null ) {
        m_wSaveFilename.setText( m_inputMeta.getSavedModelFileName() );
      }
    }

    if ( !Const.isEmpty( m_inputMeta.getBatchScoringSize() ) ) {
      m_batchScoringBatchSizeText.setText( m_inputMeta.getBatchScoringSize() );
    }

    // m_storeModelInStepMetaData.setSelection( m_inputMeta.getStoreModelInStepMetaData() );

    m_wPerformEvaluation.setSelection( m_inputMeta.getEvaluateRatherThanScore() );
    m_wOutputIRMetrics.setSelection( m_inputMeta.getOutputIRMetrics() );
    m_wOutputAUCMetrics.setSelection( m_inputMeta.getOutputAUCMetrics() );

    // Grab model if it is available (and we are not reading model file
    // names from a field in the incoming data
    PMIScoringModel
        tempM =
        ( m_inputMeta.getFileNameFromField() ) ? m_inputMeta.getDefaultModel() : m_inputMeta.getModel();
    if ( tempM != null ) {
      m_wModelText.setText( tempM.toString() );

      // Grab mappings if available
      mappingString( tempM );
      checkAbilityToBatchScore( tempM );
      checkAbilityToProduceProbabilities( tempM );
      checkAbilityToUpdateModelIncrementally( tempM );
    } else {
      // try loading the model
      loadModel();
    }
    checkEvalWidgets( tempM );
  }

  protected void checkEvalWidgets( PMIScoringModel tempM ) {
    if ( tempM != null ) {
      if ( !tempM.isSupervisedLearningModel() ) {
        m_wPerformEvaluation.setSelection( false );
      }
    }

    m_wOutputProbs.setEnabled( !m_wPerformEvaluation.getSelection() );
    m_wOutputIRMetrics.setEnabled( m_wPerformEvaluation.getSelection() );
    m_wOutputAUCMetrics.setEnabled( m_wPerformEvaluation.getSelection() );
  }

  protected void setupModelDisplayTab( int middle, int margin, ModifyListener lsMod ) {
    // Model display tab
    m_wModelTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wModelTab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.ModelTab.TabTitle" ) );

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
    FontData fontd = new FontData( "Courier New", 12, SWT.NORMAL );
    m_wModelText.setFont( new Font( getParent().getDisplay(), fontd ) );

    props.setLook( m_wModelText );
    // format the model text area
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, margin );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_wModelText.setLayoutData( fd );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, 0 );
    wModelComp.setLayoutData( fd );

    wModelComp.layout();
    m_wModelTab.setControl( wModelComp );
  }

  protected void setupFieldsMappingTab( int middle, int margin, ModifyListener lsMod ) {
    // Fields mapping tab
    m_wFieldsTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wFieldsTab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FieldsTab.TabTitle" ) );

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
    FontData fontd = new FontData( "Courier New", 12, SWT.NORMAL );
    m_wMappingText.setFont( new Font( getParent().getDisplay(), fontd ) );

    props.setLook( m_wMappingText );
    // format the fields mapping text area
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, margin );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_wMappingText.setLayoutData( fd );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, 0 );
    wFieldsComp.setLayoutData( fd );

    wFieldsComp.layout();
    m_wFieldsTab.setControl( wFieldsComp );
  }

  protected void setupFileTab( int middle, int margin, ModifyListener lsMod ) {
    // Start of the file tab
    m_wFileTab = new CTabItem( m_wTabFolder, SWT.NONE );
    m_wFileTab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.FileTab.TabTitle" ) );

    Composite wFileComp = new Composite( m_wTabFolder, SWT.NONE );
    props.setLook( wFileComp );

    FormLayout fileLayout = new FormLayout();
    fileLayout.marginWidth = 3;
    fileLayout.marginHeight = 3;
    wFileComp.setLayout( fileLayout );

    // Filename line
    final Label filenameLab = new Label( wFileComp, SWT.RIGHT );
    filenameLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Filename.Label" ) );
    props.setLook( filenameLab );
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, margin );
    fd.right = new FormAttachment( middle, -margin );
    filenameLab.setLayoutData( fd );

    // file browse button
    m_wbFilename = new Button( wFileComp, SWT.PUSH | SWT.CENTER );
    props.setLook( m_wbFilename );
    m_wbFilename.setText( BaseMessages.getString( PMIScoringMeta.PKG, "System.Button.Browse" ) );
    fd = new FormData();
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( 0, 0 );
    m_wbFilename.setLayoutData( fd );

    // combined text field and env variable widget
    m_wFilename = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_wFilename );
    m_wFilename.addModifyListener( lsMod );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( 0, margin );
    fd.right = new FormAttachment( m_wbFilename, -margin );
    m_wFilename.setLayoutData( fd );

    // store model in meta data
    /* Label saveModelMetaLab = new Label( wFileComp, SWT.RIGHT );
    props.setLook( saveModelMetaLab );
    saveModelMetaLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.SaveModelToMeta.Label" ) );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( m_wFilename, margin );
    fd.right = new FormAttachment( middle, -margin );
    saveModelMetaLab.setLayoutData( fd ); */

    /* m_storeModelInStepMetaData = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_storeModelInStepMetaData );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( m_wFilename, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_storeModelInStepMetaData.setLayoutData( fd ); */

    Label updateModelLab = new Label( wFileComp, SWT.RIGHT );
    updateModelLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.UpdateModel.Label" ) );
    props.setLook( updateModelLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    // fd.top = new FormAttachment( m_storeModelInStepMetaData, margin );
    fd.top = new FormAttachment( m_wFilename, margin );
    fd.right = new FormAttachment( middle, -margin );
    updateModelLab.setLayoutData( fd );
    m_wUpdateModel = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_wUpdateModel );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    // fd.top = new FormAttachment( m_storeModelInStepMetaData, margin );
    fd.top = new FormAttachment( m_wFilename, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_wUpdateModel.setLayoutData( fd );
    m_wUpdateModel.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        m_inputMeta.setChanged();
        m_wbSaveFilename.setEnabled( m_wUpdateModel.getSelection() );
        m_wSaveFilename.setEnabled( m_wUpdateModel.getSelection() );
      }
    } );

    // Save filename line
    Label saveFilenameLab = new Label( wFileComp, SWT.RIGHT );
    saveFilenameLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.SaveFilename.Label" ) );
    props.setLook( saveFilenameLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( m_wUpdateModel, margin );
    fd.right = new FormAttachment( middle, -margin );
    saveFilenameLab.setLayoutData( fd );

    // Save file browse button
    m_wbSaveFilename = new Button( wFileComp, SWT.PUSH | SWT.CENTER );
    props.setLook( m_wbSaveFilename );
    m_wbSaveFilename.setText( BaseMessages.getString( PMIScoringMeta.PKG, "System.Button.Browse" ) );
    fd = new FormData();
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( m_wUpdateModel, 0 );
    m_wbSaveFilename.setLayoutData( fd );
    m_wbSaveFilename.setEnabled( false );

    // combined text field and env variable widget
    m_wSaveFilename = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_wSaveFilename );
    m_wSaveFilename.addModifyListener( lsMod );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( m_wUpdateModel, margin );
    fd.right = new FormAttachment( m_wbSaveFilename, -margin );
    m_wSaveFilename.setLayoutData( fd );
    m_wSaveFilename.setEnabled( false );

    Label acceptFileNameLab = new Label( wFileComp, SWT.RIGHT );
    acceptFileNameLab.setText(
        BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.AcceptFileNamesFromFieldCheck.Label" ) );
    props.setLook( acceptFileNameLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( m_wSaveFilename, margin );
    fd.right = new FormAttachment( middle, -margin );
    acceptFileNameLab.setLayoutData( fd );
    m_wAcceptFileNameFromFieldCheckBox = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_wAcceptFileNameFromFieldCheckBox );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( m_wSaveFilename, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_wAcceptFileNameFromFieldCheckBox.setLayoutData( fd );

    m_wAcceptFileNameFromFieldCheckBox.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent e ) {
        m_inputMeta.setChanged();
        if ( m_wAcceptFileNameFromFieldCheckBox.getSelection() ) {
          m_wUpdateModel.setSelection( false );
          m_wUpdateModel.setEnabled( false );
          m_wSaveFilename.setText( "" ); //$NON-NLS-1$
          filenameLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Default.Label" ) );
          if ( !Const.isEmpty( m_wFilename.getText() ) ) {
            // load - loadModel() will take care of storing it in
            // either the main or default model in the current meta
            loadModel();
          } else {
            // try and shift the main model (if non-null) over into the
            // default model in current meta
            m_inputMeta.setDefaultModel( m_inputMeta.getModel() );
            m_inputMeta.setModel( null );
          }
        } else {
          if ( !Const.isEmpty( m_wFilename.getText() ) ) {
            // load - loadModel() will take care of storing it in
            // either the main or default model in the current meta
            loadModel();
          } else {
            // try and shift the default model (if non-null) over into the
            // main model in current meta
            m_inputMeta.setModel( m_inputMeta.getDefaultModel() );
            m_inputMeta.setDefaultModel( null );
          }

          m_wCacheModelsCheckBox.setSelection( false );
          filenameLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Filename.Label" ) );
        }

        m_wCacheModelsCheckBox.setEnabled( m_wAcceptFileNameFromFieldCheckBox.getSelection() );
        m_wAcceptFileNameFromFieldText.setEnabled( m_wAcceptFileNameFromFieldCheckBox.getSelection() );
        m_wbSaveFilename
            .setEnabled( !m_wAcceptFileNameFromFieldCheckBox.getSelection() && m_wUpdateModel.getSelection() );
        m_wSaveFilename
            .setEnabled( !m_wAcceptFileNameFromFieldCheckBox.getSelection() && m_wUpdateModel.getSelection() );
      }
    } );

    Label acceptFileNameFromFieldLab = new Label( wFileComp, SWT.RIGHT );
    acceptFileNameFromFieldLab
        .setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.AcceptFileNamesFromField.Label" ) );
    props.setLook( acceptFileNameFromFieldLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( m_wAcceptFileNameFromFieldCheckBox, margin );
    fd.right = new FormAttachment( middle, -margin );
    acceptFileNameFromFieldLab.setLayoutData( fd );
    m_wAcceptFileNameFromFieldText = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_wAcceptFileNameFromFieldText );
    m_wAcceptFileNameFromFieldText.addModifyListener( lsMod );
    FormData fdAcceptText = new FormData();
    fdAcceptText.left = new FormAttachment( middle, 0 );
    fdAcceptText.top = new FormAttachment( m_wAcceptFileNameFromFieldCheckBox, margin );
    fdAcceptText.right = new FormAttachment( 100, 0 );
    m_wAcceptFileNameFromFieldText.setLayoutData( fdAcceptText );
    m_wAcceptFileNameFromFieldText.setEnabled( false );

    Label cacheModelsLab = new Label( wFileComp, SWT.RIGHT );
    cacheModelsLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.CacheModels.Label" ) );
    props.setLook( cacheModelsLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( m_wAcceptFileNameFromFieldText, margin );
    fd.right = new FormAttachment( middle, -margin );
    cacheModelsLab.setLayoutData( fd );
    //
    m_wCacheModelsCheckBox = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_wCacheModelsCheckBox );
    FormData fdCacheCheckBox = new FormData();
    fdCacheCheckBox.left = new FormAttachment( middle, 0 );
    fdCacheCheckBox.top = new FormAttachment( m_wAcceptFileNameFromFieldText, margin );
    fdCacheCheckBox.right = new FormAttachment( 100, 0 );
    m_wCacheModelsCheckBox.setLayoutData( fdCacheCheckBox );
    m_wCacheModelsCheckBox.setEnabled( false );

    Label outputProbsLab = new Label( wFileComp, SWT.RIGHT );
    outputProbsLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.OutputProbs.Label" ) );
    props.setLook( outputProbsLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( m_wCacheModelsCheckBox, margin );
    fd.right = new FormAttachment( middle, -margin );
    outputProbsLab.setLayoutData( fd );
    m_wOutputProbs = new Button( wFileComp, SWT.CHECK );
    props.setLook( m_wOutputProbs );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( m_wCacheModelsCheckBox, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_wOutputProbs.setLayoutData( fd );

    // batch scoring size line
    Label batchLab = new Label( wFileComp, SWT.RIGHT );
    batchLab.setText( "Batch scoring batch size" ); //$NON-NLS-1$
    props.setLook( batchLab );
    FormData fdd = new FormData();
    fdd.left = new FormAttachment( 0, 0 );
    fdd.top = new FormAttachment( m_wOutputProbs, margin );
    fdd.right = new FormAttachment( middle, -margin );
    batchLab.setLayoutData( fdd );

    m_batchScoringBatchSizeText = new TextVar( transMeta, wFileComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_batchScoringBatchSizeText );
    m_batchScoringBatchSizeText.addModifyListener( lsMod );
    fdd = new FormData();
    fdd.left = new FormAttachment( middle, 0 );
    fdd.top = new FormAttachment( m_wOutputProbs, margin );
    fdd.right = new FormAttachment( 100, 0 );
    m_batchScoringBatchSizeText.setLayoutData( fdd );
    m_batchScoringBatchSizeText.setEnabled( false );

    Control lastWidget = m_batchScoringBatchSizeText;

    Group evaluationGroup = new Group( wFileComp, SWT.SHADOW_NONE );
    props.setLook( evaluationGroup );
    evaluationGroup.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.EvaluationGroupTitle" ) );
    FormLayout fl = new FormLayout();
    fl.marginWidth = 10;
    fl.marginHeight = 10;
    evaluationGroup.setLayout( fl );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( m_batchScoringBatchSizeText, margin );
    evaluationGroup.setLayoutData( fd );

    // evaluation stuff
    Label performEvalLab = new Label( evaluationGroup, SWT.RIGHT );
    props.setLook( performEvalLab );
    performEvalLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.PerformEvalLab" ) );
    performEvalLab
        .setToolTipText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.PerformEvalTipText" ) );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, margin );
    fd.right = new FormAttachment( middle, -margin );
    performEvalLab.setLayoutData( fd );

    m_wPerformEvaluation = new Button( evaluationGroup, SWT.CHECK );
    props.setLook( m_wPerformEvaluation );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( 0, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_wPerformEvaluation.setLayoutData( fd );
    lastWidget = m_wPerformEvaluation;

    m_wPerformEvaluation.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        checkEvalWidgets( null );
      }
    } );

    Label outputIRLab = new Label( evaluationGroup, SWT.RIGHT );
    props.setLook( outputIRLab );
    outputIRLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.OutputIRMetricsLab" ) );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( lastWidget, margin );
    fd.right = new FormAttachment( middle, -margin );
    outputIRLab.setLayoutData( fd );

    m_wOutputIRMetrics = new Button( evaluationGroup, SWT.CHECK );
    props.setLook( m_wOutputIRMetrics );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( lastWidget, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_wOutputIRMetrics.setLayoutData( fd );
    lastWidget = m_wOutputIRMetrics;

    Label outputAUCLab = new Label( evaluationGroup, SWT.RIGHT );
    props.setLook( outputAUCLab );
    outputAUCLab.setText( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.OutputAUCMetricsLab" ) );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( lastWidget, margin );
    fd.right = new FormAttachment( middle, -margin );
    outputAUCLab.setLayoutData( fd );

    m_wOutputAUCMetrics = new Button( evaluationGroup, SWT.CHECK );
    props.setLook( m_wOutputAUCMetrics );
    fd = new FormData();
    fd.left = new FormAttachment( middle, 0 );
    fd.top = new FormAttachment( lastWidget, margin );
    fd.right = new FormAttachment( 100, 0 );
    m_wOutputAUCMetrics.setLayoutData( fd );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, 0 );
    wFileComp.setLayoutData( fd );

    wFileComp.layout();
    m_wFileTab.setControl( wFileComp );
  }

  /**
   * Load the model.
   */
  private boolean loadModel() {
    String filename = m_wFilename.getText();
    if ( Const.isEmpty( filename ) ) {
      return false;
    }

    boolean success = false;
    try {
      if ( PMIScoringData.modelFileExists( filename, transMeta ) ) {

        PMIScoringModel tempM = PMIScoringData.loadSerializedModel( filename, log, transMeta );
        m_wModelText.setText( tempM.toString() );

        if ( m_wAcceptFileNameFromFieldCheckBox.getSelection() ) {
          m_inputMeta.setDefaultModel( tempM );
        } else {
          m_inputMeta.setModel( tempM );
        }

        checkAbilityToBatchScore( tempM );
        checkAbilityToProduceProbabilities( tempM );
        checkAbilityToUpdateModelIncrementally( tempM );

        // see if we can find a previous step and set up the
        // mappings
        mappingString( tempM );
        success = true;

      }
    } catch ( Exception ex ) {
      ex.printStackTrace();
      log.logError( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Log.FileLoadingError" ), ex );
    }

    return success;
  }

  private void checkAbilityToBatchScore( PMIScoringModel tempM ) {
    if ( tempM.isBatchPredictor() ) {
      m_wUpdateModel.setSelection( false );
      m_wUpdateModel.setEnabled( false );
      // disable the save field and button
      m_wbSaveFilename.setEnabled( false );
      m_wSaveFilename.setEnabled( false );
      // clear the text field
      m_wSaveFilename.setText( "" );

      m_wAcceptFileNameFromFieldCheckBox.setSelection( false );
      m_wAcceptFileNameFromFieldCheckBox.setEnabled( false );
      m_wAcceptFileNameFromFieldText.setEnabled( false );
      m_wAcceptFileNameFromFieldText.setText( "" );
      m_batchScoringBatchSizeText.setEnabled( true );
    } else {
      m_wUpdateModel.setEnabled( true );
      // disable the save field and button
      m_wbSaveFilename.setEnabled( true );
      m_wSaveFilename.setEnabled( true );
      m_wAcceptFileNameFromFieldCheckBox.setEnabled( true );
      m_wAcceptFileNameFromFieldText.setEnabled( true );
      m_batchScoringBatchSizeText.setEnabled( false );
    }
  }

  private void checkAbilityToUpdateModelIncrementally( PMIScoringModel tempM ) {
    if ( !tempM.isUpdateableModel() ) {
      m_wUpdateModel.setSelection( false );
      m_wUpdateModel.setEnabled( false );
      // disable the save field and button
      m_wbSaveFilename.setEnabled( false );
      m_wSaveFilename.setEnabled( false );
      // clear the text field
      m_wSaveFilename.setText( "" ); //$NON-NLS-1$

    } else if ( !m_wAcceptFileNameFromFieldCheckBox.getSelection() ) {
      m_wUpdateModel.setEnabled( true );
      // enable the save field and button if the check box is selected
      if ( m_wUpdateModel.getSelection() ) {
        m_wbSaveFilename.setEnabled( true );
        m_wSaveFilename.setEnabled( true );
      }
    }
  }

  private void checkAbilityToProduceProbabilities( PMIScoringModel tempM ) {
    // take a look at the model-type and then the class
    // attribute (if set and if necessary) in order
    // to determine whether to disable/enable the
    // output probabilities checkbox
    if ( !tempM.isSupervisedLearningModel() ) {
      // now, does the clusterer produce probabilities?
      if ( ( (PMIScoringClusterer) tempM ).canProduceProbabilities() ) {
        m_wOutputProbs.setEnabled( true );
      } else {
        m_wOutputProbs.setSelection( false );
        m_wOutputProbs.setEnabled( false );
      }
    } else {
      // take a look at the header and disable the output
      // probs checkbox if there is a class attribute set
      // and the class is numeric
      Instances header = tempM.getHeader();
      if ( header.classIndex() >= 0 ) {
        if ( header.classAttribute().isNumeric() ) {
          m_wOutputProbs.setSelection( false );
          m_wOutputProbs.setEnabled( false );
        } else {
          m_wOutputProbs.setEnabled( true );
        }
      }
    }
  }

  /**
   * Build a string that shows the mappings between PMI model attributes and incoming
   * Kettle fields.
   *
   * @param model a <code>PMIScoringModel</code> value
   */
  private void mappingString( PMIScoringModel model ) {

    try {
      StepMeta stepMetaTemp = transMeta.findStep( stepname );
      if ( stepMetaTemp != null ) {
        RowMetaInterface rowM = transMeta.getPrevStepFields( stepMetaTemp );
        Instances header = model.getHeader();
        int[] mappings = PMIScoringData.findMappings( header, rowM );

        StringBuilder result = new StringBuilder( header.numAttributes() * 10 );

        int maxLength = 0;
        for ( int i = 0; i < header.numAttributes(); i++ ) {
          if ( header.attribute( i ).name().length() > maxLength ) {
            maxLength = header.attribute( i ).name().length();
          }
        }
        maxLength += 12; // length of " (nominal)"/" (numeric)"

        int minLength = 16; // "Model attributes".length()
        String headerS = BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.ModelAttsHeader" );
        String sep = "----------------";

        if ( maxLength < minLength ) {
          maxLength = minLength;
        }
        headerS = getFixedLengthString( headerS, ' ', maxLength );
        sep = getFixedLengthString( sep, '-', maxLength );
        sep += "\t    ----------------\n";
        headerS +=
            "\t    " + BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.IncomingFields" ) + "\n";
        result.append( headerS );
        result.append( sep );

        for ( int i = 0; i < header.numAttributes(); i++ ) {
          Attribute temp = header.attribute( i );
          String attName = "(";
          if ( temp.isNumeric() ) {
            attName += BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.Numeric" ) + ")";
          } else if ( temp.isNominal() ) {
            attName += BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.Nominal" ) + ")";
          } else if ( temp.isString() ) {
            attName += BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.String" ) + ")";
          }
          attName += ( " " + temp.name() );

          attName = getFixedLengthString( attName, ' ', maxLength );
          attName += "\t--> ";
          result.append( attName );
          String inFieldNum = "";
          if ( mappings[i] == PMIScoringData.NO_MATCH ) {
            inFieldNum += "- ";
            result.append(
                inFieldNum + BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.MissingNoMatch" )
                    + "\n" );
          } else if ( mappings[i] == PMIScoringData.TYPE_MISMATCH ) {
            inFieldNum += ( rowM.indexOfValue( temp.name() ) + 1 ) + " ";
            result.append( inFieldNum + BaseMessages
                .getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.MissingTypeMismatch" ) + "\n" );
          } else {
            ValueMetaInterface tempField = rowM.getValueMeta( mappings[i] );
            String fieldName = "" + ( mappings[i] + 1 ) + " (";
            if ( tempField.isBoolean() ) {
              fieldName += BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.Boolean" ) + ")";
            } else if ( tempField.isNumeric() ) {
              fieldName += BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.Numeric" ) + ")";
            } else if ( tempField.isString() ) {
              fieldName += BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Mapping.String" ) + ")";
            }
            fieldName += " " + tempField.getName();
            result.append( fieldName + "\n" );
          }
        }

        // set the text of the text area in the Mappings tab
        m_wMappingText.setText( result.toString() );
      }
    } catch ( KettleException e ) {
      log.logError( BaseMessages.getString( PMIScoringMeta.PKG, "PMIScoringDialog.Log.UnableToFindInput" ) );
      return;
    }
  }

  /**
   * Helper method to pad/truncate strings
   *
   * @param s   String to modify
   * @param pad character to pad with
   * @param len length of final string
   * @return final String
   */
  private static String getFixedLengthString( String s, char pad, int len ) {

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
