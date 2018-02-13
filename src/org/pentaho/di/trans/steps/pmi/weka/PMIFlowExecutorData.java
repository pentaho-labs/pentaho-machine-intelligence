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

package org.pentaho.di.trans.steps.pmi.weka;

import org.apache.commons.vfs2.FileObject;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.logging.LogChannelInterface;
import org.pentaho.di.core.logging.LogLevel;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.dm.commons.ArffMeta;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Environment;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.WekaException;
import weka.gui.Logger;
import weka.gui.knowledgeflow.MainKFPerspective;
import weka.knowledgeflow.Data;
import weka.knowledgeflow.ExecutionFinishedCallback;
import weka.knowledgeflow.Flow;
import weka.knowledgeflow.FlowLoader;
import weka.knowledgeflow.FlowRunner;
import weka.knowledgeflow.LegacyFlowLoader;
import weka.knowledgeflow.LoggingLevel;
import weka.knowledgeflow.StepInjectorFlowRunner;
import weka.knowledgeflow.StepManager;
import weka.knowledgeflow.StepManagerImpl;
import weka.knowledgeflow.StepOutputListener;
import weka.knowledgeflow.steps.Step;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Data class for the Knowledge Flow step
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class PMIFlowExecutorData extends BaseStepData {

  /**
   * The (eventual) outgoing row meta data
   */
  protected RowMetaInterface m_outputRowMeta;

  /**
   * Meta data for user-specified fields to be injected
   */
  protected ArffMeta[] m_injectFields;

  /**
   * Meta data for the actual fields to be injected into the knowledge flow
   */
  protected ArffMeta[] m_injectArffMetas;

  /**
   * Mapping to fields in incoming kettle data
   */
  protected int[] m_injectFieldIndexes;

  /**
   * True if incoming data has nominal attributes
   */
  protected boolean m_hasNominalAtts;

  /**
   * True if data is to be injected one instance at a time
   */
  protected boolean m_streamData;

  /**
   * Reusable data object in the streaming case
   */
  protected Data m_streamingData;

  /**
   * The step for injecting data into at runtime
   */
  protected Step m_targetStep;

  /**
   * The step to accept output data from
   */
  protected Step m_outputStep;

  /**
   * Header for instances that are to be streamed. If there are no nominal attributes in the incoming data, then this
   * will get constructed on the first incoming row. Otherwise, it will be constructed after we have seen x rows (where
   * x is the number of rows to cache in order to determine legal nominal values).
   */
  protected Instances m_streamingHeader;

  /**
   * The name of the class attribute (if any).
   */
  protected String m_classAttributeName;

  /**
   * True when buffering for header creation has been completed for streaming setups
   */
  protected boolean m_bufferingComplete;

  /**
   * Maps to hold the nominal values of the ARFF data for the inject step
   */
  protected Map<Object, Object>[] m_nominalVals;

  /**
   * the size of the sample
   */
  protected int m_k;

  /**
   * the current row number
   */
  protected int m_currentRow;

  /**
   * Holds sampled rows
   */
  protected List<Object[]> m_sample;

  /**
   * Relation name for Instances created from the sampled rows
   */
  protected String m_sampleRelationName = "Sampled rows"; //$NON-NLS-1$

  /**
   * random number generator
   */
  protected Random m_random;

  /**
   * logging
   **/
  protected LogChannelInterface m_log;

  /**
   * The Flow we're executing
   */
  protected Flow m_flowToUse;

  /**
   * Flow runner used for execution
   */
  protected StepInjectorFlowRunner m_flowRunner;

  /**
   * Allowable connection types to inject data into the knowledge flow
   */
  public static final List<String>
      INJECT_CONNS =
      Arrays.asList( StepManager.CON_DATASET, StepManager.CON_TRAININGSET, StepManager.CON_TESTSET,
          StepManager.CON_INSTANCE );

  /**
   * Allowable connection types that we can listen for an process
   */
  public static final List<String>
      OUTPUT_CONNS =
      Arrays
          .asList( StepManager.CON_TEXT, StepManager.CON_DATASET, StepManager.CON_TRAININGSET, StepManager.CON_TESTSET,
              StepManager.CON_AUX_DATA_BATCH_ASSOCIATION_RULES, StepManager.CON_BATCH_CLASSIFIER,
              StepManager.CON_INCREMENTAL_CLASSIFIER );

  public void setLog( LogChannelInterface log ) {
    m_log = log;
  }

  public void setInjectFields( ArffMeta[] arffMeta ) {
    m_injectFields = arffMeta;
  }

  public ArffMeta[] getInjectFields() {
    return m_injectFields;
  }

  /**
   * Set whether the flow is to have data streamed to it (i.e. instance events).
   *
   * @param streamData true if data is to be streamed to the knowledge flow
   */
  protected void setStreamData( boolean streamData ) {
    m_streamData = streamData;
  }

  /**
   * Get whether data is to be streamed to the flow.
   *
   * @return true if data is to be streamed to the flow
   */
  protected boolean getStreamData() {
    return m_streamData;
  }

  /**
   * Returns true if streaming has been selected and there are nominal attributes in the incoming data. In this case, we
   * have to buffer some data in order to determine nominal values.
   *
   * @return true if streaming is selected and we will be buffering data (i.e. there are nominal attributes in the
   * incoming data)
   */
  protected boolean getBufferingForStreaming() {
    return ( m_streamData && m_hasNominalAtts );
  }

  /**
   * Set the name of the attribute to use as the class.
   *
   * @param classAttName the name of the class attribute
   */
  protected void setClassAttributeName( String classAttName ) {
    m_classAttributeName = classAttName;
  }

  /**
   * Set the meta data for the incoming rows (later gets modified) into the output format by getFields() in the meta
   * class
   *
   * @param rmi the incoming row meta data
   */
  protected void setOutputRowMeta( RowMetaInterface rmi ) {
    m_outputRowMeta = rmi;
  }

  /**
   * Get the output row meta data.
   *
   * @return the output row meta data
   */
  protected RowMetaInterface getOutputRowMeta() {
    return m_outputRowMeta;
  }

  /**
   * Set the relation name for the sample.
   *
   * @param relationName the relation name to use for the sample
   */
  protected void setSampleRelationName( String relationName ) {
    m_sampleRelationName = relationName;
  }

  /**
   * Set the flow to execute
   *
   * @param flow     the flow to execute
   * @param env      environment variables
   * @param logLevel Kettle log level to map to Weka's KF log level
   */
  protected void setFlow( Flow flow, Environment env, LogLevel logLevel ) {
    m_flowToUse = flow;
    m_flowRunner = new StepInjectorFlowRunner();
    m_flowRunner.getExecutionEnvironment().setEnvironmentVariables( env );
    m_flowRunner.setFlow( m_flowToUse );
    m_flowRunner.setLogger( new LogAdapter( m_log ) );
    LoggingLevel wekaLevel = LoggingLevel.BASIC;
    if ( logLevel == LogLevel.NOTHING ) {
      wekaLevel = LoggingLevel.NONE;
    } else if ( logLevel == LogLevel.MINIMAL ) {
      wekaLevel = LoggingLevel.LOW;
    } else if ( logLevel == LogLevel.DEBUG || logLevel == LogLevel.ROWLEVEL ) {
      wekaLevel = LoggingLevel.DEBUGGING;
    } else if ( logLevel == LogLevel.ERROR ) {
      wekaLevel = LoggingLevel.ERROR;
    }
    m_flowRunner.setLoggingLevel( wekaLevel );
    m_streamingHeader = null;
    m_streamingData = new Data( StepManager.CON_INSTANCE );
  }

  /**
   * Get the flow to execute
   *
   * @return the flow to execute
   */
  public Flow getFlow() {
    return m_flowToUse;
  }

  /**
   * Allocate an array to hold meta data for the ARFF instances
   *
   * @param num number of meta data objects to allocate
   */
  protected void allocate( int num ) {
    m_injectFields = new ArffMeta[num];
  }

  private void reset() {
    m_bufferingComplete = false;
  }

  /**
   * Sets up the ArffMeta array based on the incomming Kettle row format.
   *
   * @param rmi a <code>RowMetaInterface</code> value
   */
  public void setupArffMeta( RowMetaInterface rmi ) {
    if ( rmi != null ) {
      allocate( rmi.size() );
      // initialize the output fields to all incoming fields with
      // corresponding arff types
      for ( int i = 0; i < m_injectFields.length; i++ ) {
        ValueMetaInterface inField = rmi.getValueMeta( i );
        int fieldType = inField.getType();
        switch ( fieldType ) {
          case ValueMetaInterface.TYPE_NUMBER:
          case ValueMetaInterface.TYPE_INTEGER:
          case ValueMetaInterface.TYPE_BOOLEAN:
            m_injectFields[i] = new ArffMeta( inField.getName(), fieldType, ArffMeta.NUMERIC );
            System.err.println( "Setting up field: " + inField.getName() + " as numeric" );
            break;
          case ValueMetaInterface.TYPE_STRING:
            m_injectFields[i] = new ArffMeta( inField.getName(), fieldType, ArffMeta.NOMINAL );
            System.err.println( "Setting up field: " + inField.getName() + " as nominal" );
            // check for indexed values
            if ( inField.getStorageType() == ValueMetaInterface.STORAGE_TYPE_INDEXED ) {
              Object[] legalVals = inField.getIndex();
              StringBuffer temp = new StringBuffer();
              boolean first = true;
              for ( Object l : legalVals ) {
                if ( first ) {
                  temp.append( l.toString().trim() );
                  first = false;
                } else {
                  temp.append( "," ).append( l.toString().trim() ); //$NON-NLS-1$
                }
              }
              m_injectFields[i].setNominalVals( temp.toString() );
            }
            break;
          case ValueMetaInterface.TYPE_DATE:
            System.err.println( "Setting up field: " + inField.getName() + " as date" );
            m_injectFields[i] = new ArffMeta( inField.getName(), fieldType, ArffMeta.DATE );
            m_injectFields[i].setDateFormat( inField.getDateFormat().toPattern() );
            break;
        }
      }
    }
  }

  /**
   * Initialize the reservoir/cache for the requested sample (or cache) size.
   *
   * @param sampleSize the number of rows to sample or to cache
   * @param seed       the random number seed to use
   */
  protected void initializeReservoir( int sampleSize, int seed ) {

    reset();
    m_k = sampleSize;

    if ( !m_streamData ) {
      m_sample = ( m_k > 0 ) ? new ArrayList<Object[]>( m_k ) : new ArrayList<Object[]>();
    } else {
      m_sample = new ArrayList<Object[]>();
    }
    m_currentRow = 0;
    m_random = new Random( seed );

    // throw away the first 100 random numbers
    for ( int i = 0; i < 100; i++ ) {
      m_random.nextDouble();
    }
  }

  /**
   * Set the indexes of the fields to inject into the knowledge flow
   *
   * @param injectFieldIndexes array of indexes
   * @param arffMetas          array of arff metas
   */
  @SuppressWarnings( "unchecked" ) protected void setInjectFieldIndexes( int[] injectFieldIndexes,
      ArffMeta[] arffMetas ) {
    m_injectFieldIndexes = injectFieldIndexes;
    m_injectArffMetas = arffMetas;

    // initialize any necessary HashMaps
    m_nominalVals = new HashMap[m_injectFieldIndexes.length];
    for ( int i = 0; i < m_injectFieldIndexes.length; i++ ) {
      if ( m_injectFieldIndexes[i] >= 0 ) {
        if ( m_injectArffMetas[i].getArffType() == ArffMeta.NOMINAL ) {
          m_nominalVals[i] = new HashMap<Object, Object>();
          if ( org.pentaho.di.core.util.Utils.isEmpty( m_injectArffMetas[i].getNominalVals() ) ) {
            m_hasNominalAtts = true;
          } else {
            // transfer over the values
            List<String> vList = ArffMeta.stringToVals( m_injectArffMetas[i].getNominalVals() );
            for ( String v : vList ) {
              m_nominalVals[i].put( v, v );
            }
          }
        }
      }
    }

    if ( getStreamData() && !m_hasNominalAtts ) {
      m_log.logBasic(
          BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Info.NoCachingNecessaryForStreaming" ) );
    }
  }

  public static Flow getFlowFromFile( String fileName, VariableSpace vars ) throws Exception {
    if ( !org.pentaho.di.core.util.Utils.isEmpty( fileName ) ) {
      File flowFile = pathToURI( fileName, vars );

      if ( flowFile != null && flowFile.exists() ) {
        return Flow.loadFlow( flowFile, new FlowRunner.SimpleLogger() );
      } else {
        throw new Exception( "Flow '" + fileName + "' does not seem to exist!" );
      }
    } else {
      throw new Exception( "Filename is empty!" );
    }
  }

  // TODO plumb Kettle log wrapper through to here
  public static Flow getFlowFromFileVFS( String fileName, VariableSpace vars, Environment env ) throws Exception {
    if ( !org.pentaho.di.core.util.Utils.isEmpty( fileName ) ) {
      fileName = vars.environmentSubstitute( fileName );
      String extension = "kf"; // default is a json flow
      if ( fileName.lastIndexOf( '.' ) >= 0 ) {
        extension = fileName.substring( fileName.lastIndexOf( '.' ) + 1 );
      }
      FlowLoader loaderForFlow = Flow.getFlowLoader( extension, new FlowRunner.SimpleLogger() );

      InputStream inputStream = KettleVFS.getInputStream( fileName );
      FileObject fo = KettleVFS.getFileObject( fileName, vars );
      String parent = fo.getParent().toString();
      parent = parent.replace( "file://", "" );

      env.addVariable( MainKFPerspective.FLOW_PARENT_DIRECTORY_VARIABLE_KEY, parent );
      return Flow.loadFlow( inputStream, loaderForFlow );
    } else {
      throw new Exception( "Filename is empty!" );
    }
  }

  public static Flow getFlowFromJSON( String json ) throws Exception {
    try {
      return Flow.JSONToFlow( json );
    } catch ( WekaException ex ) {
      // try legacy xml format
      LegacyFlowLoader legacyLoader = new LegacyFlowLoader();
      StringReader sr = new StringReader( json );
      return legacyLoader.readFlow( sr );
    }
  }

  public static String getJSONFromFLow( Flow flow ) throws WekaException {
    return flow.toJSON();
  }

  protected void validateInputStep( String targetStepName, String inputConnName, boolean streaming, VariableSpace vars )
      throws KettleException {

    if ( org.pentaho.di.core.util.Utils.isEmpty( inputConnName ) ) {
      throw new KettleException(
          BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.NoInputConnectionSpecified" ) );
    }

    if ( org.pentaho.di.core.util.Utils.isEmpty( targetStepName ) ) {
      throw new KettleException(
          BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.NoInputStepSpecified" ) );
    }

    inputConnName = vars.environmentSubstitute( inputConnName );
    if ( streaming ) {
      if ( !inputConnName.equalsIgnoreCase( StepManager.CON_INSTANCE ) ) {
        throw new KettleException(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.ConnMustBeInstanceForStreaming" ) );
      }
      m_streamData = true;
    }

    if ( m_flowRunner != null ) {
      StepManagerImpl targetManager = m_flowRunner.getFlow().findStep( vars.environmentSubstitute( targetStepName ) );
      if ( targetManager == null ) {
        throw new KettleException( BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KFData.Message.Error.StepNotPartOfFlow",
                vars.environmentSubstitute( targetStepName ) ) );

      }
      m_targetStep = targetManager.getManagedStep();
      if ( !m_targetStep.getClass().getCanonicalName().equals( "weka.knowledgeflow.steps.KettleInject" ) ) {
        throw new KettleException( BaseMessages
            .getString( PMIFlowExecutorMeta.class, "KFData.Message.Error.InjectKFStepIsNotOfTypeKettleInject",
                vars.environmentSubstitute( targetStepName ) ) );
      }
      // m_targetStep = m_flowRunner.findStep( vars.environmentSubstitute( targetStepName ), KettleInject.class );
      // make sure that the KettleInject step is outputting the same connection as the user has
      // specified as the inject connection type - otherwise the step downstream from the KettleInject
      // will not receive any data!
      if ( m_targetStep.getStepManager().numOutgoingConnectionsOfType( vars.environmentSubstitute( inputConnName ) )
          == 0 ) {
        throw new KettleException( BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.KettleInjectNotOutputtingInjectConnType",
                vars.environmentSubstitute( inputConnName ) ) );
      }
    }
  }

  protected static StepManagerImpl validateOutputStep( Flow flowToUse, String outputStepName, String outputConnName,
      StepOutputListener outListener, VariableSpace vars, LogChannelInterface log ) throws KettleException {

    if ( org.pentaho.di.core.util.Utils.isEmpty( outputStepName ) || org.pentaho.di.core.util.Utils
        .isEmpty( outputConnName ) ) {
      throw new KettleStepException( BaseMessages.getString( PMIFlowExecutorMeta.PKG,
          "KnowledgeFlowData.Error.BothOutputStepNameAndConnNameNeedSpecifying" ) ); //$NON-NLS-1$
    }

    outputStepName = vars.environmentSubstitute( outputStepName );
    outputConnName = vars.environmentSubstitute( outputConnName );
    StepManagerImpl manager = flowToUse.findStep( outputStepName );
    if ( manager == null ) {
      throw new KettleStepException( BaseMessages
          .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.UnableToFindOutputStepInFlow", outputStepName ) );
    }
    manager.getManagedStep();

    if ( outListener != null ) {
      if ( org.pentaho.di.core.util.Utils.isEmpty( outputConnName ) ) {
        throw new KettleException(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.NoOutputConnectionSpecified" ) );
      }

      // now check that the output step can produce the specified connection type
      // output connections that the step can produce
      List<String> outputCons = manager.getManagedStep().getOutgoingConnectionTypes();
      boolean ok = false;
      for ( String conn : outputCons ) {
        if ( conn.equalsIgnoreCase( outputConnName ) ) {
          ok = true;
          break;
        }
      }

      if ( !ok ) {
        throw new KettleStepException( BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.OutputStepDoesNotProduceSpecifiedConnection",
                outputStepName, outputConnName ) );
      }

      if ( log != null ) {
        // register StepOutputListener with output step
        log.logDebug( "Registering to receive " + outputConnName + " from " + outputStepName );
      }
      manager.addStepOutputListener( outListener, outputConnName );
    }

    return manager;
  }

  public static List<String> getAllInjectStepNames( Flow flow ) {
    List<String> stepNames = new ArrayList<String>();
    if ( flow != null ) {
      List<StepManagerImpl> startPoints = flow.findPotentialStartPoints();
      for ( StepManagerImpl sm : startPoints ) {
        if ( sm.getManagedStep().getClass().getCanonicalName().equals( "weka.knowledgeflow.steps.KettleInject" ) ) {
          stepNames.add( sm.getName() );
        }
      }
    }

    return stepNames;
  }

  public static List<String> getAllAllowableOutputStepNames( Flow flow ) {
    List<String> outputStepNames = new ArrayList<String>();
    if ( flow != null ) {
      for ( StepManagerImpl sm : flow.getSteps() ) {
        List<String> stepOutputConns = sm.getManagedStep().getOutgoingConnectionTypes();
        boolean ok = false;
        for ( String conn : stepOutputConns ) {
          if ( OUTPUT_CONNS.contains( conn ) ) {
            ok = true;
            break;
          }
        }
        if ( ok ) {
          outputStepNames.add( sm.getName() );
        }
      }
    }

    return outputStepNames;
  }

  public static List<String> getAllAllowableConnNamesForStep( Flow flow, String stepName ) {
    List<String> outputConnNames = new ArrayList<String>();
    if ( flow != null ) {
      StepManagerImpl sm = flow.findStep( stepName );
      if ( sm != null ) {
        List<String> stepOutputConnNames = sm.getManagedStep().getOutgoingConnectionTypes();
        for ( String connName : stepOutputConnNames ) {
          if ( OUTPUT_CONNS.contains( connName ) ) {
            outputConnNames.add( connName );
          }
        }
      }
    }

    return outputConnNames;
  }

  public static List<String> getInputConnectionsForNamedStep( Flow flow, String stepName ) {
    List<String> connNames = new ArrayList<String>();

    StepManagerImpl step = flow.findStep( stepName );
    if ( step != null ) {
      connNames = step.getManagedStep().getIncomingConnectionTypes();
    }

    return connNames;
  }

  /**
   * Construct an instance from a kettle row
   *
   * @param inputRowMeta the meta data for the row
   * @param row          the row itself
   * @param header       the arff header to use
   * @param batch        true if this instance is going into a batch of instances ( rather than being streamed into the
   *                     flow) - this only affects how string attributes are handled
   * @return an Instance corresponding to the row
   * @throws KettleException if the conversion can't be performed
   */
  protected Instance constructInstance( RowMetaInterface inputRowMeta, Object[] row, Instances header, boolean batch )
      throws KettleException {

    double[] vals = new double[header.numAttributes()]; // holds values for the
    // new Instance

    for ( int i = 0; i < m_injectArffMetas.length; i++ ) {
      ArffMeta tempField = m_injectArffMetas[i];
      if ( tempField != null ) {
        int arffType = tempField.getArffType();
        Attribute currentAtt = header.attribute( i );

        ValueMetaInterface vmi = inputRowMeta.getValueMeta( m_injectFieldIndexes[i] );
        int fieldType = vmi.getType();
        Object rowVal = row[m_injectFieldIndexes[i]];

        // check for null
        String stringV = vmi.getString( rowVal );
        if ( stringV == null || stringV.length() == 0 ) {
          // set to missing value
          vals[i] = Utils.missingValue();
        } else {
          switch ( arffType ) {
            case ArffMeta.NUMERIC:
              if ( fieldType == ValueMetaInterface.TYPE_BOOLEAN ) {
                Boolean b = vmi.getBoolean( rowVal );
                vals[i] = b ? 1.0 : 0.0;
              } else if ( fieldType == ValueMetaInterface.TYPE_INTEGER ) {
                Long t = vmi.getInteger( rowVal );
                vals[i] = t;
              } else {
                Double n = vmi.getNumber( rowVal );
                vals[i] = n;
              }

              break;
            case ArffMeta.NOMINAL:
              String s = vmi.getString( rowVal );
              // now need to look for this value in the attribute
              // in order to get the correct index
              int index = currentAtt.indexOfValue( s );
              if ( index < 0 ) {
                // user may have supplied a list of legal nominal values, but now
                // we've
                // seen a val that isn't in that list
                vals[i] = Utils.missingValue();
              } else {
                vals[i] = index;
              }

              break;
            case ArffMeta.DATE:
              // Get the date as a number
              Double date = vmi.getNumber( rowVal );
              vals[i] = date.doubleValue();

              break;
            case ArffMeta.STRING:
              String ss = vmi.getString( rowVal );
              // make sure that any escaped newlines, carriage returns etc. get
              // converted
              // back
              ss = Utils.unbackQuoteChars( ss );
              if ( batch ) {
                vals[i] = currentAtt.addStringValue( ss );
              } else {
                currentAtt.setStringValue( ss );
                vals[i] = 0.0;
              }
              break;
          }
        }
      }
    }

    return new DenseInstance( 1.0, vals );
  }

  /**
   * Convert the contents of the reservoir into a set of Instances.
   *
   * @param inputRowMeta the meta data for the incoming rows
   * @return an Instances object holding all the data in the reservoir
   * @throws KettleException if there is a problem during the conversion
   */
  protected Instances reservoirToInstances( RowMetaInterface inputRowMeta ) throws KettleException {

    // Construct the Instances structure
    Instances header = createHeader( inputRowMeta );

    // Convert the sampled rows to Instance objects and add them to the data set
    Iterator<Object[]> li = m_sample.iterator();
    int counter = 0;
    while ( li.hasNext() ) {
      Object[] row = li.next(); // the row to process
      if ( row == null ) {
        // either more rows were requested than there actually were in the
        // end, or the mechanism for filling the list has added some residue
        // null values
        break;
      }
      counter++;

      Instance newInst = constructInstance( inputRowMeta, row, header, true );

      // add that sucker...
      header.add( newInst );
    }

    m_log.logBasic(
        BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Info.ConvertedRowsToInstances", counter ) );
    header.compactify();
    return header;
  }

  /**
   * Create an Instances structure (header only) from supplied row meta data and information on valid nominal values (if
   * any) collected in the reservoir/cache.
   *
   * @param inputRowMeta the row meta data
   * @return ARFF header as an Instances object
   * @throws KettleException if there is a problem constructing the header
   */
  private Instances createHeader( RowMetaInterface inputRowMeta ) throws KettleException {
    ArrayList<Attribute> attInfo = new ArrayList<Attribute>( m_injectArffMetas.length );

    for ( int i = 0; i < m_injectArffMetas.length; i++ ) {
      ArffMeta tempField = m_injectArffMetas[i];
      Attribute tempAtt = null;
      int arffType = tempField.getArffType();
      switch ( arffType ) {
        case ArffMeta.NUMERIC:
          tempAtt = new Attribute( tempField.getFieldName() );
          break;
        case ArffMeta.NOMINAL:
          List<String> attVals = setupNominalVals( i, inputRowMeta.getValueMeta( m_injectFieldIndexes[i] ) );
          tempAtt = new Attribute( tempField.getFieldName(), attVals );
          break;
        case ArffMeta.DATE:
          String dateF = tempField.getDateFormat();
          tempAtt = new Attribute( tempField.getFieldName(), dateF );
          break;
        case ArffMeta.STRING:
          tempAtt = new Attribute( tempField.getFieldName(), (List<String>) null );
      }

      if ( tempAtt != null ) {
        attInfo.add( tempAtt );
      } else {
        throw new KettleException(
            BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.UnhandledAttributeType" ) );
      }
    }

    Instances header = new Instances( m_sampleRelationName, attInfo, m_sample.size() );
    if ( m_classAttributeName != null ) {
      // set the class attribute
      Attribute classA = header.attribute( m_classAttributeName );
      if ( classA != null ) {
        header.setClass( classA );
      } else {
        throw new KettleException( BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KFData.Message.Error.UnableToSetClassIndexCantFindAttribute",
                m_classAttributeName ) );
      }
    }
    return header;
  }

  /**
   * Set up a List of valid nominal values for a particular field/attribute
   *
   * @param index     the index of the attribute/field
   * @param fieldMeta the meta data for the field
   * @return a FastVector of nominal values
   * @throws KettleException if a problem occurs
   */
  private List<String> setupNominalVals( int index, ValueMetaInterface fieldMeta ) throws KettleException {
    Map<Object, Object> map = m_nominalVals[index];

    // make sure that the values are in sorted order
    TreeSet<String> sorted = new TreeSet<String>();

    Set<Object> keySet = map.keySet();
    for ( Object val : keySet ) {
      // if the user hasn't supplied the list of valid nominal values
      // we need to use ValueMeta to convert them (in case lazy converion
      // is being used and they are in binary string format)
      if ( org.pentaho.di.core.util.Utils.isEmpty( m_injectArffMetas[index].getNominalVals() ) ) {
        String sval = fieldMeta.getString( val );
        sorted.add( sval );
      } else {
        sorted.add( val.toString() );
      }
    }

    List<String> attVals = new ArrayList<String>( sorted.size() );
    for ( String s : sorted ) {
      attVals.add( s );
    }

    return attVals;
  }

  protected void injectDataStreaming( Instance toInject ) throws WekaException {

    m_streamingData.setPayloadElement( StepManager.CON_INSTANCE, toInject );

    if ( toInject == null ) {
      // end of stream
      m_streamingData.setPayloadElement( StepManagerImpl.CON_AUX_DATA_INCREMENTAL_STREAM_END, true );
    }

    m_flowRunner.injectStreaming( m_streamingData, m_targetStep, toInject == null );
  }

  protected void injectDataBatch( ExecutionFinishedCallback finishedCallback, RowMetaInterface inputMeta,
      PMIFlowExecutorMeta kfMeta, VariableSpace vars ) throws KettleException, WekaException {
    m_log.logBasic(
        BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Info.ConstructingInstancesFromReservoir" ) );
    Instances dataSet = reservoirToInstances( inputMeta );
    if ( kfMeta.getSetClass() ) {
      Attribute classA = dataSet.attribute( vars.environmentSubstitute( kfMeta.getClassAttributeName() ) );
      if ( classA != null ) {
        dataSet.setClass( classA );
      } else {
        throw new KettleException( BaseMessages
            .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.UnableToSetClassIndexCantFindAttribute",
                vars.environmentSubstitute( kfMeta.getClassAttributeName() ) ) );
      }
    }

    m_log
        .logBasic( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Info.InjectingInstancesIntoKF" ) );

    Data data = new Data( vars.environmentSubstitute( kfMeta.getInjectConnectionName() ), dataSet );
    data.setPayloadElement( StepManager.CON_AUX_DATA_SET_NUM, 1 );
    data.setPayloadElement( StepManager.CON_AUX_DATA_MAX_SET_NUM, 1 );
    m_flowRunner.injectWithExecutionFinishedCallback( data, finishedCallback, m_targetStep );
  }

  protected void executeNoInject( ExecutionFinishedCallback finishedCallback ) throws WekaException {
    m_flowRunner.addExecutionFinishedCallback( finishedCallback );
    m_flowRunner.runParallel();
  }

  protected static void setElement( List<Object[]> list, int idx, Object[] item ) {
    final int size = list.size();
    if ( size <= idx ) {
      // int len = Math.max(size, idx - size);
      int buff = ( size == 0 ) ? 100 : size * 2;
      for ( int i = 0; i < buff; i++ ) {
        list.add( null );
      }
    }
    list.set( idx, item );
  }

  protected void flushStreamingBuffer( RowMetaInterface inputMeta, Object[] inputRow ) throws KettleException {
    if ( m_hasNominalAtts && !m_bufferingComplete ) {
      m_bufferingComplete = true;
      m_log.logBasic(
          BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Info.StreamingBufferFullInjectingRows" ) );

      m_streamingHeader = createHeader( inputMeta );

      for ( int i = 0; i < m_sample.size(); i++ ) {
        Object[] aRow = m_sample.get( i );
        Instance injectInst = constructInstance( inputMeta, aRow, m_streamingHeader, false );
        injectInst.setDataset( m_streamingHeader );

        try {
          injectDataStreaming( injectInst );
        } catch ( WekaException ex ) {
          throw new KettleException( ex );
        }
      }

      // now inject the current row (if any)
      try {
        if ( inputRow != null ) {
          m_log.logBasic(
              BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Info.StreamingSubsequentRows" ) );
          Instance injectInst = constructInstance( inputMeta, inputRow, m_streamingHeader, false );
          injectInst.setDataset( m_streamingHeader );
          injectDataStreaming( injectInst );
          m_currentRow++;
        } else {
          injectDataStreaming( null );
        }
      } catch ( WekaException ex ) {
        throw new KettleException( ex );
      }
    }
  }

  protected void processRow( Object[] inputRow, RowMetaInterface inputMeta ) throws Exception {
    if ( m_streamData && m_currentRow == 0 ) {
      if ( m_hasNominalAtts ) {
        // check our buffer size
        if ( m_k <= 0 ) {
          throw new Exception( BaseMessages
              .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.MustHaveANonZeroCacheSizeWhenStreaming" ) );
        }
      }
    }

    if ( inputRow == null ) {
      if ( m_streamData ) {
        if ( !m_bufferingComplete && m_hasNominalAtts ) {
          // flush routine terminates the inject stream
          flushStreamingBuffer( inputMeta, null );
        } else {
          injectDataStreaming( null ); // end of stream
        }
      }
      return;
    }

    // Store any necessary nominal header values first.
    // Store as (possibly) kettle internal binary format (for speed).
    // At the end of the incoming stream of rows we will construct
    // the arff header and at that time convert from the binary format.
    if ( m_hasNominalAtts ) {
      for ( int i = 0; i < m_injectFieldIndexes.length; i++ ) {
        if ( m_injectFieldIndexes[i] >= 0 ) {
          Object inField = inputRow[m_injectFieldIndexes[i]];
          if ( inField != null ) {
            // we might want to allow the user to convert numeric types to
            // nominal here...
            if ( m_injectArffMetas[i].getArffType() == ArffMeta.NOMINAL ) {
              if ( !m_nominalVals[i].containsKey( inField ) ) {
                m_nominalVals[i].put( inField, inField );
              }
            }
          }
        }
      }
    }

    if ( m_streamData && !m_hasNominalAtts && m_streamingHeader == null ) {
      // first row and there are no nominal attributes to worry about,
      // so construct header now!
      m_streamingHeader = createHeader( inputMeta );
    }

    if ( !m_streamData ) {
      // Now see if this row should be stored in the reservoir
      // if sampling size is 0, do not sample.
      if ( m_k == 0 ) {
        m_sample.add( inputRow );
      } else if ( m_currentRow < m_k ) {
        setElement( m_sample, m_currentRow, inputRow );
        // size can be less than 0, which is essentially a blocking step
      } else if ( m_k > 0 ) {
        double r = m_random.nextDouble();
        if ( r < ( (double) m_k / (double) m_currentRow ) ) {
          r = m_random.nextDouble();
          int replace = (int) ( m_k * r );
          setElement( m_sample, replace, inputRow );
        }
      }
      m_currentRow++;
    } else if ( m_hasNominalAtts && !m_bufferingComplete ) {
      // streaming and we need to store rows to determine
      // nominal values
      if ( m_sample.size() < m_k ) {
        m_sample.add( inputRow );
        m_currentRow++;
      } else {
        flushStreamingBuffer( inputMeta, inputRow );
      }
    } else {
      Instance injectInst = constructInstance( inputMeta, inputRow, m_streamingHeader, false );
      injectInst.setDataset( m_streamingHeader );
      injectDataStreaming( injectInst );
      m_currentRow++;
    }
  }

  protected void buildAndValidateInjectMapping( RowMetaInterface inputMeta ) throws KettleException {
    int[] injectFieldIndexes = new int[getInjectFields().length];
    ArffMeta[] arffMeta = getInjectFields();
    for ( int i = 0; i < arffMeta.length; i++ ) {
      if ( arffMeta[i] != null ) {
        injectFieldIndexes[i] = inputMeta.indexOfValue( arffMeta[i].getFieldName() );
        // Do we still have this field coming in??
        if ( injectFieldIndexes[i] < 0 ) {
          throw new KettleException( BaseMessages
              .getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlowData.Error.FieldCouldNotBeFoundInTheInputStream",
                  //$NON-NLS-1$
                  arffMeta[i].getFieldName() ) );
        }
      } else {
        // OK, this particular entry had no corresponding arff
        // type
        injectFieldIndexes[i] = -1;
      }
    }

    setInjectFieldIndexes( injectFieldIndexes, arffMeta );
  }

  public static File pathToURI( String path, VariableSpace vars ) throws Exception {
    path = vars.environmentSubstitute( path );
    File result = null;
    if ( path.toLowerCase().startsWith( "file:" ) ) {
      path = path.replace( " ", "%20" );
      result = new File( new URI( path ) );
    } else if ( !path.contains( "://" ) ) {
      // assume a regular files
      result = new File( path );
    }

    return result;
  }

  public static class LogAdapter extends PrintStream implements Serializable, Logger {

    private transient LogChannelInterface m_log;

    public LogAdapter( LogChannelInterface wrapped ) {
      super( System.out );
      m_log = wrapped;
    }

    @Override public void logMessage( String s ) {
      String wekaLevel = null;
      try {
        wekaLevel = s.substring( 1, s.indexOf( ']' ) );
      } catch ( Exception ex ) {
        // ignore
      }
      if ( wekaLevel == null || wekaLevel.equalsIgnoreCase( "basic" ) ) {
        m_log.logBasic( s );
      } else if ( wekaLevel.equalsIgnoreCase( "low" ) ) {
        m_log.logMinimal( s );
      } else if ( wekaLevel.equalsIgnoreCase( "detailed" ) ) {
        m_log.logDetailed( s );
      } else if ( wekaLevel.equalsIgnoreCase( "debugging" ) ) {
        m_log.logDebug( s );
      } else if ( wekaLevel.equalsIgnoreCase( "error" ) ) {
        m_log.logError( s );
      } else if ( wekaLevel.equalsIgnoreCase( "warning" ) ) {
        m_log.logRowlevel( s );
      }
    }

    @Override public void statusMessage( String s ) {
      // m_log.logDetailed( s );
    }

    public void println( String s ) {
      System.out.println( s );
      statusMessage( s );
    }

    public void println( Object o ) {
      println( o.toString() );
    }

    public void print( String s ) {
      System.out.print( s );
      statusMessage( s );
    }

    public void print( Object o ) {
      print( o.toString() );
    }
  }
}
