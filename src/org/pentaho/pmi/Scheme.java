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

package org.pentaho.pmi;

import weka.core.Attribute;
import weka.core.Instances;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for learning schemes provided by underlying ML engines.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public abstract class Scheme {

  // TODO add gradient boosted trees for regression?

  /**
   * The name of this scheme - concrete subclasses will set this
   */
  protected String m_schemeName;

  /**
   * Any additional options the scheme/engine might require
   */
  protected Map<String, Object> m_additionalOptions = new LinkedHashMap<>();

  /**
   * Map (keyed by Weka filter name) of options (for key filter). These filters will be assembled into a MultiFilter
   * and combined with a FilteredClassifier at execution time
   */
  protected Map<String, String> m_preprocessingConfigs = new LinkedHashMap<>();

  /**
   * Map (keyed by Weka sampling filter name) of options (for key sampler). These sampling filters will be applied to
   * incoming data before it gets passed to the scheme (in the case of cross-validation/hold-out evals they will be
   * added to the FilteredClassifier config). Allows for Resample (supervised and unsupervised) only at present. Might
   * add SMOTE in the future.
   */
  protected Map<String, String> m_samplingConfigs = new LinkedHashMap<>();

  /**
   * Constructor that sets the name of this scheme
   *
   * @param schemeName the name of this scheme
   */
  public Scheme( String schemeName ) {
    m_schemeName = schemeName;
  }

  /**
   * Set any additional options that this Scheme/Engine might require
   *
   * @param additionalOptions additional options that this Scheme/Engine might require
   */
  public void setAdditionalOptions( Map<String, Object> additionalOptions ) {
    m_additionalOptions = additionalOptions;
  }

  /**
   * Get any additional options that this Scheme/Engine might require
   *
   * @return additional options that this Scheme/Engine might require
   */
  public Map<String, Object> getAdditionalOptions() {
    return m_additionalOptions;
  }

  /**
   * Set preprocessing filter configurations to apply. Map holds filter class name as key and command line options
   * as values.
   *
   * @param preprocessingConfigs preprocessing filter configurations to apply
   */
  public void setPreprocessingConfigs( Map<String, String> preprocessingConfigs ) {
    m_preprocessingConfigs = preprocessingConfigs;
  }

  /**
   * Get preprocessing filter configurations to apply. Map holds filter class name as key and command line options
   * as values.
   *
   * @return preprocessing filter configurations to apply
   */
  public Map<String, String> getPreprocessingConfigs() {
    return m_preprocessingConfigs;
  }

  /**
   * Set (re)sampling filter configurations to apply. Map holds sampling filter class name as key and command line options
   * as values.
   *
   * @param samplingConfigs (re)sampling configurations to apply
   */
  public void setSamplingConfigs( Map<String, String> samplingConfigs ) {
    m_samplingConfigs = samplingConfigs;
  }

  /**
   * Get (re)sampling filter configurations to apply. Map holds sampling filter class name as key and command line options
   * as values.
   *
   * @return (re)sampling configurations to apply
   */
  public Map<String, String> getSamplingConfigs() {
    return m_samplingConfigs;
  }

  /**
   * Returns true if the currently selected underlying scheme can handle the supplied data (including potentially the
   * class type)
   *
   * @param data     the header of the data that will be used for training
   * @param messages a list to store messages describing any problems/warnings for the selected scheme with respect to the incoming data
   * @return true if the underlying scheme can process the data
   */
  public abstract boolean canHandleData( Instances data, List<String> messages );

  /**
   * Sublcass should return true if the scheme supports training incrementally (i.e. row-by-row updates rather than
   * batch training); otherwise should return false.
   *
   * @return true if the scheme supports incremental training
   */
  public abstract boolean supportsIncrementalTraining();

  /**
   * Get a map of meta information about the scheme and its parameters, useful for building editor dialogs
   *
   * @return a map of meta information about the scheme and its parameters
   * @throws Exception if a problem occurs
   */
  public abstract Map<String, Object> getSchemeInfo() throws Exception;

  /**
   * Configure the underlying scheme using the supplied command-line option settings
   *
   * @param options an array of command-line option settings
   * @throws Exception if a problem occurs
   */
  public abstract void setSchemeOptions( String[] options ) throws Exception;

  /**
   * Get the underlying scheme's command line option settings. This may be different from those
   * that could be obtained from scheme returned by {@code getConfiguredScheme()}, as the configured
   * scheme might be a wrapper (meta classifier) around the underlying scheme.
   *
   * @return the options of the underlying scheme
   */
  public abstract String[] getSchemeOptions();

  /**
   * Set underlying scheme parameters from a map of parameter values. Note that this will set only primitive parameter
   * types on the scheme. It does not process nested objects. This method is used primarily by the GUI editor dialogs. Use
   * setSchemeOptions() to handle all parameters (including those on nested complex objects).
   *
   * @param schemeParameters a map of scheme parameters to set
   * @throws Exception if a problem occurs.
   */
  public abstract void setSchemeParameters( Map<String, Map<String, Object>> schemeParameters ) throws Exception;

  /**
   * Return the underlying predictive scheme, configured and ready to use. The incoming training data
   * is supplied so that the scheme can decide (based on data characteristics) whether the underlying scheme
   * needs to be combined with data filters in order to be applicable to the data. E.g. The user might have selected
   * logistic regression which, in the given engine, can only support binary class problems. At execution time, the
   * incoming data could have more than two class labels, in which case the underlying scheme will need to be wrapped
   * in a MultiClassClassifier.
   *
   * @param trainingHeader the header of the incoming training data
   * @return the underlying predictive scheme
   * @throws Exception if there is a problem configuring the scheme
   */
  // TODO add log channel to report messages to
  public abstract Object getConfiguredScheme( Instances trainingHeader ) throws Exception;

  /**
   * Get the name of this scheme.
   *
   * @return
   */
  public String getSchemeName() {
    return m_schemeName;
  }

  /**
   * Add a warning message to messages if string attributes are present in the incoming data.
   *
   * @param data     the structure of the incoming data
   * @param messages a list of messages to add a warning message to
   */
  protected void addStringAttributeWarningMessageIfNeccessary( Instances data, List<String> messages ) {
    if ( SchemeUtils.checkForAttributeType( data, Attribute.STRING, true ) ) {
      messages.add( "Incoming data contains raw string fields - make sure that text vectorization is turned on." );
    }
  }
}
