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

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import weka.associations.AssociationRule;
import weka.associations.AssociationRules;
import weka.associations.Item;
import weka.core.Attribute;
import weka.core.Environment;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.Utils;
import weka.core.WekaException;
import weka.knowledgeflow.Data;
import weka.knowledgeflow.ExecutionFinishedCallback;
import weka.knowledgeflow.Flow;
import weka.knowledgeflow.StepManager;
import weka.knowledgeflow.StepOutputListener;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import static weka.knowledgeflow.StepManager.*;

/**
 * PDI step that executes a Weka Knowledge Flow process
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 */
public class PMIFlowExecutor extends BaseStep implements StepInterface, StepOutputListener {

  /**
   * The environment variables to pass to the flow
   */
  protected Environment m_env;

  protected PMIFlowExecutorMeta m_meta;
  protected PMIFlowExecutorData m_data;

  protected Flow m_flowCopy;
  protected boolean m_injectDataIntoKF;
  protected boolean m_listeningForOutputFromKF;
  protected boolean m_flowExecutionFinished;
  protected boolean m_batchProcessingLaunched;

  protected Exception m_flowOutputException;

  protected AtomicBoolean m_processingKFOutput;

  public PMIFlowExecutor( StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta,
      Trans trans ) {
    super( stepMeta, stepDataInterface, copyNr, transMeta, trans );

    String internalTransDir = transMeta.getVariable( "Internal.Transformation.Filename.Directory" );
    m_env = new Environment();

    if ( !org.pentaho.di.core.util.Utils.isEmpty( internalTransDir ) ) {
      try {
        File temp = PMIFlowExecutorData.pathToURI( internalTransDir, transMeta );
        internalTransDir = temp.getAbsolutePath();
      } catch ( Exception ex ) {
        //        logError( BaseMessages.getString( KFMeta.PKG, "KF.Message.Error.MalformedURI" ) ); //$NON-NLS-1$
        return;
      }
    }
    m_env.addVariable( "Internal.Transformation.Filename.Directory", //$NON-NLS-1$
        internalTransDir );

    List<String> transVarsInUse = transMeta.getUsedVariables();
    for ( String varName : transVarsInUse ) {
      if ( !varName.equals( "Internal.Transformation.Filename.Directory" ) ) {
        String varValue = transMeta.getVariable( varName, "" );
        m_env.addVariable( varName, varValue );
      }
    }
  }

  @Override public boolean processRow( StepMetaInterface smi, StepDataInterface sdi ) throws KettleException {

    Object[] row = getRow();

    if ( first ) {
      first = false;

      // output row meta setup + KF step validation etc.
      Object inRowMeta = getInputRowMeta();
      m_data.setOutputRowMeta( getInputRowMeta().clone() );

      // if we have no ARFF metadata, then assume all fields are to be injected
      if ( m_data.getInjectFields() == null || m_data.getInjectFields().length == 0 ) {
        m_data.setupArffMeta( getInputRowMeta() );
      }

      // Now check to see if the incoming rows have these
      // fields and set up indexes. (pre-configured step might now have a new
      // incoming stream that is not compatible with its
      // configuration)
      m_data.buildAndValidateInjectMapping( getInputRowMeta() );

      // Determine the output format (set up the RowMetaInterface stored in the
      // data class)
      m_meta.getFields( m_data.getOutputRowMeta(), getStepname(), null, null, this, getRepository(), getMetaStore() );

      // not injecting data - just launch
      if ( !m_injectDataIntoKF ) {
        if ( !isStopped() ) {
          try {
            m_data.executeNoInject( new ExecutionFinishedCallback() {
              @Override public void executionFinished() {
                // just wait a little bit, to allow us to process any connection
                // data that we might be listening for
                try {
                  Thread.sleep( 2000 );
                } catch ( InterruptedException e ) {
                  // ignore
                }
                if ( !m_processingKFOutput.get() ) {
                  m_flowExecutionFinished = true;
                }
              }
            } );
          } catch ( WekaException e ) {
            throw new KettleException( e );
          }
        }
      }
    }

    if ( isStopped() ) {
      return false;
    }

    if ( row == null ) {
      if ( m_flowOutputException != null ) {
        throw new KettleException( m_flowOutputException );
      }

      // Finished?
      if ( m_flowExecutionFinished && !m_processingKFOutput.get() ) {
        setOutputDone();
        return false;
      }

      if ( m_injectDataIntoKF ) {
        if ( !m_meta.getStreamData() ) {
          if ( !m_batchProcessingLaunched ) {
            try {
              m_batchProcessingLaunched = true;
              m_data.injectDataBatch( new ExecutionFinishedCallback() {
                @Override public void executionFinished() {
                  // just wait a little bit, to allow us to process any connection
                  // data that we might be listening for
                  try {
                    Thread.sleep( 2000 );
                  } catch ( InterruptedException e ) {
                    // ignore
                  }
                  //if ( !m_processingKFOutput.get() ) {
                  m_flowExecutionFinished = true;
                  //}
                }
              }, getInputRowMeta(), m_meta, this );
            } catch ( WekaException ex ) {
              throw new KettleException( ex );
            }
          }
        } else {
          try {
            m_data.processRow( null, getInputRowMeta() );
            m_flowExecutionFinished = true;
            return false;
          } catch ( Exception e ) {
            throw new KettleException( e );
          }
        }
      }
    } else {
      if ( m_meta.getPassRowsThrough() ) {
        putRow( m_data.getOutputRowMeta(), row );
      } else {
        try {
          m_data.processRow( row, getInputRowMeta() );
        } catch ( Exception ex ) {
          throw new KettleException( ex );
        }
      }
    }

    return true;
  }

