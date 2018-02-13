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

import org.pentaho.di.core.util.Utils;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.dm.commons.ArffMeta;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;
import weka.core.Attribute;
import weka.core.Environment;
import weka.core.Instances;
import weka.core.WekaException;
import weka.knowledgeflow.Flow;
import weka.knowledgeflow.StepManager;
import weka.knowledgeflow.StepManagerImpl;

import java.util.List;

/**
 * Contains the metadata for the PMI Flow Executor step
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIFlowExecutor", image = "WEKAS.svg", name = "PMI Flow Executor", description =
    "Executes a WEKA Knowledge Flow data "
        + "mining process", documentationUrl = "http://wiki.pentaho.com/display/EAI/Knowledge+Flow", categoryDescription = "Data Mining" )
public class PMIFlowExecutorMeta extends BaseStepMeta implements StepMetaInterface {

  public static Class<?> PKG = PMIFlowExecutorMeta.class;

  /**
   * XML tag for the KF step
   */
  public static final String XML_TAG = "weka_kf"; //$NON-NLS-1$

  /**
   * Meta data for the ARFF instances input to the inject step
   */
  protected ArffMeta[] m_injectFields;

  /**
   * File name of the serialized Weka knowledge flow load/import
   */
  private String m_flowFileName;

  /**
   * Whether to store the flow in the step meta data or not
   */
  private boolean m_storeFlowInStepMetaData;

  /**
   * Name of the KnowledgeFlow step to inject data into
   */
  private String m_injectStepName;

  /**
   * Name of the event to generate for the inject step
   */
  private String m_injectEventName;

  /**
   * Name of the KnowledgeFlow step to listen for output from
   */
  private String m_outputStepName;

  /**
   * Name of the event to listen for from the output step
   */
  private String m_outputConnectionName;

  /**
   * Pass rows through rather than listening for output
   */
  private boolean m_passRowsThrough = false;

  /**
   * Holds the actual knowledge flow to run
   */
  private String m_flow;

  /**
   * Relation name for sampled data set
   */
  private String m_sampleRelationName = "Sampled rows"; //$NON-NLS-1$

  /**
   * Number of rows to sample
   */
  private String m_numRowsToSample = "0";

  /**
   * Random seed for reserviour sampling
   */
  private String m_randomSeed = "1";

  /**
   * don't sample, stream instead
   */
  private boolean m_streamData;

  /**
   * Class attribute/column?
   */
  private boolean m_setClass;

  /**
   * The Class attribute name
   */
  private String m_classAttribute;

  /**
   * True if the output structure has been determined successfully in advance of the input data being seen in its
   * entirety
   */
  private boolean m_structureDetermined;

  /**
   * Set whether to store the XML flow description as part of the step meta data. In this case the source file path is
   * ignored (and cleared for that matter)
   *
   * @param s true if the flow should be stored in the step meta data
   */
  public void setStoreFlowInStepMetaData( boolean s ) {
    m_storeFlowInStepMetaData = s;
  }

  /**
   * Get whether to store the XML flow description as part of the step meta data. In this case the source file path is
   * ignored (and cleared for that matter)
   *
   * @return true if the flow should be stored in the step meta data
   */
  public boolean getStoreFlowInStepMetaData() {
    return m_storeFlowInStepMetaData;
  }

  /**
   * Set the relation name to use for the sampled data.
   *
   * @param relationName the relation name to use
   */
  public void setSampleRelationName( String relationName ) {
    m_sampleRelationName = relationName;
  }

  /**
   * Get the relation name to use for the sampled data.
   *
   * @return the relation name to use
   */
  public String getSampleRelationName() {
    return m_sampleRelationName;
  }

  /**
   * Get the number of rows to randomly sample.
   *
   * @return the number of rows to sample
   */
  public String getSampleSize() {
    return m_numRowsToSample;
  }

  /**
   * Set the number of rows to randomly sample.
   *
   * @param size the number of rows to sample
   */
  public void setSampleSize( String size ) {
    m_numRowsToSample = size;
  }

  /**
   * Get the random seed to use for sampling.
   *
   * @return the random seed
   */
  public String getRandomSeed() {
    return m_randomSeed;
  }

  /**
   * Set the random seed to use for sampling rows.
   *
   * @param seed the seed to use
   */
  public void setRandomSeed( String seed ) {
    m_randomSeed = seed;
  }

  /**
   * Get whether incoming kettle rows are to be passed through to any downstream kettle steps (rather than output of
   * knowledge flow being passed on)
   *
   * @return true if rows are to be passed on to downstream kettle steps
   */
  public boolean getPassRowsThrough() {
    return m_passRowsThrough;
  }

  /**
   * Set whether incoming kettle rows are to be passed through to any downstream kettle steps (rather than output of the
   * knowledge flow being passed on).
   *
   * @param p true if rows are to be passed on to downstream kettle steps
   */
  public void setPassRowsThrough( boolean p ) {
    m_passRowsThrough = p;
  }

  /**
   * Set the file name of the serialized Weka flow to load/import from.
   *
   * @param fFile the file name
   */
  public void setSerializedFlowFileName( String fFile ) {
    m_flowFileName = fFile;
  }

  /**
   * Get the file name of the serialized Weka flow to load/import from.
   *
   * @return the file name of the serialized Weka flow
   */
  public String getSerializedFlowFileName() {
    return m_flowFileName;
  }

  /**
   * Set the actual knowledgeflow flows to run.
   *
   * @param flow the flows to run
   */
  public void setFlow( String flow ) {
    m_flow = flow;
  }

  /**
   * Get the knowledgeflow flow to be run.
   *
   * @return the flow to be run
   */
  public String getFlow() {
    return m_flow;
  }

  /**
   * Set the name of the step to inject data into.
   *
   * @param isn the name of the step to inject data into
   */
  public void setInjectStepName( String isn ) {
    m_injectStepName = isn;
  }

  /**
   * Get the name of the step to inject data into.
   *
   * @return the name of the step to inject data into
   */
  public String getInjectStepName() {
    return m_injectStepName;
  }

  /**
   * Set the name of the event to use for injecting.
   *
   * @param ien the name of the event to use for injecting
   */
  public void setInjectConnectionName( String ien ) {
    m_injectEventName = ien;
  }

  /**
   * Get the name of the event to use for injecting.
   *
   * @return the name of the event to use for injecting
   */
  public String getInjectConnectionName() {
    return m_injectEventName;
  }

  /**
   * Set the name of the step to listen to for output.
   *
   * @param osn the name of the step to listen to for output
   */
  public void setOutputStepName( String osn ) {
    m_outputStepName = osn;
  }

  /**
   * Get the name of the step to listen to for output.
   *
   * @return the name of the step to listen to for output
   */
  public String getOutputStepName() {
    return m_outputStepName;
  }

  /**
   * Set the name of the connection to use for output from the flow
   *
   * @param oen the name of the connection to use for output from the flow
   */
  public void setOutputConnectionName( String oen ) {
    m_outputConnectionName = oen;
  }

  /**
   * Get the name of the connection to use for output from the flow
   *
   * @return the name of the connection to use for output from the flow
   */
  public String getOutputConnectionName() {
    return m_outputConnectionName;
  }

  /**
   * Set whether to set a class index in the sampled data.
   *
   * @param sc true if a class index is to be set in the data
   */
  public void setSetClass( boolean sc ) {
    m_setClass = sc;
  }

  /**
   * Get whether a class index is to be set in the sampled data.
   *
   * @return true if a class index is to be set in the sampled data
   */
  public boolean getSetClass() {
    return m_setClass;
  }

  /**
   * Set the name of the attribute to be set as the class attribute.
   *
   * @param ca the name of the class attribute
   */
  public void setClassAttributeName( String ca ) {
    m_classAttribute = ca;
  }

  /**
   * Get the name of the attribute to be set as the class attribute.
   *
   * @return the name of the class attribute
   */
  public String getClassAttributeName() {
    return m_classAttribute;
  }

  /**
   * Set whether data should be streamed to the knowledge flow when injecting rather than batch injected.
   *
   * @param sd true if data should be streamed
   */
  public void setStreamData( boolean sd ) {
    m_streamData = sd;
  }

  /**
   * Get whether data is to be streamed to the knowledge flow when injecting rather than batch injected.
   *
   * @return true if data is to be streamed
   */
  public boolean getStreamData() {
    return m_streamData;
  }

  /**
   * Set the array of meta data for the inject step
   *
   * @param am an array of ArffMeta
   */
  public void setInjectFields( ArffMeta[] am ) {
    m_injectFields = am;
  }

  /**
   * Get the meta data for the inject step
   *
   * @return an array of ArffMeta
   */
  public ArffMeta[] getInjectFields() {
    return m_injectFields;
  }

  /**
   * Return the XML describing this (configured) step
   *
   * @return a <code>String</code> containing the XML
   */
  @Override public String getXML() {
    StringBuilder retval = new StringBuilder( 100 );

    retval.append( "<" + XML_TAG + ">" ); //$NON-NLS-1$ //$NON-NLS-2$

    retval.append( XMLHandler.addTagValue( "store_flow_in_meta", //$NON-NLS-1$
        m_storeFlowInStepMetaData ) );

    retval.append( XMLHandler.addTagValue( "inject_step", m_injectStepName ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "inject_event", m_injectEventName ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "output_step", m_outputStepName ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "output_event", m_outputConnectionName ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "pass_rows_through", m_passRowsThrough ) ); //$NON-NLS-1$

    // can we save the flow as XML?
    if ( !Utils.isEmpty( m_flow ) && Utils.isEmpty( m_flowFileName ) ) {
      retval.append( XMLHandler.addTagValue( "kf_flow", m_flow ) ); //$NON-NLS-1$
    } else {
      retval.append( XMLHandler.addTagValue( "flow_file_name", m_flowFileName ) ); //$NON-NLS-1$
    }

    retval.append( "    <arff>" ).append( Const.CR ); //$NON-NLS-1$
    if ( m_injectFields != null ) {
      for ( ArffMeta m_injectField : m_injectFields ) {
        if ( m_injectField != null ) {
          retval.append( "        " ).append( m_injectField.getXML() ) //$NON-NLS-1$
              .append( Const.CR );
        }
      }
    }
    retval.append( "    </arff>" + Const.CR ); //$NON-NLS-1$

    retval.append( XMLHandler.addTagValue( "stream_data", m_streamData ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "sample_relation_name", //$NON-NLS-1$
        m_sampleRelationName ) );
    retval.append( XMLHandler.addTagValue( "reservoir_size", m_numRowsToSample ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "random_seed", m_randomSeed ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "set_class", m_setClass ) ); //$NON-NLS-1$
    retval.append( XMLHandler.addTagValue( "class_attribute", m_classAttribute ) ); //$NON-NLS-1$

    retval.append( "</" + XML_TAG + ">" ); //$NON-NLS-1$ //$NON-NLS-2$

    return retval.toString();
  }

  /**
   * Loads the meta data for this (configured) step from XML.
   *
   * @param stepnode  the step to load
   * @param dbs       the available list of databases to reference to
   * @param metaStore the metastore to optionally load external metadata from
   * @throws KettleXMLException if an error occurs
   */
  @Override public void loadXML( Node stepnode, List<DatabaseMeta> dbs, IMetaStore metaStore )
      throws KettleXMLException {
    int nrModels = XMLHandler.countNodes( stepnode, XML_TAG );

    if ( nrModels > 0 ) {
      Node wekanode = XMLHandler.getSubNodeByNr( stepnode, XML_TAG, 0 );

      // try and get the XML-based knowledge flow
      boolean success = false;

      try {
        m_flow = XMLHandler.getTagValue( wekanode, "kf_flow" ); //$NON-NLS-1$

        if ( !Utils.isEmpty( m_flow ) ) {
          success = true;
        }
      } catch ( Exception ex ) {
        success = false;
      }

      if ( !success ) {
        m_flowFileName = XMLHandler.getTagValue( wekanode, "flow_file_name" ); //$NON-NLS-1$
      }

      String store = XMLHandler.getTagValue( wekanode, "store_flow_in_meta" ); //$NON-NLS-1$
      if ( !Utils.isEmpty( store ) ) {
        m_storeFlowInStepMetaData = store.equalsIgnoreCase( "Y" ); //$NON-NLS-1$
      }

      m_injectStepName = XMLHandler.getTagValue( wekanode, "inject_step" ); //$NON-NLS-1$
      m_injectEventName = XMLHandler.getTagValue( wekanode, "inject_event" ); //$NON-NLS-1$
      m_outputStepName = XMLHandler.getTagValue( wekanode, "output_step" ); //$NON-NLS-1$
      m_outputConnectionName = XMLHandler.getTagValue( wekanode, "output_event" ); //$NON-NLS-1$

      String temp = XMLHandler.getTagValue( wekanode, "pass_rows_through" ); //$NON-NLS-1$
      m_passRowsThrough = !temp.equalsIgnoreCase( "N" );

      Node fields = XMLHandler.getSubNode( wekanode, "arff" ); //$NON-NLS-1$
      int nrfields = XMLHandler.countNodes( fields, ArffMeta.XML_TAG );

      m_injectFields = new ArffMeta[nrfields];

      for ( int i = 0; i < nrfields; i++ ) {
        Node fnode = XMLHandler.getSubNodeByNr( fields, ArffMeta.XML_TAG, i );
        m_injectFields[i] = new ArffMeta( fnode );
      }

      temp = XMLHandler.getTagValue( wekanode, "stream_data" ); //$NON-NLS-1$
      if ( temp.equalsIgnoreCase( "N" ) ) { //$NON-NLS-1$
        m_streamData = false;
      } else {
        m_streamData = true;
      }

      m_sampleRelationName = XMLHandler.getTagValue( wekanode, "sample_relation_name" ); //$NON-NLS-1$
      m_randomSeed = XMLHandler.getTagValue( wekanode, "random_seed" ); //$NON-NLS-1$
      m_numRowsToSample = XMLHandler.getTagValue( wekanode, "reservoir_size" ); //$NON-NLS-1$
      m_classAttribute = XMLHandler.getTagValue( wekanode, "class_attribute" ); //$NON-NLS-1$

      temp = XMLHandler.getTagValue( wekanode, "set_class" ); //$NON-NLS-1$
      if ( temp.equalsIgnoreCase( "N" ) ) { //$NON-NLS-1$
        m_setClass = false;
      } else {
        m_setClass = true;
      }
    }
  }

  /**
   * Read this step's configuration from a repository
   *
   * @param rep     the repository to access
   * @param id_step the id for this step
   * @throws KettleException if an error occurs
   */
  @Override public void readRep( Repository rep, IMetaStore metaStore, ObjectId id_step, List<DatabaseMeta> dbs )
      throws KettleException {
    // try and get a filename first as this overides any flow stored
    // in the repository
    boolean success = false;
    try {
      m_flowFileName = rep.getStepAttributeString( id_step, 0, "flow_file_name" ); //$NON-NLS-1$
      success = true;
      if ( m_flowFileName == null || Utils.isEmpty( m_flowFileName ) ) {
        success = false;
      }
    } catch ( KettleException ex ) {
      success = false;
    }

    if ( !success ) {
      m_flow = rep.getStepAttributeString( id_step, 0, "kf_flow" ); //$NON-NLS-1$
    }

    m_storeFlowInStepMetaData = rep.getStepAttributeBoolean( id_step, 0, "store_flow_in_meta" ); //$NON-NLS-1$

    m_injectStepName = rep.getStepAttributeString( id_step, 0, "inject_step" ); //$NON-NLS-1$
    m_injectEventName = rep.getStepAttributeString( id_step, 0, "inject_event" ); //$NON-NLS-1$
    m_outputStepName = rep.getStepAttributeString( id_step, 0, "output_step" ); //$NON-NLS-1$
    m_outputConnectionName = rep.getStepAttributeString( id_step, 0, "output_event" ); //$NON-NLS-1$
    m_passRowsThrough = rep.getStepAttributeBoolean( id_step, 0, "pass_rows_through" ); //$NON-NLS-1$

    int numFields = rep.countNrStepAttributes( id_step, "arff_field" ); //$NON-NLS-1$

    m_injectFields = new ArffMeta[numFields];

    for ( int i = 0; i < numFields; i++ ) {
      m_injectFields[i] = new ArffMeta( rep, id_step, i );
    }

    m_streamData = rep.getStepAttributeBoolean( id_step, 0, "stream_data" ); //$NON-NLS-1$
    m_sampleRelationName = rep.getStepAttributeString( id_step, 0, "sample_relation_name" ); //$NON-NLS-1$
    m_numRowsToSample = rep.getStepAttributeString( id_step, 0, "reservoir_size" ); //$NON-NLS-1$
    m_randomSeed = rep.getStepAttributeString( id_step, 0, "random_seed" ); //$NON-NLS-1$
    m_setClass = rep.getStepAttributeBoolean( id_step, 0, "set_class" ); //$NON-NLS-1$
    m_classAttribute = rep.getStepAttributeString( id_step, 0, "class_attribute" ); //$NON-NLS-1$
  }

  /**
   * Save this step's meta data to a repository
   *
   * @param rep               the repository to save to
   * @param id_transformation transformation id
   * @param id_step           step id
   * @throws KettleException if an error occurs
   */
  @Override public void saveRep( Repository rep, IMetaStore metaStore, ObjectId id_transformation, ObjectId id_step )
      throws KettleException {

    rep.saveStepAttribute( id_transformation, id_step, 0, "store_flow_in_meta", //$NON-NLS-1$
        m_storeFlowInStepMetaData );

    rep.saveStepAttribute( id_transformation, id_step, 0, "inject_step", m_injectStepName );
    rep.saveStepAttribute( id_transformation, id_step, 0, "inject_event", m_injectEventName );
    rep.saveStepAttribute( id_transformation, id_step, 0, "output_step", m_outputStepName );
    rep.saveStepAttribute( id_transformation, id_step, 0, "output_event", m_outputConnectionName );
    rep.saveStepAttribute( id_transformation, id_step, 0, "pass_rows_through", m_passRowsThrough );

    if ( !Utils.isEmpty( m_flow ) && Utils.isEmpty( m_flowFileName ) ) {
      rep.saveStepAttribute( id_transformation, id_step, 0, "kf_flow", m_flow );
    } else {
      rep.saveStepAttribute( id_transformation, id_step, 0, "flow_file_name", m_flowFileName );
    }

    if ( m_injectFields != null ) {
      for ( int i = 0; i < m_injectFields.length; i++ ) {
        if ( m_injectFields[i] != null ) {
          m_injectFields[i].saveRep( rep, id_transformation, id_step, i );
        }
      }
    }

    rep.saveStepAttribute( id_transformation, id_step, 0, "stream_data", m_streamData );
    rep.saveStepAttribute( id_transformation, id_step, 0, "sample_relation_name", m_sampleRelationName );
    rep.saveStepAttribute( id_transformation, id_step, 0, "random_seed", m_randomSeed );
    rep.saveStepAttribute( id_transformation, id_step, 0, "reservoir_size", m_numRowsToSample );
    rep.saveStepAttribute( id_transformation, id_step, 0, "set_class", m_setClass );
    rep.saveStepAttribute( id_transformation, id_step, 0, "class_attribute", m_classAttribute );
  }

  /**
   * Set up the outgoing row meta data from the supplied Instances object.
   *
   * @param insts the Instances to use for setting up the outgoing row meta data
   * @param row   holds the final outgoing row meta data
   */
  protected void setUpMetaData( Instances insts, RowMetaInterface row ) throws KettlePluginException {
    row.clear();
    for ( int i = 0; i < insts.numAttributes(); i++ ) {
      Attribute temp = insts.attribute( i );
      String attName = temp.name();
      ValueMetaInterface newVM = null;
      switch ( temp.type() ) {
        case Attribute.NUMERIC:
          newVM = ValueMetaFactory.createValueMeta( attName, ValueMetaInterface.TYPE_NUMBER );
          break;
        case Attribute.NOMINAL:
        case Attribute.STRING:
          newVM = ValueMetaFactory.createValueMeta( attName, ValueMetaInterface.TYPE_STRING );
          break;
        case Attribute.DATE:
          newVM = ValueMetaFactory.createValueMeta( attName, ValueMetaInterface.TYPE_DATE );
          break;
        case Attribute.RELATIONAL:
          logBasic( BaseMessages.getString( PKG, "KnowledgeFlowMeta.Warning.IgnoringRelationalAttributes" ) );
      }

      if ( newVM != null ) {
        row.addValueMeta( newVM );
      }
    }
  }

  /**
   * Gets the fields
   *
   * @param inputRowMeta the input row meta that is modified in this method to reflect the output row metadata of the step
   * @param origin       the name of the step to use as input for the origin field in the values
   * @param info         fields used as extra lookup information
   * @param nextStep     the next step that is targeted
   * @param space        the variable space to use to replace variables
   * @param repository   the repository to use to load Kettle metadata objects impacting the output fields
   * @param metaStore    the MetaStore to use to load additional external data or metadata impacting the output fields
   * @throws KettleStepException if a problem occurs
   */
  public void getFields( RowMetaInterface inputRowMeta, String origin, RowMetaInterface[] info, StepMeta nextStep,
      VariableSpace space, Repository repository, IMetaStore metaStore ) throws KettleStepException {
    Flow flow = null;

    try {
      if ( !Utils.isEmpty( getFlow() ) ) {
        flow = PMIFlowExecutorData.getFlowFromJSON( getFlow() );
      } else if ( !Utils.isEmpty( getSerializedFlowFileName() ) ) {
        flow =
            PMIFlowExecutorData.getFlowFromFileVFS( getSerializedFlowFileName(), space, Environment.getSystemWide() );
      }
    } catch ( Exception ex ) {
      throw new KettleStepException( ex );
    }

    // if we pass rows through, or there is no flow, then there is nothing to be done
    if ( getPassRowsThrough() || flow == null ) {
      return;
    }

    // validate output setup
    try {
      StepManagerImpl
          outputStep =
          PMIFlowExecutorData
              .validateOutputStep( flow, getOutputStepName(), getOutputConnectionName(), null, space, getLog() );

      String outputConnectionName = space.environmentSubstitute( getOutputConnectionName() );
      Instances outputStructure = null;
      try {
        outputStructure = outputStep.getManagedStep().outputStructureForConnectionType( outputConnectionName );
      } catch ( WekaException ex ) {
        throw new KettleStepException( ex );
      }

      if ( outputStructure != null ) {
        try {
          setUpMetaData( outputStructure, inputRowMeta );
          m_structureDetermined = true;
        } catch ( KettlePluginException ex ) {
          throw new KettleStepException( ex );
        }
      } else {
        try {
          // if we are listening to a text connection then it's easy to construct output row metadata
          if ( outputConnectionName.equalsIgnoreCase( "text" ) ) {
            inputRowMeta.clear();
            inputRowMeta.addValueMeta( ValueMetaFactory.createValueMeta( "Title", ValueMetaInterface.TYPE_STRING ) );
            inputRowMeta.addValueMeta( ValueMetaFactory.createValueMeta( "Text", ValueMetaInterface.TYPE_STRING ) );
            m_structureDetermined = true;
          } else if ( outputConnectionName.equalsIgnoreCase( StepManager.CON_BATCH_CLASSIFIER ) || outputConnectionName
              .equalsIgnoreCase( StepManager.CON_INCREMENTAL_CLASSIFIER ) || outputConnectionName
              .equalsIgnoreCase( StepManager.CON_BATCH_CLUSTERER ) || outputConnectionName
              .equalsIgnoreCase( StepManager.CON_BATCH_ASSOCIATOR ) ) {

            // Outputs serialized associator *if* associator does not produce AssociationRules objects
            // (which will be determined above if there is a non-null set of instances from
            // outputStructureForConnectionType. TODO might want to revisit this
            String schemeType = "Classifier";
            if ( outputConnectionName.equalsIgnoreCase( StepManager.CON_BATCH_CLUSTERER ) ) {
              schemeType = "Clusterer";
            } else if ( outputConnectionName.equalsIgnoreCase( StepManager.CON_BATCH_ASSOCIATOR ) ) {
              schemeType = "Asssociator";
            }

            // classifier connections - output name, options and serialized classifier
            inputRowMeta.clear();
            inputRowMeta.addValueMeta(
                ValueMetaFactory.createValueMeta( schemeType + "_name", ValueMetaInterface.TYPE_STRING ) );
            inputRowMeta.addValueMeta(
                ValueMetaFactory.createValueMeta( schemeType + "_options", ValueMetaInterface.TYPE_STRING ) );
            inputRowMeta.addValueMeta( ValueMetaFactory.createValueMeta( schemeType, ValueMetaInterface.TYPE_BINARY ) );
            m_structureDetermined = true;
          } else {
            m_structureDetermined = false;
          }
        } catch ( KettlePluginException ex ) {
          throw new KettleStepException( ex );
        }
      }
    } catch ( KettleException ex ) {
      throw new KettleStepException( ex );
    }
  }

  /**
   * Returns whether we have been able to successfully determine the structure of the output (in advance of seeing all
   * the input rows).
   *
   * @return true if the output structure has been determined.
   */
  public boolean isOutputStructureDetermined() {
    return m_structureDetermined;
  }

  /**
   * Check for equality
   *
   * @param obj an <code>Object</code> to compare with
   * @return true if equal to the supplied object
   */
  @Override public boolean equals( Object obj ) {
    if ( obj != null && ( obj.getClass().equals( this.getClass() ) ) ) {
      PMIFlowExecutorMeta m = (PMIFlowExecutorMeta) obj;
      return ( getXML() == m.getXML() );
    }
    return false;
  }

  /**
   * Clone this step's meta data
   *
   * @return the cloned meta data
   */
  public Object clone() {
    return (PMIFlowExecutorMeta) super.clone();
  }

  /*
 * (non-Javadoc)
 *
 * @see org.pentaho.di.trans.step.StepMetaInterface#setDefault()
 */
  @Override public void setDefault() {
    m_flowFileName = null;
    m_injectStepName = null;
    m_injectEventName = null;
    m_outputStepName = null;
    m_outputConnectionName = null;
    m_passRowsThrough = false;
    m_flow = null;
    m_numRowsToSample = "0"; //$NON-NLS-1$
    m_randomSeed = "1"; //$NON-NLS-1$
    m_streamData = false;
    m_setClass = false;
    m_classAttribute = null;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.pentaho.di.trans.step.BaseStepMeta#getDialogClassName()
   */
  @Override public String getDialogClassName() {
    return "org.pentaho.di.ui.trans.steps.pmi.weka.PMIFlowExecutorDialog";
  }

  @Override
  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int i, TransMeta transMeta,
      Trans trans ) {
    return new PMIFlowExecutor( stepMeta, stepDataInterface, i, transMeta, trans );
  }

  @Override public StepDataInterface getStepData() {
    return new PMIFlowExecutorData();
  }
}
