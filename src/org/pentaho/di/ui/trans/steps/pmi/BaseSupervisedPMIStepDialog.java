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

package org.pentaho.di.ui.trans.steps.pmi;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
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
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Props;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.errorhandling.StreamInterface;
import org.pentaho.di.trans.steps.pmi.BaseSupervisedPMIStepData;
import org.pentaho.di.trans.steps.pmi.BaseSupervisedPMIStepMeta;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.core.dialog.ShowMessageDialog;
import org.pentaho.di.ui.core.widget.ColumnInfo;
import org.pentaho.di.ui.core.widget.ComboVar;
import org.pentaho.di.ui.core.widget.TableView;
import org.pentaho.di.ui.core.widget.TextVar;
import org.pentaho.di.ui.trans.step.BaseStepDialog;
import org.pentaho.dm.commons.ArffMeta;
import org.pentaho.pmi.Evaluator;
import org.pentaho.pmi.PMIEngine;
import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SchemeUtils;
import org.pentaho.pmi.UnsupportedEngineException;
import weka.core.Attribute;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;
import weka.filters.unsupervised.attribute.MergeInfrequentNominalValues;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.filters.unsupervised.attribute.StringToWordVector;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.pentaho.di.core.Const.MARGIN;

/**
 * Base dialog class for PMI classification and regression steps.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class BaseSupervisedPMIStepDialog extends BaseStepDialog implements StepDialogInterface {

  private static Class<?> PKG = BaseSupervisedPMIStepMeta.class;

  protected CTabFolder m_container;

  /**
   * Individual tabs (Configure - engine selection & row handling opts); fields - input field & class; scheme - scheme opts & model output path stuff;
   * evaluation - eval stuff; preprocessing - preprocessing stuff
   */
  protected CTabItem m_configureTab, m_fieldsTab, m_schemeTab, m_evaluationTab, m_preprocessingTab;
  protected Composite m_configureComposite, m_fieldsComposite, m_schemeComposite, m_preprocessingComposite,
      m_evaluationComposite;

  /**
   * Group for scheme parameter widgets
   */
  protected Group m_schemeGroup;

  /**
   * Engine drop-down
   */
  protected ComboVar m_engineDropDown;

  /**
   * Rows to process drop-down
   */
  protected ComboVar m_rowsToProcessDropDown;

  /**
   * Stratification drop-down
   */
  protected ComboVar m_stratificationFieldDropDown;

  /**
   * Batch size field
   */
  protected TextVar m_batchSizeField;

  /**
   * Reservoir size field
   */
  protected TextVar m_reservoirSizeField;

  /**
   * Reservoir sampling checkbox
   */
  protected Button m_reservoirSamplingBut;

  /**
   * Random seed field
   */
  protected TextVar m_reservoirRandomSeedField;

  /**
   * Table for incoming fields & arff types
   */
  protected TableView m_fieldsTable;

  /**
   * Relation name for the training data
   */
  protected TextVar m_relationNameField;

  /**
   * Field for selecting the class/target
   */
  protected ComboVar m_classFieldDropDown;

  /**
   * Select upstream step for training data
   */
  protected ComboVar m_trainingStepDropDown;

  /**
   * Select upstream step for separate test set
   */
  protected ComboVar m_testStepDropDown;

  /**
   * Field for specifying the directory to save the model to
   */
  protected TextVar m_modelOutputDirectoryField;

  /**
   * Button for browsing to model files
   */
  protected Button m_browseModelOutputDirectoryButton;

  /**
   * Field for specifying the filename when saving the model
   */
  protected TextVar m_modelFilenameField;

  /**
   * Field that appears only for incremental learning schemes - allows the initial n rows to be cached and used
   * initially to determine legal values for nominal fields before being passed to the scheme for training. This is
   * only necessary if there are incoming string fields for which the user intends to be treated as nominal but for
   * which they have not explicitly specified legal values.
   */
  protected TextVar m_incrementalRowCacheField;

  /**
   * Resample checkbox
   */
  protected Button m_resampleCheck;

  /**
   * Popup config for resampling
   */
  protected Button m_resampleConfig;

  /**
   * Checkbox for removing useless attributes
   */
  protected Button m_removeUselessCheck;

  /**
   * Popup config for remove useless filter
   */
  protected Button m_removeUselessConfig;

  /**
   * Remove infrequent values checkbox
   */
  protected Button m_mergeInfrequentValsCheck;

  /**
   * Popup config for remove infrequent values filter
   */
  protected Button m_mergeInfrequentValsConfig;

  /**
   * text vectorization checkbox
   */
  protected Button m_stringToWordVectorCheck;
  /**
   * Popup config for text vectorization options
   */
  protected Button m_stringToWordVectorConfig;

  /**
   * Select evaluation mode
   */
  protected ComboVar m_evalModeDropDown;

  /**
   * Percentage split to use
   */
  protected TextVar m_percentageSplitField;

  /**
   * Number of cross-validation folds to use
   */
  protected TextVar m_xValFoldsField;

  /**
   * Random seed to use for percentage split and x-val
   */
  protected TextVar m_randomSeedField;

  /**
   * Checkbox for outputting AUC metrics - if performing evaluation
   */
  protected Button m_outputAUCMetricsCheck;

  /**
   * Checkbox for outputting IR metrics - if performing evaluation
   */
  protected Button m_outputIRMetricsCheck;

  /**
   * Resample can be supervised or unsupervised - we have to switch based on the selected class type
   */
  protected Filter m_resample;

  protected RemoveUseless m_removeUselessFilter;
  protected MergeInfrequentNominalValues m_mergeInfrequentNominalValsFilter;
  protected StringToWordVector m_stringToWordVectorFilter;

  protected static int MIDDLE;
  protected static final int FIRST_LABEL_RIGHT_PERCENTAGE = 35;
  protected static final int FIRST_PROMPT_RIGHT_PERCENTAGE = 55; // 55
  protected static final int SECOND_LABEL_RIGHT_PERCENTAGE = 65; // 65
  protected static final int SECOND_PROMPT_RIGHT_PERCENTAGE = 80;
  protected static final int THIRD_PROMPT_RIGHT_PERCENTAGE = 90;

  protected static final int GOE_FIRST_BUTTON_RIGHT_PERCENTAGE = 70;
  protected static final int GOE_SECOND_BUTTON_RIGHT_PERCENTAGE = 80;

  private Control lastControl;

  protected BaseSupervisedPMIStepMeta m_originalMeta;
  protected BaseSupervisedPMIStepMeta m_inputMeta;

  /**
   * Current contents of the config scheme tab
   */
  protected Map<String, Object> m_schemeWidgets = new LinkedHashMap<>();

  /**
   * The current scheme's info/paramemter metadata
   */
  protected Map<String, Object> m_topLevelSchemeInfo;

  /**
   * The actual top-level scheme
   */
  protected Scheme m_scheme;

  /**
   * Sampling (instance) filter(s) in use
   */
  protected List<Filter> m_samplingFilters;

  /**
   * Preprocessing (attribute) filters in use
   */
  protected List<Filter> m_preprocessingFilters;

  protected ModifyListener m_simpleModifyListener = new ModifyListener() {
    @Override public void modifyText( ModifyEvent modifyEvent ) {
      m_inputMeta.setChanged();
    }
  };

  protected SelectionAdapter m_simpleSelectionAdapter = new SelectionAdapter() {
    @Override public void widgetDefaultSelected( SelectionEvent selectionEvent ) {
      super.widgetDefaultSelected( selectionEvent );
      ok();
    }
  };

  public BaseSupervisedPMIStepDialog( Shell parent, Object inMeta, TransMeta tr, String stepName ) {
    super( parent, (BaseStepMeta) inMeta, tr, stepName );

    m_inputMeta = (BaseSupervisedPMIStepMeta) inMeta;
    m_originalMeta = (BaseSupervisedPMIStepMeta) m_inputMeta.clone();
  }

  @Override public String open() {

    // display, step name etc.
    initialDialogSetup();
    addConfigTab();
    addFieldsTab();
    addSchemeTab();
    addPreprocessingTab();
    addEvaluationTab();

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( wStepname, MARGIN );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( 100, -50 );
    m_container.setLayoutData( fd );

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

    wStepname.addSelectionListener( lsDef );

    // Detect X or ALT-F4 or something that kills this window...
    shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent e ) {
        cancel();
      }
    } );

    boolean okEngine = getData( m_inputMeta );
    populateSchemeTab( !okEngine, m_inputMeta );
    setEvaluationModeFromMeta( m_inputMeta );

    m_inputMeta.setChanged( changed );
    m_container.setSelection( 0 );

    m_container.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        if ( selectionEvent.item.equals( m_evaluationTab ) ) {
          checkWidgets();
        }
      }
    } );

    checkWidgets();
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

  protected void setData( BaseSupervisedPMIStepMeta meta ) {
    meta.setEngineName( m_engineDropDown.getText() );
    meta.setRowsToProcess( m_rowsToProcessDropDown.getText() );
    meta.setBatchSize( m_batchSizeField.getText() );
    meta.setUseReservoirSampling( m_reservoirSamplingBut.getSelection() );
    meta.setReservoirSize( m_reservoirSizeField.getText() );
    meta.setRandomSeedReservoirSampling( m_reservoirRandomSeedField.getText() );

    meta.setTrainingStepInputName( m_trainingStepDropDown.getText() );
    meta.setTestingStepInputName( m_testStepDropDown.getText() );
    meta.setClassField( m_classFieldDropDown.getText() );
    meta.setStratificationFieldName( m_stratificationFieldDropDown.getText() );

    meta.clearStepIOMeta();
    List<StreamInterface> infoStreams = meta.getStepIOMeta().getInfoStreams();
    String trainingStepName = meta.getTrainingStepInputName();
    if ( !Const.isEmpty( trainingStepName ) ) {
      StepMeta m = transMeta.findStep( transMeta.environmentSubstitute( trainingStepName ) );
      if ( m != null ) {
        infoStreams.get( 0 ).setStepMeta( m );
      }
    }
    String testStepName = meta.getTestingStepInputName();
    if ( !Const.isEmpty( testStepName ) ) {
      StepMeta m = transMeta.findStep( transMeta.environmentSubstitute( testStepName ) );
      if ( m != null ) {
        infoStreams.get( 1 ).setStepMeta( m );
      }
    }

    List<ArffMeta> userFields = new ArrayList<>();
    int numNonEmpty = m_fieldsTable.nrNonEmpty();
    for ( int i = 0; i < numNonEmpty; i++ ) {
      TableItem item = m_fieldsTable.getNonEmpty( i );

      String fieldName = item.getText( 1 );
      int kettleType = ValueMetaFactory.getIdForValueMeta( item.getText( 2 ) );
      int arffType = getArffTypeInt( item.getText( 3 ) );
      String nomVals = item.getText( 4 );
      ArffMeta newArffMeta = new ArffMeta( fieldName, kettleType, arffType );
      if ( !Const.isEmpty( nomVals ) ) {
        newArffMeta.setNominalVals( nomVals );
      }
      userFields.add( newArffMeta );
    }
    meta.setFieldMetadata( userFields );

    meta.setModelOutputPath( m_modelOutputDirectoryField.getText() );
    meta.setModelFileName( m_modelFilenameField.getText() );

    // preprocessing filters
    m_samplingFilters.clear();
    m_preprocessingFilters.clear();
    if ( m_resampleCheck.getSelection() ) {
      m_samplingFilters.add( m_resample );
    }
    if ( m_removeUselessCheck.getSelection() ) {
      m_preprocessingFilters.add( m_removeUselessFilter );
    }
    if ( m_mergeInfrequentValsCheck.getSelection() ) {
      m_preprocessingFilters.add( m_mergeInfrequentNominalValsFilter );
    }
    if ( m_stringToWordVectorCheck.getSelection() ) {
      m_preprocessingFilters.add( m_stringToWordVectorFilter );
    }
    meta.setSamplingConfigs( SchemeUtils.filterListToConfigs( m_samplingFilters ) );
    meta.setPreprocessingConfigs( SchemeUtils.filterListToConfigs( m_preprocessingFilters ) );

    String evalMode = m_evalModeDropDown.getText();
    Evaluator.EvalMode toSet = Evaluator.EvalMode.NONE;
    for ( Evaluator.EvalMode e : Evaluator.EvalMode.values() ) {
      if ( evalMode.equalsIgnoreCase( e.toString() ) ) {
        toSet = e;
        break;
      }
    }
    meta.setEvalMode( toSet );

    meta.setXValFolds( m_xValFoldsField.getText() );
    meta.setPercentageSplit( m_percentageSplitField.getText() );
    meta.setRandomSeed( m_randomSeedField.getText() );
    meta.setOutputAUCMetrics( m_outputAUCMetricsCheck.getSelection() );
    meta.setOutputIRMetrics( m_outputIRMetricsCheck.getSelection() );

    // Algorithm options - populates the 'properties' map from the widgets and then sets these
    // values on the scheme itself
    GOEDialog.widgetValuesToPropsMap( m_scheme, m_topLevelSchemeInfo, m_schemeWidgets );

    String[] schemeOpts = m_scheme.getSchemeOptions();
    if ( schemeOpts != null && schemeOpts.length > 0 ) {
      meta.setSchemeCommandLineOptions( Utils.joinOptions( schemeOpts ) );
    }

    if ( m_incrementalRowCacheField != null ) {
      meta.setInitialRowCacheForNominalValDetermination( m_incrementalRowCacheField.getText() );
    }
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

  protected boolean getData( BaseSupervisedPMIStepMeta meta ) {
    boolean engineOK = true;
    List<String> availEngines = Arrays.asList( m_engineDropDown.getItems() );
    if ( availEngines.contains( meta.getEngineName() ) ) {
      m_engineDropDown.setText( meta.getEngineName() );
    } else {
      m_engineDropDown.setText( m_engineDropDown.getItems()[0] );
      engineOK = false;
    }
    m_rowsToProcessDropDown.setText( meta.getRowsToProcess() );
    m_batchSizeField.setText( meta.getBatchSize() );
    m_reservoirSamplingBut.setSelection( meta.getUseReservoirSampling() );
    m_reservoirSizeField.setText( meta.getReservoirSize() );
    m_reservoirRandomSeedField.setText( meta.getRandomSeedReservoirSampling() );

    m_trainingStepDropDown.setText( meta.getTrainingStepInputName() );
    m_testStepDropDown.setText( meta.getTestingStepInputName() );
    m_classFieldDropDown.setText( meta.getClassField() );
    m_stratificationFieldDropDown.setText( meta.getStratificationFieldName() );

    List<ArffMeta> userFields = meta.getFieldMetadata();
    if ( userFields.size() > 0 ) {
      m_fieldsTable.clearAll();

      for ( ArffMeta m : userFields ) {
        TableItem item = new TableItem( m_fieldsTable.table, SWT.NONE );
        item.setText( 1, Const.NVL( m.getFieldName(), "" ) );
        item.setText( 2, Const.NVL( ValueMetaFactory.getValueMetaName( m.getKettleType() ), "" ) );
        item.setText( 3, Const.NVL( BaseSupervisedPMIStepData.typeToString( m.getArffType() ), "" ) );
        if ( !Const.isEmpty( m.getNominalVals() ) ) {
          item.setText( 4, m.getNominalVals() );
        }
      }

      m_fieldsTable.removeEmptyRows();
      m_fieldsTable.setRowNums();
      m_fieldsTable.optWidth( true );
    }

    if ( !Const.isEmpty( m_trainingStepDropDown.getText() ) ) {
      populateClassAndStratCombos();
    }

    // Algo config is taken care of already
    if ( !Const.isEmpty( meta.getModelOutputPath() ) ) {
      m_modelOutputDirectoryField.setText( meta.getModelOutputPath() );
    }

    if ( !Const.isEmpty( meta.getModelFileName() ) ) {
      m_modelFilenameField.setText( meta.getModelFileName() );
    }

    // Preprocessing
    // sets options on these filters based on what is stored in meta. Also sets the status of the
    // checkboxes for each filter
    try {
      setOptionsForPreprocessingFromMeta( meta );
    } catch ( Exception e ) {
      e.printStackTrace();
      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemSettingPreprocessingOptions.Title" ),
              e.getMessage(), false );
      smd.open();
    }

    // Evaluation
    m_xValFoldsField.setText( meta.getXValFolds() );
    m_percentageSplitField.setText( meta.getPercentageSplit() );
    m_randomSeedField.setText( meta.getRandomSeed() );
    m_outputAUCMetricsCheck.setSelection( meta.getOutputAUCMetrics() );
    m_outputIRMetricsCheck.setSelection( meta.getOutputIRMetrics() );

    return engineOK;
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
    shell.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Shell.Title", m_inputMeta.getSchemeName() ) );

    MIDDLE = props.getMiddlePct();

    // Stepname line
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

    m_container = new CTabFolder( shell, SWT.BORDER );
    props.setLook( m_container, Props.WIDGET_STYLE_TAB );
    m_container.setSimple( false );
  }

  protected void addConfigTab() {
    m_configureTab = new CTabItem( m_container, SWT.NONE );
    m_configureTab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ConfigTab.TabTitle" ) );
    m_configureComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_configureComposite );
    FormLayout fl = new FormLayout();
    fl.marginWidth = 3;
    fl.marginHeight = 3;
    m_configureComposite.setLayout( fl );

    // engine group
    Group engGroup = new Group( m_configureComposite, SWT.SHADOW_NONE );
    props.setLook( engGroup );
    engGroup.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ConfigTab.EngineGroup" ) );
    fl = new FormLayout();
    fl.marginWidth = 10;
    fl.marginHeight = 10;
    engGroup.setLayout( fl );
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( 0, 0 );
    engGroup.setLayoutData( fd );

    Label engineLab = new Label( engGroup, SWT.RIGHT );
    engineLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Engine.Label" ) );
    props.setLook( engineLab );
    engineLab.setLayoutData( getFirstLabelFormData() );

    m_engineDropDown = new ComboVar( transMeta, engGroup, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_engineDropDown );
    m_engineDropDown.setEditable( false );
    m_engineDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        m_inputMeta.setChanged();
        checkWidgets();
        populateSchemeTab( true, m_inputMeta );
      }
    } );

    m_engineDropDown.setLayoutData( getFirstPromptFormData( engineLab ) );
    List<String> engineNames = PMIEngine.getEngineNames();
    List<String> engineProbsExceptions = new ArrayList<>();
    List<String> problematicEngines = new ArrayList<>();
    String schemeName = m_originalMeta.getSchemeName();
    for ( String engineN : engineNames ) {
      try {
        PMIEngine eng = PMIEngine.getEngine( engineN );
        if ( eng.engineAvailable( engineProbsExceptions ) ) {
          if ( eng.supportsScheme( schemeName ) ) {
            m_engineDropDown.add( engineN );
          }
        } else {
          problematicEngines.add( eng.engineName() );
        }
      } catch ( UnsupportedEngineException e ) {
        e.printStackTrace();
        engineProbsExceptions.add( e.getMessage() );
        problematicEngines.add( engineN );
      }
    }

    if ( problematicEngines.size() > 0 ) {
      StringBuilder b = new StringBuilder();
      for ( String n : problematicEngines ) {
        b.append( n ).append( " " );
      }
      showMessageDialog( BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnavailableEngineTitle" ),
          BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnavailableEngineMessage", b.toString() ),
          SWT.OK | SWT.ICON_INFORMATION, true );
    }

    // row handling group
    Group rowGroup = new Group( m_configureComposite, SWT.SHADOW_NONE );
    props.setLook( rowGroup );
    rowGroup.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ConfigTab.RowHandlingGroup" ) );
    fl = new FormLayout();
    fl.marginWidth = 10;
    fl.marginHeight = 10;
    rowGroup.setLayout( fl );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( engGroup, MARGIN );
    rowGroup.setLayoutData( fd );

    Label rowsToProcLab = new Label( rowGroup, SWT.RIGHT );
    rowsToProcLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RowsToProcess.Label" ) );
    props.setLook( rowsToProcLab );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( FIRST_LABEL_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( 0, MARGIN );
    rowsToProcLab.setLayoutData( fd );

    m_rowsToProcessDropDown = new ComboVar( transMeta, rowGroup, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_rowsToProcessDropDown );
    m_rowsToProcessDropDown.setEditable( false );
    m_rowsToProcessDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        m_inputMeta.setChanged();
        handleRowsToProcessChange();
      }
    } );
    m_rowsToProcessDropDown.setLayoutData( getFirstPromptFormData( rowsToProcLab ) );
    m_rowsToProcessDropDown
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.TipText" ) );
    m_rowsToProcessDropDown
        .add( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.AllEntry.Label" ) );
    m_rowsToProcessDropDown
        .add( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.BatchEntry.Label" ) );
    m_rowsToProcessDropDown
        .add( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.StratifiedEntry.Label" ) );

    Label rowsToProcessSizeLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( rowsToProcessSizeLab );
    rowsToProcessSizeLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcessSize.Label" ) );
    rowsToProcessSizeLab.setLayoutData( getSecondLabelFormData( m_rowsToProcessDropDown ) );

    m_batchSizeField = new TextVar( transMeta, rowGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_batchSizeField );
    m_batchSizeField.addModifyListener( m_simpleModifyListener );
    m_batchSizeField.setLayoutData( getSecondPromptFormData( rowsToProcessSizeLab ) );
    m_batchSizeField.setEnabled( false );
    lastControl = m_batchSizeField;

    // reservoir sampling
    Label reservoirSamplingLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( reservoirSamplingLab );
    reservoirSamplingLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSampling.Label" ) );
    reservoirSamplingLab.setLayoutData( getFirstLabelFormData() );

    m_reservoirSamplingBut = new Button( rowGroup, SWT.CHECK );
    props.setLook( m_reservoirSamplingBut );
    fd = getFirstPromptFormData( reservoirSamplingLab );
    fd.right = null;
    m_reservoirSamplingBut.setLayoutData( fd );
    m_reservoirSamplingBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        m_inputMeta.setChanged();
        handleReservoirSamplingChange();
      }
    } );
    m_reservoirSamplingBut
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSampling.TipText" ) );

    Label reservoirSamplingSizeLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( reservoirSamplingSizeLab );
    reservoirSamplingSizeLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSamplingSize.Label" ) );
    reservoirSamplingSizeLab.setLayoutData( getSecondLabelFormData( m_reservoirSamplingBut ) );

    m_reservoirSizeField = new TextVar( transMeta, rowGroup, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( m_reservoirSizeField );
    m_reservoirSizeField
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.ReservoirSamplingSize.TipText" ) );
    m_reservoirSizeField.setLayoutData( getSecondPromptFormData( reservoirSamplingSizeLab ) );
    m_reservoirSizeField.setEnabled( false );
    lastControl = m_reservoirSizeField;
    m_reservoirSizeField.addModifyListener( new ModifyListener() {
      @Override public void modifyText( ModifyEvent modifyEvent ) {
        m_inputMeta.setChanged();
      }
    } );

    Label randomSeedLab = new Label( rowGroup, SWT.RIGHT );
    props.setLook( randomSeedLab );
    randomSeedLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeedReservoirSampling.Label" ) );
    randomSeedLab.setLayoutData( getFirstLabelFormData() );

    m_reservoirRandomSeedField = new TextVar( transMeta, rowGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_reservoirRandomSeedField );
    m_reservoirRandomSeedField
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeedReservoirSampling.TipText" ) );
    m_reservoirRandomSeedField.setLayoutData( getFirstPromptFormData( randomSeedLab ) );
    m_reservoirRandomSeedField.setEnabled( true );
    lastControl = m_reservoirRandomSeedField;

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_configureComposite.setLayoutData( fd );
    m_configureComposite.layout();

    m_configureTab.setControl( m_configureComposite );
  }

  protected void addFieldsTab() {

    m_fieldsTab = new CTabItem( m_container, SWT.NONE );
    m_fieldsTab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.FieldsTab.Title" ) );

    m_fieldsComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_fieldsComposite );
    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_fieldsComposite.setLayout( fl );

    Label fieldsTableLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( fieldsTableLab );
    fieldsTableLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.FieldsTable.Label" ) );

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, MARGIN );
    fieldsTableLab.setLayoutData( fd );

    // Stratification field
    Label stratificationLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( stratificationLab );
    stratificationLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Stratification.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( 100, -MARGIN * 2 );
    stratificationLab.setLayoutData( fd );

    m_stratificationFieldDropDown = new ComboVar( transMeta, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    m_stratificationFieldDropDown.setEditable( true );
    props.setLook( m_stratificationFieldDropDown );
    fd = getFirstPromptFormData( stratificationLab );
    fd.top = null;
    fd.bottom = new FormAttachment( 100, -MARGIN * 2 );
    m_stratificationFieldDropDown.setLayoutData( fd );
    m_stratificationFieldDropDown
        .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.Stratification.TipText" ) );

    // class field
    Label classLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( classLab );
    classLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Class.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( m_stratificationFieldDropDown, -MARGIN );
    classLab.setLayoutData( fd );

    m_classFieldDropDown = new ComboVar( transMeta, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    m_classFieldDropDown.setEditable( true );
    props.setLook( m_classFieldDropDown );
    fd = getFirstPromptFormData( classLab );
    fd.top = null;
    fd.bottom = new FormAttachment( m_stratificationFieldDropDown, -MARGIN );
    m_classFieldDropDown.setLayoutData( fd );

    // separate test set step field
    Label separateTestLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( separateTestLab );
    separateTestLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.SeparateTest.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( m_classFieldDropDown, -MARGIN );
    separateTestLab.setLayoutData( fd );

    m_testStepDropDown = new ComboVar( transMeta, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_testStepDropDown );
    fd = getFirstPromptFormData( separateTestLab );
    fd.top = null;
    fd.bottom = new FormAttachment( m_classFieldDropDown, -MARGIN );
    m_testStepDropDown.setLayoutData( fd );

    // training set step field
    Label trainingLab = new Label( m_fieldsComposite, SWT.RIGHT );
    props.setLook( trainingLab );
    trainingLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.Training.Label" ) );
    fd = getFirstLabelFormData();
    fd.top = null;
    fd.bottom = new FormAttachment( m_testStepDropDown, -MARGIN );
    trainingLab.setLayoutData( fd );

    m_trainingStepDropDown = new ComboVar( transMeta, m_fieldsComposite, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_trainingStepDropDown );
    fd = getFirstPromptFormData( trainingLab );
    fd.top = null;
    fd.bottom = new FormAttachment( m_testStepDropDown, -MARGIN );
    m_trainingStepDropDown.setLayoutData( fd );
    m_trainingStepDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        checkWidgets();
        populateClassAndStratCombos();
      }
    } );

    String[] previousStepNames = transMeta.getPrevStepNames( stepname );
    if ( previousStepNames != null ) {
      for ( String name : previousStepNames ) {
        m_trainingStepDropDown.add( name );
        m_testStepDropDown.add( name );
      }
    }

    wGet = new Button( m_fieldsComposite, SWT.PUSH );
    props.setLook( wGet );
    wGet.setText( BaseMessages.getString( PKG, "System.Button.GetFields" ) );
    wGet.setToolTipText( BaseMessages.getString( PKG, "System.Tooltip.GetFields" ) );
    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( MIDDLE, -MARGIN );
    fd.bottom = new FormAttachment( m_trainingStepDropDown, -MARGIN );
    wGet.setLayoutData( fd );
    wGet.setEnabled( true );

    wGet.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        populateFieldsTable();
      }
    } );

    final int fieldsRows = 1;

    ColumnInfo[]
        colinf =
        new ColumnInfo[] { new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputFieldsColumn.Name" ),
            ColumnInfo.COLUMN_TYPE_TEXT, false ),
            new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputFieldsColumn.KettleType" ),
                ColumnInfo.COLUMN_TYPE_TEXT, false ),
            new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputFieldsColumn.ArffType" ),
                ColumnInfo.COLUMN_TYPE_CCOMBO, true ),
            new ColumnInfo( BaseMessages.getString( PKG, "BasePMIStepFlowDialog.OutputFieldsColumn.NomVals" ),
                ColumnInfo.COLUMN_TYPE_TEXT, false ) };
    colinf[0].setReadOnly( true );
    colinf[1].setReadOnly( true );
    colinf[2].setReadOnly( false );
    colinf[3].setReadOnly( false );

    colinf[2].setComboValues( new String[] { BaseMessages.getString( PKG, "PMIScoringDialog.attributeType.Numeric" ),
        BaseMessages.getString( PKG, "PMIScoringDialog.attributeType.Nominal" ),
        BaseMessages.getString( PKG, "PMIScoringDialog.attributeType.String" ) } );

    m_fieldsTable =
        new TableView( transMeta, m_fieldsComposite, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI, colinf, fieldsRows,
            new ModifyListener() {
              @Override public void modifyText( ModifyEvent modifyEvent ) {
                m_inputMeta.setChanged();
              }
            }, props );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( fieldsTableLab, MARGIN );
    fd.right = new FormAttachment( 100, 0 );
    fd.bottom = new FormAttachment( wGet, -MARGIN * 2 );
    m_fieldsTable.setLayoutData( fd );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_fieldsComposite.setLayoutData( fd );
    m_fieldsComposite.layout();

    m_fieldsTab.setControl( m_fieldsComposite );

    // TODO set enabled status of various stuff (after getData())
  }

  protected void addSchemeTab() {
    m_schemeTab = new CTabItem( m_container, SWT.NONE );
    m_schemeTab.setText( BaseMessages.getString( PKG, "BasePMIStep.SchemeTab.Title" ) );

    m_schemeComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_schemeComposite );
    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_schemeComposite.setLayout( fl );

    m_schemeGroup = new Group( m_schemeComposite, SWT.SHADOW_NONE );
    props.setLook( m_schemeGroup );
    m_schemeGroup.setText( m_inputMeta.getSchemeName() + ( Const.isEmpty( m_engineDropDown.getText() ) ? "" :
        " (" + m_engineDropDown.getText() + ")" ) );
    fl = new FormLayout();
    fl.marginWidth = 10;
    fl.marginHeight = 10;
    m_schemeGroup.setLayout( fl );
    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.top = new FormAttachment( 0, 0 );
    m_schemeGroup.setLayoutData( fd );

    fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_schemeComposite.setLayoutData( fd );
    m_schemeComposite.layout();

    Label modelOutputDirectoryLab = new Label( m_schemeComposite, SWT.RIGHT );
    props.setLook( modelOutputDirectoryLab );
    modelOutputDirectoryLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputModelDirectory.Label" ) );
    lastControl = m_schemeGroup;
    modelOutputDirectoryLab.setLayoutData( getFirstLabelFormData() );

    m_modelOutputDirectoryField = new TextVar( transMeta, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_modelOutputDirectoryField );
    m_modelOutputDirectoryField.setLayoutData( getFirstPromptFormData( modelOutputDirectoryLab ) );

    m_browseModelOutputDirectoryButton = new Button( m_schemeComposite, SWT.PUSH );
    props.setLook( m_browseModelOutputDirectoryButton );
    m_browseModelOutputDirectoryButton
        .setText( BaseMessages.getString( PKG, "BasePMIStepDialog.BrowseModelOutputDirectory.Button" ) );
    m_browseModelOutputDirectoryButton.setLayoutData( getSecondLabelFormData( m_modelOutputDirectoryField ) );

    m_browseModelOutputDirectoryButton.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        DirectoryDialog dialog = new DirectoryDialog( shell, SWT.SAVE );

        if ( !Const.isEmpty( m_modelOutputDirectoryField.getText() ) ) {
          boolean ok = false;
          String outputDir = transMeta.environmentSubstitute( m_modelOutputDirectoryField.getText() );
          File updatedPath = null;
          if ( outputDir.toLowerCase().startsWith( "file:" ) ) {
            outputDir = outputDir.replace( " ", "%20" );
            try {
              updatedPath = new File( new java.net.URI( outputDir ) );
              ok = true;
            } catch ( URISyntaxException e ) {
              e.printStackTrace();
            }
          } else {
            updatedPath = new File( outputDir );
            ok = true;
          }
          if ( ok && updatedPath.exists() && updatedPath.isDirectory() ) {
            dialog.setFilterPath( updatedPath.toString() );
          }
        }

        String selectedDirectory = dialog.open();
        if ( selectedDirectory != null ) {
          m_modelOutputDirectoryField.setText( selectedDirectory );
        }
      }
    } );
    lastControl = m_modelOutputDirectoryField;

    Label modelOutputFilenameLab = new Label( m_schemeComposite, SWT.RIGHT );
    modelOutputFilenameLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.OutputModelFilename.Label" ) );
    props.setLook( modelOutputDirectoryLab );
    modelOutputFilenameLab.setLayoutData( getFirstLabelFormData() );

    m_modelFilenameField = new TextVar( transMeta, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_modelFilenameField );
    m_modelFilenameField.setLayoutData( getFirstPromptFormData( modelOutputFilenameLab ) );

    m_schemeTab.setControl( m_schemeComposite );
  }

  @SuppressWarnings( "unchecked" )
  protected void populateSchemeTab( boolean engineChange, BaseSupervisedPMIStepMeta stepMeta ) {
    for ( Control k : m_schemeGroup.getChildren() ) {
      k.dispose();
    }
    m_schemeWidgets.clear();

    String currentEngine = m_engineDropDown.getText();
    if ( !Const.isEmpty( currentEngine ) ) {
      try {
        PMIEngine eng = PMIEngine.getEngine( currentEngine );
        Scheme scheme = eng.getScheme( m_originalMeta.getSchemeName() );

        // Only configure with the current meta scheme options if the engine has not changed
        if ( !engineChange && !Const.isEmpty( stepMeta.getSchemeCommandLineOptions() ) ) {
          scheme.setSchemeOptions( Utils.splitOptions( stepMeta.getSchemeCommandLineOptions() ) );
        }
        m_topLevelSchemeInfo = scheme.getSchemeInfo();
        m_scheme = scheme;

        lastControl = null;
        String helpInfo = (String) m_topLevelSchemeInfo.get( "helpSummary" );
        String helpSynopsis = (String) m_topLevelSchemeInfo.get( "helpSynopsis" );
        if ( !Const.isEmpty( helpInfo ) ) {
          Group helpGroup = new Group( m_schemeGroup, SWT.SHADOW_NONE );
          props.setLook( helpGroup );
          helpGroup.setText( "About" );
          FormLayout fl = new FormLayout();
          fl.marginWidth = 10;
          fl.marginHeight = 10;
          helpGroup.setLayout( fl );
          FormData fd = new FormData();
          fd.left = new FormAttachment( 0, 0 );
          fd.right = new FormAttachment( 100, 0 );
          fd.top = new FormAttachment( 0, 0 );
          helpGroup.setLayoutData( fd );

          // TODO do this properly at some stage...
          Button moreButton = null;
          /* if ( !Const.isEmpty( helpSynopsis ) ) {
            moreButton = new Button( helpGroup, SWT.PUSH );
            props.setLook( moreButton );
            moreButton.setText( "More..." );
            fd = new FormData();
            fd.top = new FormAttachment( 0, 4 );
            fd.right = new FormAttachment( 100, -4 );
            moreButton.setLayoutData( fd );

            moreButton.addSelectionListener( new SelectionAdapter() {
              @Override public void widgetSelected( SelectionEvent selectionEvent ) {
                // TODO popup "more" window
              }
            } );
          } */

          Label aboutLab = new Label( helpGroup, SWT.LEFT );
          props.setLook( aboutLab );
          aboutLab.setText( helpInfo );
          fd = new FormData();
          fd.top = new FormAttachment( 0, 4 );
          fd.left = new FormAttachment( 0, 0 );
          fd.right = moreButton != null ? new FormAttachment( moreButton, -4 ) : new FormAttachment( 100, -4 );
          aboutLab.setLayoutData( fd );
          lastControl = helpGroup;
        }

        final Map<String, Map<String, Object>>
            properties =
            (Map<String, Map<String, Object>>) m_topLevelSchemeInfo.get( "properties" );
        // lastControl = null;
        for ( Map.Entry<String, Map<String, Object>> e : properties.entrySet() ) {
          final String propName = e.getKey();
          final Map<String, Object> propDetails = e.getValue();
          String tipText = (String) propDetails.get( "tip-text" );
          String type = (String) propDetails.get( "type" );
          Object value = propDetails.get( "value" );

          Label propLabel = new Label( m_schemeGroup, SWT.RIGHT );
          props.setLook( propLabel );
          propLabel.setText( propName );
          if ( !Const.isEmpty( tipText ) ) {
            propLabel.setToolTipText( tipText );
          }
          propLabel.setLayoutData( getFirstLabelFormData() );

          // everything apart from object, array and pick-list is handled by a text field
          if ( type.equalsIgnoreCase( "object" ) ) {
            String objectTextRep = value.toString();
            Object objectValue = propDetails.get( "objectValue" );
            final String goeBaseType = propDetails.get( "goeBaseType" ).toString();
            final Label objectValueLab = new Label( m_schemeGroup, SWT.RIGHT );
            props.setLook( objectValueLab );
            objectValueLab.setText( objectTextRep );
            objectValueLab.setLayoutData( getFirstPromptFormData( propLabel ) );

            final Button objectValEditBut = new Button( m_schemeGroup, SWT.PUSH );
            props.setLook( objectValEditBut );
            objectValEditBut.setText( "Edit..." /*+ objectTextRep */ );
            // objectValEditBut.setLayoutData( getSecondPromptFormData( objectValueLab ) );
            objectValEditBut.setLayoutData( getFirstGOEFormData( objectValueLab ) );

            final Button objectChooseBut = new Button( m_schemeGroup, SWT.PUSH );
            props.setLook( objectChooseBut );
            objectChooseBut.setText( "Choose..." );
            // objectChooseBut.setLayoutData( getThirdPropmtFormData( objectValEditBut ) );
            objectChooseBut.setLayoutData( getSecondGOEFormData( objectValEditBut ) );
            objectChooseBut.addSelectionListener( new SelectionAdapter() {
              @Override public void widgetSelected( SelectionEvent selectionEvent ) {
                super.widgetSelected( selectionEvent );
                Object selectedObject = null;
                try {
                  objectChooseBut.setEnabled( false );
                  objectValEditBut.setEnabled( false );
                  GOETree treeDialog = new GOETree( shell, SWT.OK | SWT.CANCEL, goeBaseType );
                  int result = treeDialog.open();
                  if ( result == SWT.OK ) {
                    Object selectedTreeValue = treeDialog.getSelectedTreeObject();
                    if ( selectedTreeValue != null ) {
                      Map<String, Object> propDetails = properties.get( propName );
                      if ( propDetails != null ) {
                        propDetails.put( "objectValue", selectedTreeValue );
                      }
                    }
                    objectValueLab.setText( SchemeUtils.getTextRepresentationOfObjectValue( selectedTreeValue ) );
                  }
                } catch ( Exception ex ) {
                  // TODO popup error dialog
                  ex.printStackTrace();
                } finally {
                  objectChooseBut.setEnabled( true );
                  objectValEditBut.setEnabled( true );
                }
              }
            } );

            objectValEditBut.addSelectionListener( new SelectionAdapter() {
              @Override public void widgetSelected( SelectionEvent selectionEvent ) {
                super.widgetSelected( selectionEvent );
                objectValEditBut.setEnabled( false );
                objectChooseBut.setEnabled( false );
                try {
                  GOEDialog
                      dialog =
                      new GOEDialog( shell, SWT.OK | SWT.CANCEL, propDetails.get( "objectValue" ),
                          transMeta );
                  dialog.open();

                  objectValueLab
                      .setText( SchemeUtils.getTextRepresentationOfObjectValue( propDetails.get( "objectValue" ) ) );
                } catch ( Exception e1 ) {
                  e1.printStackTrace();
                } finally {
                  objectValEditBut.setEnabled( true );
                  objectChooseBut.setEnabled( true );
                }
              }
            } );

            lastControl = objectValEditBut;
          } else if ( type.equalsIgnoreCase( "array" ) ) {
            // TODO
          } else if ( type.equalsIgnoreCase( "pick-list" ) ) {
            String pickListValues = (String) propDetails.get( "pick-list-values" );
            String[] vals = pickListValues.split( "," );
            ComboVar pickListCombo = new ComboVar( transMeta, m_schemeGroup, SWT.BORDER | SWT.READ_ONLY );
            props.setLook( pickListCombo );
            for ( String v : vals ) {
              pickListCombo.add( v.trim() );
            }
            if ( value != null && value.toString().length() > 0 ) {
              pickListCombo.setText( value.toString() );
            }
            pickListCombo.addSelectionListener( new SelectionAdapter() {
              @Override public void widgetSelected( SelectionEvent selectionEvent ) {
                super.widgetSelected( selectionEvent );
                m_inputMeta.setChanged();
              }
            } );
            pickListCombo.setLayoutData( getFirstPromptFormData( propLabel ) );
            lastControl = pickListCombo;
            m_schemeWidgets.put( propName, pickListCombo );
          } else if ( type.equalsIgnoreCase( "boolean" ) ) {
            Button boolBut = new Button( m_schemeGroup, SWT.CHECK );
            props.setLook( boolBut );
            boolBut.setLayoutData( getFirstPromptFormData( propLabel ) );
            if ( value != null && value.toString().length() > 0 ) {
              boolBut.setSelection( Boolean.parseBoolean( value.toString() ) );
            }
            lastControl = boolBut;
            m_schemeWidgets.put( propName, boolBut );
            /* ComboVar pickListCombo = new ComboVar( transMeta, m_schemeGroup, SWT.BORDER | SWT.READ_ONLY );
            props.setLook( pickListCombo );
            pickListCombo.add( "true" );
            pickListCombo.add( "false" );
            if ( value != null && value.toString().length() > 0 ) {
              pickListCombo.setText( value.toString() );
            }
            pickListCombo.setLayoutData( getFirstPromptFormData( propLabel ) );
            lastControl = pickListCombo;
            m_schemeWidgets.put( propName, pickListCombo );
*/
          } else {
            TextVar propVar = new TextVar( transMeta, m_schemeGroup, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
            props.setLook( propVar );
            if ( value != null ) {
              propVar.setText( value.toString() );
            }
            propVar.addModifyListener( m_simpleModifyListener );
            propVar.setLayoutData( getFirstPromptFormData( propLabel ) );
            lastControl = propVar;
            m_schemeWidgets.put( propName, propVar );
          }
        }

      } catch ( Exception e ) {
        e.printStackTrace();
        ShowMessageDialog
            smd =
            new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
                BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemProcessingSchemeSettings.Title" ),
                e.getMessage(), false );
        smd.open();
      }
    }

    m_schemeGroup.layout();
    m_schemeComposite.layout();

    if ( m_scheme.supportsIncrementalTraining() ) {
      Label incrementalCacheLab = new Label( m_schemeComposite, SWT.RIGHT );
      props.setLook( incrementalCacheLab );
      incrementalCacheLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.IncrementalRowCacheSize.Label" ) );
      incrementalCacheLab
          .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.IncrementalRowCacheSize.TipText" ) );
      FormData fd = getFirstLabelFormData();
      fd.top = new FormAttachment( m_modelFilenameField, MARGIN );
      incrementalCacheLab.setLayoutData( fd );

      m_incrementalRowCacheField = new TextVar( transMeta, m_schemeComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
      props.setLook( m_incrementalRowCacheField );
      m_incrementalRowCacheField.addModifyListener( m_simpleModifyListener );
      fd = getFirstPromptFormData( incrementalCacheLab );
      fd.top = new FormAttachment( m_modelFilenameField, MARGIN );
      m_incrementalRowCacheField.setLayoutData( fd );

      m_incrementalRowCacheField.setText( m_inputMeta.getInitialRowCacheForNominalValDetermination() );
    }
  }

  protected void addPreprocessingTab() {
    m_preprocessingTab = new CTabItem( m_container, SWT.NONE );
    m_preprocessingTab.setText( BaseMessages.getString( PKG, "BasePMIStep.PreprocessingTab.Title" ) );

    m_preprocessingComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_preprocessingComposite );

    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_preprocessingComposite.setLayout( fl );

    try {
      m_samplingFilters = SchemeUtils.filterConfigsToList( m_originalMeta.getSamplingConfigs() );
      m_preprocessingFilters = SchemeUtils.filterConfigsToList( m_originalMeta.getPreprocessingConfigs() );

      // resample/class balance
      Label resampleLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( resampleLab );
      resampleLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ResampleFilter.Label" ) );
      resampleLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.ResampleFilter.TipText" ) );
      lastControl = null;
      resampleLab.setLayoutData( getFirstLabelFormData() );

      m_resampleCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_resampleCheck );
      m_resampleCheck.setLayoutData( getFirstPromptFormData( resampleLab ) );
      m_resampleCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_resampleConfig.setEnabled( m_resampleCheck.getSelection() );
        }
      } );

      m_resampleConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      m_resampleConfig.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.ResampleFilter.Button" ) );
      props.setLook( m_resampleConfig );
      m_resampleConfig.setLayoutData( getSecondPromptFormData( m_resampleCheck ) );
      m_resampleConfig.setEnabled( false );
      lastControl = m_resampleCheck;

      m_resampleConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_resample, m_resampleConfig );
        }
      } );

      Label removeUselessLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( removeUselessLab );
      removeUselessLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RemoveUselessFilter.Label" ) );
      removeUselessLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.RemoveUselessFilter.TipText" ) );
      removeUselessLab.setLayoutData( getFirstLabelFormData() );

      m_removeUselessCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_removeUselessCheck );
      m_removeUselessCheck.setLayoutData( getFirstPromptFormData( removeUselessLab ) );
      m_removeUselessCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_removeUselessConfig.setEnabled( m_removeUselessCheck.getSelection() );
        }
      } );

      m_removeUselessConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      props.setLook( m_removeUselessConfig );
      m_removeUselessConfig.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RemoveUselessFilter.Button" ) );
      m_removeUselessConfig.setEnabled( m_removeUselessCheck.getSelection() );
      m_removeUselessConfig.setLayoutData( getSecondPromptFormData( m_removeUselessCheck ) );
      lastControl = m_removeUselessCheck;

      m_removeUselessConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_removeUselessFilter, m_removeUselessConfig );
        }
      } );

      Label mergeInfequentLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( mergeInfequentLab );
      mergeInfequentLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.MergeInfrequentValsFilter.Label" ) );
      mergeInfequentLab
          .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.MergeInfrequentValsFilter.TipText" ) );
      mergeInfequentLab.setLayoutData( getFirstLabelFormData() );

      m_mergeInfrequentValsCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_mergeInfrequentValsCheck );
      m_mergeInfrequentValsCheck.setLayoutData( getFirstPromptFormData( mergeInfequentLab ) );
      m_mergeInfrequentValsCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_mergeInfrequentValsConfig.setEnabled( m_mergeInfrequentValsCheck.getSelection() );
        }
      } );

      m_mergeInfrequentValsConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      props.setLook( m_mergeInfrequentValsConfig );
      m_mergeInfrequentValsConfig
          .setText( BaseMessages.getString( PKG, "BasePMIStepDialog.MergeInfrequentValsFilter.Button" ) );
      m_mergeInfrequentValsConfig.setEnabled( m_mergeInfrequentValsCheck.getSelection() );
      m_mergeInfrequentValsConfig.setLayoutData( getSecondPromptFormData( m_mergeInfrequentValsCheck ) );
      lastControl = m_mergeInfrequentValsCheck;

      m_mergeInfrequentValsConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_mergeInfrequentNominalValsFilter, m_mergeInfrequentValsConfig );
        }
      } );

      Label stringToWordVecLab = new Label( m_preprocessingComposite, SWT.RIGHT );
      props.setLook( stringToWordVecLab );
      stringToWordVecLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.StringToWordVectorFilter.Label" ) );
      stringToWordVecLab
          .setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.StringToWordVectorFilter.TipText" ) );
      stringToWordVecLab.setLayoutData( getFirstLabelFormData() );

      m_stringToWordVectorCheck = new Button( m_preprocessingComposite, SWT.CHECK );
      props.setLook( m_stringToWordVectorCheck );
      m_stringToWordVectorCheck.setLayoutData( getFirstPromptFormData( stringToWordVecLab ) );
      m_stringToWordVectorCheck.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          m_stringToWordVectorConfig.setEnabled( m_stringToWordVectorCheck.getSelection() );
        }
      } );

      m_stringToWordVectorConfig = new Button( m_preprocessingComposite, SWT.PUSH );
      props.setLook( m_stringToWordVectorConfig );
      m_stringToWordVectorConfig
          .setText( BaseMessages.getString( PKG, "BasePMIStepDialog.StringToWordVectorFilter.Button" ) );
      m_stringToWordVectorConfig.setEnabled( m_stringToWordVectorCheck.getSelection() );
      m_stringToWordVectorConfig.setLayoutData( getSecondPromptFormData( m_stringToWordVectorCheck ) );
      lastControl = m_stringToWordVectorConfig;

      m_stringToWordVectorConfig.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          popupEditorDialog( m_stringToWordVectorFilter, m_stringToWordVectorConfig );
        }
      } );

      m_resample = new Resample(); // assume a nominal class initially...
      m_removeUselessFilter = new RemoveUseless();
      m_mergeInfrequentNominalValsFilter = new MergeInfrequentNominalValues();
      m_mergeInfrequentNominalValsFilter.setAttributeIndices( "first-last" ); // default is 1,2
      m_stringToWordVectorFilter = new StringToWordVector();

    } catch ( Exception e ) {
      e.printStackTrace();

      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemSettingPreprocessingOptions.Title" ),
              e.getMessage(), false );
      smd.open();
    }

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_preprocessingComposite.setLayoutData( fd );
    m_preprocessingComposite.layout();

    m_preprocessingTab.setControl( m_preprocessingComposite );
  }

  protected void popupEditorDialog( Object objectToEdit, Button button ) {
    try {
      button.setEnabled( false );
      GOEDialog dialog = new GOEDialog( getParent(), SWT.OK | SWT.CANCEL, objectToEdit, transMeta );
      dialog.open();
    } catch ( Exception ex ) {
      ex.printStackTrace();

      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemEditingOptionsGOEDialog.Title" ),
              ex.getMessage(), false );
      smd.open();
    }
    button.setEnabled( true );
  }

  protected void setOptionsForPreprocessingFromMeta( BaseSupervisedPMIStepMeta meta ) throws Exception {
    Map<String, String> sampling = meta.getSamplingConfigs();

    for ( Map.Entry<String, String> e : sampling.entrySet() ) {
      if ( e.getKey().endsWith( ".Resample" ) ) {
        if ( e.getKey().contains( ".unsupervised" ) ) {
          // switch to unsupervised
          m_resample = new weka.filters.unsupervised.instance.Resample();
        }
      }
      /* // set options
      if ( !Const.isEmpty( e.getValue() ) ) {
        Filter toSet = null;
        if ( e.getKey().endsWith( ".Resample" ) ) {
          toSet = m_resample;
        } else if ( e.getKey().endsWith( ".RemoveUseless" ) ) {
          toSet = m_removeUselessFilter;
        } else if ( e.getKey().endsWith( ".MergeInfrequentNominalValues" ) ) {
          toSet = m_mergeInfrequentNominalValsFilter;
        } else if ( e.getKey().endsWith( ".StringToWordVector" ) ) {
          toSet = m_stringToWordVectorFilter;
        }

        if ( toSet != null ) {
          toSet.setOptions( Utils.splitOptions( e.getValue() ) );
        }
      } */
    }

    setOptionsForFilter( m_resample, m_samplingFilters, m_resampleCheck, m_resampleConfig );
    setOptionsForFilter( m_removeUselessFilter, m_preprocessingFilters, m_removeUselessCheck, m_removeUselessConfig );
    setOptionsForFilter( m_mergeInfrequentNominalValsFilter, m_preprocessingFilters, m_mergeInfrequentValsCheck,
        m_mergeInfrequentValsConfig );
    setOptionsForFilter( m_stringToWordVectorFilter, m_preprocessingFilters, m_stringToWordVectorCheck,
        m_stringToWordVectorConfig );
  }

  protected void setOptionsForFilter( Filter filter, List<Filter> filterList, Button associatedCheckBox,
      Button associatedConfigBut ) {
    try {
      for ( Filter f : filterList ) {
        if ( f.getClass().getCanonicalName().equals( filter.getClass().getCanonicalName() ) ) {
          filter.setOptions( f.getOptions() );
          if ( associatedCheckBox != null ) {
            associatedCheckBox.setSelection( true );
            associatedConfigBut.setEnabled( true );
          }
          break;
        }
      }
    } catch ( Exception ex ) {
      ex.printStackTrace();

      ShowMessageDialog
          smd =
          new ShowMessageDialog( shell, SWT.OK | SWT.ICON_ERROR,
              BaseMessages.getString( PKG, "PMIScoringDialog.Error.ProblemSettingPreprocessingOptions.Title" ),
              ex.getMessage(), false );
      smd.open();
    }
  }

  protected void addEvaluationTab() {
    m_evaluationTab = new CTabItem( m_container, SWT.NONE );
    m_evaluationTab.setText( BaseMessages.getString( PKG, "BasePMIStep.EvaluationTab.Title" ) );

    m_evaluationComposite = new Composite( m_container, SWT.NONE );
    props.setLook( m_evaluationComposite );

    FormLayout fl = new FormLayout();
    fl.marginHeight = 3;
    fl.marginWidth = 3;
    m_evaluationComposite.setLayout( fl );

    Label evalModeLabel = new Label( m_evaluationComposite, SWT.RIGHT );
    evalModeLabel.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.EvaluationMode.Label" ) );
    props.setLook( evalModeLabel );
    lastControl = null;
    evalModeLabel.setLayoutData( getFirstLabelFormData() );

    m_evalModeDropDown = new ComboVar( transMeta, m_evaluationComposite, SWT.BORDER | SWT.READ_ONLY );
    props.setLook( m_evalModeDropDown );

    m_evalModeDropDown.setLayoutData( getFirstPromptFormData( evalModeLabel ) );
    lastControl = m_evalModeDropDown;
    m_evalModeDropDown.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        checkWidgets();
      }
    } );

    Label crossValLabel = new Label( m_evaluationComposite, SWT.RIGHT );
    crossValLabel.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.CrossValFolds.Label" ) );
    props.setLook( crossValLabel );
    crossValLabel.setLayoutData( getFirstLabelFormData() );
    crossValLabel.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.CrossValFolds.TipText" ) );

    m_xValFoldsField = new TextVar( transMeta, m_evaluationComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_xValFoldsField );
    m_xValFoldsField.setLayoutData( getFirstPromptFormData( crossValLabel ) );
    lastControl = m_xValFoldsField;

    Label percentageSplitLabel = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( percentageSplitLabel );
    percentageSplitLabel.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.PercentageSplit.Label" ) );
    percentageSplitLabel.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.PercentageSplit.TipText" ) );
    percentageSplitLabel.setLayoutData( getFirstLabelFormData() );

    m_percentageSplitField = new TextVar( transMeta, m_evaluationComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_percentageSplitField );
    m_percentageSplitField.setLayoutData( getFirstPromptFormData( percentageSplitLabel ) );
    lastControl = m_percentageSplitField;

    Label randomSeedLab = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( randomSeedLab );
    randomSeedLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeed.Label" ) );
    randomSeedLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.RandomSeed.TipText" ) );
    randomSeedLab.setLayoutData( getFirstLabelFormData() );

    m_randomSeedField = new TextVar( transMeta, m_evaluationComposite, SWT.SINGLE | SWT.LEAD | SWT.BORDER );
    props.setLook( m_randomSeedField );
    m_randomSeedField.setLayoutData( getFirstPromptFormData( randomSeedLab ) );
    lastControl = m_randomSeedField;

    Label outputAUCLab = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( outputAUCLab );
    outputAUCLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.AUC.Label" ) );
    outputAUCLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.AUC.TipText" ) );
    outputAUCLab.setLayoutData( getFirstLabelFormData() );

    m_outputAUCMetricsCheck = new Button( m_evaluationComposite, SWT.CHECK );
    props.setLook( m_outputAUCMetricsCheck );
    m_outputAUCMetricsCheck.setLayoutData( getFirstPromptFormData( outputAUCLab ) );
    lastControl = m_outputAUCMetricsCheck;

    Label outputIRMetricsLab = new Label( m_evaluationComposite, SWT.RIGHT );
    props.setLook( outputIRMetricsLab );
    outputIRMetricsLab.setText( BaseMessages.getString( PKG, "BasePMIStepDialog.IR.Label" ) );
    outputIRMetricsLab.setToolTipText( BaseMessages.getString( PKG, "BasePMIStepDialog.IR.TipText" ) );
    outputIRMetricsLab.setLayoutData( getFirstLabelFormData() );

    m_outputIRMetricsCheck = new Button( m_evaluationComposite, SWT.CHECK );
    props.setLook( m_outputAUCMetricsCheck );
    m_outputIRMetricsCheck.setLayoutData( getFirstPromptFormData( outputIRMetricsLab ) );

    FormData fd = new FormData();
    fd.left = new FormAttachment( 0, 0 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, -MARGIN * 2 );
    fd.bottom = new FormAttachment( 100, 0 );
    m_evaluationComposite.setLayoutData( fd );
    m_evaluationComposite.layout();

    m_evaluationTab.setControl( m_evaluationComposite );
  }

  protected void checkWidgets() {
    handleRowsToProcessChange();
    handleReservoirSamplingChange();

    wGet.setEnabled( !Const.isEmpty( m_trainingStepDropDown.getText() ) );
    m_schemeGroup.setText( m_inputMeta.getSchemeName() + ( Const.isEmpty( m_engineDropDown.getText() ) ? "" :
        " (" + m_engineDropDown.getText() + ")" ) );

    // enable/disable separate test drop-down based on evaluation mode selected
    String currentEvalSetting = m_evalModeDropDown.getText();
    boolean aucIREnable = checkAUCIRWidgets();
    if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.NONE.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( false );
      m_outputAUCMetricsCheck.setEnabled( false );
      m_outputIRMetricsCheck.setEnabled( false );
      m_outputAUCMetricsCheck.setSelection( false );
      m_outputIRMetricsCheck.setSelection( false );
      m_testStepDropDown.setEnabled( false );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.SEPARATE_TEST_SET.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( false );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( true );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.CROSS_VALIDATION.toString() ) ) {
      m_xValFoldsField.setEnabled( true );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( true );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( false );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.PERCENTAGE_SPLIT.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( true );
      m_randomSeedField.setEnabled( true );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( false );
    } else if ( currentEvalSetting.equalsIgnoreCase( Evaluator.EvalMode.PREQUENTIAL.toString() ) ) {
      m_xValFoldsField.setEnabled( false );
      m_percentageSplitField.setEnabled( false );
      m_randomSeedField.setEnabled( false );
      m_outputAUCMetricsCheck.setEnabled( aucIREnable );
      m_outputIRMetricsCheck.setEnabled( aucIREnable );
      if ( !aucIREnable ) {
        m_outputAUCMetricsCheck.setSelection( false );
        m_outputIRMetricsCheck.setSelection( false );
      }
      m_testStepDropDown.setEnabled( false );
    }
  }

  protected boolean checkAUCIRWidgets() {
    boolean enableCheckBoxes = false;
    if ( !Const.isEmpty( m_classFieldDropDown.getText() ) ) {
      String classFieldName = transMeta.environmentSubstitute( m_classFieldDropDown.getText() );

      int numNonEmpty = m_fieldsTable.nrNonEmpty();
      for ( int i = 0; i < numNonEmpty; i++ ) {
        TableItem item = m_fieldsTable.getNonEmpty( i );

        String fieldName = item.getText( 1 );
        if ( transMeta.environmentSubstitute( fieldName ).equals( classFieldName ) ) {
          int arffType = getArffTypeInt( item.getText( 3 ) );
          if ( arffType == Attribute.NOMINAL ) {
            String nomVals = item.getText( 4 );
            enableCheckBoxes = !Const.isEmpty( nomVals ); // assume that this is a valid list of nominal labels
          }
          break;
        }
      }
    }

    return enableCheckBoxes;
  }

  protected void handleRowsToProcessChange() {
    // check and disable the size input if batch isn't selected
    String rowsToProcess = m_rowsToProcessDropDown.getText();
    if ( rowsToProcess.equals(
        BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.BatchEntry.Label" ) ) ) {
      m_batchSizeField.setEnabled( true );
      m_batchSizeField.setText( m_originalMeta.getBatchSize() );

      // reset other controllers
      m_reservoirSamplingBut.setEnabled( false );
      m_reservoirSamplingBut.setSelection( false );
      m_reservoirSizeField.setEnabled( false );
      m_reservoirSizeField.setText( "" );
      m_stratificationFieldDropDown.setEnabled( false );
      m_stratificationFieldDropDown.setText( "" );
    } else if ( rowsToProcess
        .equals( BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.AllEntry.Label" ) ) ) {
      m_batchSizeField.setEnabled( false );
      m_batchSizeField.setText( "" );

      // reset the other controllers
      m_reservoirSamplingBut.setEnabled( true );
      m_reservoirSamplingBut.setSelection( m_originalMeta.getUseReservoirSampling() );
      m_reservoirSizeField.setEnabled( m_reservoirSamplingBut.getSelection() );
      m_reservoirSizeField.setText( m_originalMeta.getReservoirSize() );
      m_stratificationFieldDropDown.setEnabled( true );
      m_stratificationFieldDropDown.setText( m_originalMeta.getStratificationFieldName() );
    } else if ( rowsToProcess.equals(
        BaseMessages.getString( PKG, "BasePMIStepDialog.NumberOfRowsToProcess.Dropdown.StratifiedEntry.Label" ) ) ) {
      m_batchSizeField.setEnabled( false );
      m_reservoirSamplingBut.setEnabled( true );
      m_reservoirSamplingBut.setSelection( m_originalMeta.getUseReservoirSampling() );
      m_reservoirSizeField.setEnabled( true );
      m_reservoirSizeField.setText( m_originalMeta.getReservoirSize() );
      m_stratificationFieldDropDown.setEnabled( true );
      m_stratificationFieldDropDown.setText( m_originalMeta.getStratificationFieldName() );
    }
  }

  protected List<ArffMeta> getArffMetasForIncomingFields( boolean popupErrorDialogIfNecessary, boolean silent ) {

    try {
      RowMetaInterface row = getRowMetaForTrainingDataSourceStep();
      if ( row != null ) {
        return BaseSupervisedPMIStepData.fieldsToArffMetas( row );
      }
    } catch ( KettleStepException e ) {
      if ( popupErrorDialogIfNecessary ) {
        String message = BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnableToFindIncomingFields" );
        showMessageDialog( message, message, SWT.OK | SWT.ICON_WARNING, false );
      } else {
        if ( !silent ) {
          log.logDebug( BaseMessages.getString( PKG, "BasePMIStepDialog.Warning.UnableToFindIncomingFields" ) );
        }
      }
    }

    return new ArrayList<>();
  }

  protected RowMetaInterface getRowMetaForTrainingDataSourceStep() throws KettleStepException {
    if ( Const.isEmpty( m_trainingStepDropDown.getText() ) ) {
      return null;
    }
    // RowMetaInterface r = transMeta.getPrevStepFields( stepname );
    StepMeta us = transMeta.findStep( stepname );
    List<StepMeta> connected = transMeta.findPreviousSteps( us );
    StepMeta trainingStep = null;
    for ( StepMeta conn : connected ) {
      if ( conn.getName().equalsIgnoreCase( transMeta.environmentSubstitute( m_trainingStepDropDown.getText() ) ) ) {
        trainingStep = conn;
        break;
      }
    }

    if ( trainingStep == null ) {
      // TODO popup warning/error
    }

    return transMeta.getStepFields( trainingStep, us, null );
  }

  protected void populateClassAndStratCombos() {
    List<ArffMeta> incomingFields = getArffMetasForIncomingFields( false, true );
    String existingC = m_classFieldDropDown.getText();
    String existingS = m_stratificationFieldDropDown.getText();
    m_classFieldDropDown.removeAll();
    m_stratificationFieldDropDown.removeAll();
    for ( ArffMeta m : incomingFields ) {
      m_classFieldDropDown.add( m.getFieldName() );
      m_stratificationFieldDropDown.add( m.getFieldName() );
    }

    if ( !Const.isEmpty( existingC ) ) {
      m_classFieldDropDown.setText( existingC );
    }
    if ( !Const.isEmpty( existingS ) ) {
      m_stratificationFieldDropDown.setText( existingS );
    }
  }

  protected void setEvaluationModeFromMeta( BaseSupervisedPMIStepMeta meta ) {
    for ( Evaluator.EvalMode e : Evaluator.EvalMode.values() ) {
      String evalS = e.toString().toLowerCase();
      if ( !evalS.equalsIgnoreCase( Evaluator.EvalMode.PREQUENTIAL.toString() )
          || m_scheme.supportsIncrementalTraining() && evalS
          .equalsIgnoreCase( Evaluator.EvalMode.PREQUENTIAL.toString() ) ) {
        m_evalModeDropDown.add( evalS );
      }
    }

    Evaluator.EvalMode mode = meta.getEvalMode();
    if ( mode == null ) {
      mode = Evaluator.EvalMode.NONE;
    }

    m_evalModeDropDown.setText( mode.toString().toLowerCase() );
  }

  protected void populateFieldsTable() {
    try {

      RowMetaInterface r = getRowMetaForTrainingDataSourceStep();
      if ( r == null ) {
        return;
      }

      if ( r != null ) {
        BaseStepDialog.getFieldsFromPrevious( r, m_fieldsTable, 1, new int[] { 1 }, new int[] { 2 }, -1, -1, null );

        // set some default arff stuff for the new fields
        int nrNonEmptyFields = m_fieldsTable.nrNonEmpty();
        for ( int i = 0; i < nrNonEmptyFields; i++ ) {
          TableItem item = m_fieldsTable.getNonEmpty( i );

          int kettleType = ValueMetaFactory.getIdForValueMeta( item.getText( 2 ) );
          if ( Const.isEmpty( item.getText( 3 ) ) ) {

            switch ( kettleType ) {
              case ValueMetaInterface.TYPE_NUMBER:
              case ValueMetaInterface.TYPE_INTEGER:
              case ValueMetaInterface.TYPE_BOOLEAN:
              case ValueMetaInterface.TYPE_DATE:
                item.setText( 3, "Numeric" );
                break;
              case ValueMetaInterface.TYPE_STRING: {
                item.setText( 3, "Nominal" );
                int index = r.indexOfValue( item.getText( 1 ) );
                ValueMetaInterface vm = r.getValueMeta( index );
                if ( vm.getStorageType() == ValueMetaInterface.STORAGE_TYPE_INDEXED ) {
                  Object[] legalValues = vm.getIndex();
                  String vals = "";
                  for ( int j = 0; i < legalValues.length; j++ ) {
                    if ( j != 0 ) {
                      vals += "," + legalValues[j].toString();
                    } else {
                      vals += legalValues[j].toString();
                    }
                  }
                  item.setText( 4, vals );
                }
              }
              break;
            }
          }
        }
      }
    } catch ( KettleException e ) {
      logError( BaseMessages.getString( PKG, "System.Dialog.GetFieldsFailed.Message" ), e );
      new ErrorDialog( shell, BaseMessages.getString( PKG, "System.Dialog.GetFieldsFailed.Title" ),
          BaseMessages.getString( PKG, "System.Dialog.GetFieldsFailed.Message" ), e );
    }
  }

  protected void handleReservoirSamplingChange() {
    m_reservoirSizeField.setEnabled( m_reservoirSamplingBut.getSelection() );
  }

  protected void showMessageDialog( String title, String message, int flags, boolean scroll ) {
    ShowMessageDialog smd = new ShowMessageDialog( shell, flags, title, message, scroll );
    smd.open();
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

  private FormData getSecondLabelFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, 0 );
    fd.right = new FormAttachment( SECOND_LABEL_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );
    return fd;
  }

  private FormData getSecondPromptFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, MARGIN );
    fd.top = new FormAttachment( lastControl, MARGIN );
    fd.right = new FormAttachment( SECOND_PROMPT_RIGHT_PERCENTAGE, 0 );
    return fd;
  }

  private FormData getThirdPropmtFormData( Control prevControl ) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, 0 );
    fd.right = new FormAttachment( THIRD_PROMPT_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );

    return fd;
  }

  private FormData getFirstGOEFormData(Control prevControl) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, MARGIN );
    fd.top = new FormAttachment( lastControl, MARGIN );
    fd.right = new FormAttachment( GOE_FIRST_BUTTON_RIGHT_PERCENTAGE, 0 );
    return fd;
  }

  private FormData getSecondGOEFormData(Control prevControl) {
    FormData fd = new FormData();
    fd.left = new FormAttachment( prevControl, 0 );
    fd.right = new FormAttachment( GOE_SECOND_BUTTON_RIGHT_PERCENTAGE, 0 );
    fd.top = new FormAttachment( lastControl, MARGIN );

    return fd;
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