  @Override public boolean init( StepMetaInterface stepMeta, StepDataInterface stepData ) {
    if ( super.init( stepMeta, stepData ) ) {
      m_meta = (PMIFlowExecutorMeta) stepMeta;
      m_data = (PMIFlowExecutorData) stepData;

      m_flowExecutionFinished = false;
      m_batchProcessingLaunched = false;
      m_processingKFOutput = new AtomicBoolean( false );

      try {
        // m_data.setOutputRowMeta( getInputRowMeta().clone() );

        // If a a file name is set, then load from file
        if ( !org.pentaho.di.core.util.Utils.isEmpty( m_meta.getSerializedFlowFileName() ) ) {
          try {
            m_flowCopy = PMIFlowExecutorData.getFlowFromFileVFS( m_meta.getSerializedFlowFileName(), this, m_env );
          } catch ( Exception e ) {
            throw new KettleException(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlow.Error.ProblemDeserializingFlowFile" ) );
          }
        } else if ( !org.pentaho.di.core.util.Utils.isEmpty( m_meta.getFlow() ) ) {
          try {
            m_flowCopy = PMIFlowExecutorData.getFlowFromJSON( m_meta.getFlow() );
          } catch ( Exception e ) {
            throw new KettleException(
                BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlow.Error.ProblemDeserializingFlow" ) );
          }
        } else {
          throw new KettleException(
              BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlow.Error.UnableToFindAFlowToExecute" ) );
        }

        m_data.setLog( getLogChannel() );
        m_data.setFlow( m_flowCopy, m_env, getLogLevel() );

        // If we are injecting data into the knowledge flow, then validate the specified inject step
        m_injectDataIntoKF = !org.pentaho.di.core.util.Utils.isEmpty( m_meta.getInjectStepName() );
        if ( m_injectDataIntoKF ) {
          logBasic(
              BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlow.Info.PreparingForInjectingIntoKF" ) );
        }

        m_listeningForOutputFromKF =
            !org.pentaho.di.core.util.Utils.isEmpty( m_meta.getOutputStepName() ) && !m_meta.getPassRowsThrough();

        if ( m_listeningForOutputFromKF ) {
          logBasic( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlow.Info.ReceivingDataFromKFStep",
              environmentSubstitute( m_meta.getOutputStepName() ) ) );
        }

        // Validate input and output steps (as necessary)
        if ( m_injectDataIntoKF ) {
          m_data
              .validateInputStep( m_meta.getInjectStepName(), m_meta.getInjectConnectionName(), m_meta.getStreamData(),
                  this );

          if ( m_meta.getSetClass() ) {
            m_data.setClassAttributeName( environmentSubstitute( m_meta.getClassAttributeName() ) );
          }

          m_data.setInjectFields( m_meta.getInjectFields() );
          // if we have no ARFF metadata, then assume all fields are to be injected
          //if ( m_data.getInjectFields() == null || m_data.getInjectFields().length == 0 ) {
          //            m_data.setupArffMeta( getInputRowMeta() );
          //        }

          // Now check to see if the incoming rows have these
          // fields and set up indexes. (pre-configured step might now have a new
          // incoming stream that is not compatible with its
          // configuration)
          // m_data.buildAndValidateInjectMapping( getInputRowMeta() );

          // initialize the reservoir
          if ( !m_meta.getStreamData() ) {
            logBasic( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlow.Info.InitializingReservoir" ) );
          } else {
            m_data.setSampleRelationName( environmentSubstitute( m_meta.getSampleRelationName() ) );
          }

          try {
            m_data.initializeReservoir( Integer.parseInt( environmentSubstitute( m_meta.getSampleSize() ) ),
                Integer.parseInt( environmentSubstitute( m_meta.getRandomSeed() ) ) );
          } catch ( NumberFormatException ex ) {
            throw new KettleException( ex );
          }

          if ( m_data.getBufferingForStreaming() ) {
            logBasic( BaseMessages.getString( PMIFlowExecutorMeta.PKG,
                "KnowledgeFlow.Info.BufferingRowsToDetermineValuesForNominalFields",
                environmentSubstitute( m_meta.getSampleSize() ) ) );
          }
        }

        // Determine the output format (set up the RowMetaInterface stored in the
        // data class)
        // m_meta.getFields( m_data.getOutputRowMeta(), getStepname(), null, null, this );

        if ( m_listeningForOutputFromKF ) {
          logBasic( BaseMessages.getString( PMIFlowExecutorMeta.PKG,
              "KnowledgeFlow.Info.ValidatingOutputKFStepAndRegisteringAsListener" ) );
          m_data.m_outputStep =
              PMIFlowExecutorData
                  .validateOutputStep( m_flowCopy, m_meta.getOutputStepName(), m_meta.getOutputConnectionName(), this,
                      this, getLogChannel() ).getManagedStep();
        }
      } catch ( KettleException ex ) {
        logError( ex.getMessage(), ex );
        return false;
      }
    }

    return true;
  }

  @Override public boolean dataFromStep( Data data ) throws WekaException {
    logDetailed( BaseMessages.getString( PMIFlowExecutorMeta.PKG, "KnowledgeFlow.Info.ReceivedDataFromKFStep",
        data.getSourceStep().getName() ) );
    m_processingKFOutput.set( true );
    if ( data.getConnectionName().equals( CON_TEXT ) ) {
      handleTextData( data );
    } else if ( data.getConnectionName().equals( CON_DATASET ) ) {
      handleDataSet( data );
    } else if ( data.getConnectionName().equals( CON_BATCH_CLASSIFIER ) || data.getConnectionName()
        .equals( CON_INCREMENTAL_CLASSIFIER ) || data.getConnectionName().equals( CON_BATCH_CLUSTERER ) || data
        .getConnectionName().equals( CON_INCREMENTAL_CLUSTERER ) || data.getConnectionName()
        .equals( CON_BATCH_ASSOCIATOR ) ) {
      handleModel( data );
    } else if ( data.getConnectionName().equalsIgnoreCase( StepManager.CON_AUX_DATA_BATCH_ASSOCIATION_RULES ) ) {
      handleAssociationRules( data );
    }

    // setOutputDone();
    // m_flowExecutionFinished = true;
    m_processingKFOutput.set( false );
    return true;
  }

  protected void handleDataSet( Data data ) {
    Instances insts = data.getPrimaryPayload();
    instancesToRows( insts );
  }

  protected void handleTrainingSet( Data data ) {
    Instances insts = data.getPrimaryPayload();
    instancesToRows( insts );
  }

  protected void handleTestSet( Data data ) {
    Instances insts = data.getPrimaryPayload();
    instancesToRows( insts );
  }

  protected void handleModel( Data data ) {
    Object model = data.getPrimaryPayload();
    Instances
        header =
        data.getConnectionName().equals( CON_INCREMENTAL_CLASSIFIER ) ?
            ( (Instance) data.getPayloadElement( CON_AUX_DATA_TEST_INSTANCE ) ).dataset() :
            (Instances) data.getPayloadElement( CON_AUX_DATA_TRAININGSET );
    header = new Instances( header, 0 );
    try {
      serializeModelAndOutputRow( model, header );
    } catch ( Exception ex ) {
      m_flowOutputException = ex;
    }
  }

  protected void handleAssociationRules( Data data ) {
    AssociationRules rules = data.getPrimaryPayload();

    if ( rules.getNumRules() > 0 ) {
      List<AssociationRule> rulesList = rules.getRules();
      int rowWidth = 3 + rulesList.get( 0 ).getMetricNamesForRule().length;
      for ( AssociationRule r : rulesList ) {
        Object[] row = new Object[rowWidth];
        Collection<Item> premise = r.getPremise();
        StringBuilder temp = new StringBuilder();
        boolean fst = true;
        for ( Item i : premise ) {
          if ( fst ) {
            temp.append( i.toString() );
            fst = false;
          } else {
            temp.append( "," + i.toString() ); //$NON-NLS-1$
          }
        }
        row[0] = temp.toString();

        Collection<Item> consequence = r.getConsequence();
        temp = new StringBuilder();
        fst = true;
        for ( Item i : consequence ) {
          if ( fst ) {
            temp.append( i.toString() );
            fst = false;
          } else {
            temp.append( "," + i.toString() ); //$NON-NLS-1$
          }
        }
        row[1] = temp.toString();

        // support
        row[2] = r.getTotalSupport();

        // now the metrics
        double[] metrics = null;
        try {
          metrics = r.getMetricValuesForRule();
        } catch ( Exception e1 ) {
          // e1.printStackTrace();
        }
        if ( metrics != null ) {
          for ( int i = 3; i < row.length; i++ ) {
            // check for missing values
            if ( Utils.isMissingValue( metrics[i - 3] ) ) {
              row[i] = null;
            } else {
              row[i] = metrics[i - 3];
            }
          }
        }

        try {
          putRow( m_data.getOutputRowMeta(), row );
        } catch ( KettleStepException ex ) {
          m_flowOutputException = ex;
        }
      }
    }
  }

  protected void serializeModelAndOutputRow( Object model, Instances header ) throws IOException, KettleStepException {
    ByteArrayOutputStream ostream = new ByteArrayOutputStream();
    OutputStream os = ostream;
    ObjectOutputStream p;

    p = new ObjectOutputStream( new BufferedOutputStream( new GZIPOutputStream( os ) ) );
    p.writeObject( model );
    if ( header != null ) {
      p.writeObject( header );
    }
    p.flush();
    p.close();

    byte[] bytes = ostream.toByteArray();

    Object[] row = new Object[3];
    row[0] = model.getClass().getName();
    if ( model instanceof OptionHandler ) {
      row[1] = Utils.joinOptions( ( (OptionHandler) model ).getOptions() );
    }
    row[2] = bytes;
    putRow( m_data.getOutputRowMeta(), row );
  }

  protected void instancesToRows( Instances instancesOut ) {
    int width = instancesOut.numAttributes();
    for ( int i = 0; i < instancesOut.numInstances(); i++ ) {
      Instance temp = instancesOut.instance( i );
      Object[] row = new Object[width];
      for ( int j = 0; j < width; j++ ) {
        switch ( temp.attribute( j ).type() ) {
          case Attribute.NUMERIC:
            if ( temp.isMissing( j ) ) {
              row[j] = null;
            } else {
              row[j] = temp.value( j );
            }
            break;
          case Attribute.NOMINAL:
            if ( temp.isMissing( j ) ) {
              row[j] = ""; // empty string for missing? //$NON-NLS-1$
            } else {
              row[j] = temp.attribute( j ).value( (int) temp.value( j ) );
            }
            break;
          case Attribute.DATE:
            break;
        }
      }

      try {
        putRow( m_data.getOutputRowMeta(), row );
      } catch ( KettleStepException ex ) {
        m_flowOutputException = ex;
      }
    }
  }

  protected void handleTextData( Data data ) {
    String text = data.getPrimaryPayload();
    String title = data.getPayloadElement( CON_AUX_DATA_TEXT_TITLE );
    Object[] r = new Object[2];
    r[0] = title;
    r[1] = text;

    try {
      putRow( m_data.getOutputRowMeta(), r );
    } catch ( KettleStepException ex ) {
      m_flowOutputException = ex;
    }
  }
}
