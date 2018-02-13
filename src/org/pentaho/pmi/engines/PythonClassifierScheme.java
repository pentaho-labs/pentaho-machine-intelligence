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

package org.pentaho.pmi.engines;

import org.pentaho.di.core.Const;
import org.pentaho.pmi.SchemeUtils;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.classifiers.Classifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.SelectedTag;
import weka.core.Tag;
import weka.core.WekaException;
import weka.core.WekaPackageClassLoaderManager;
import weka.filters.supervised.attribute.Discretize;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of a python scikit-learn classification/regression scheme. Uses the ScikitLearnClassifier wrapper
 * classifier from the wekaPython Weka package.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class PythonClassifierScheme extends SupervisedScheme {

  /**
   * The underlying ScikitLearnClassifier
   */
  protected Classifier m_scheme;

  /**
   * The name of the learner in scikit-learn
   */
  protected String m_pythonLearner = "";

  /**
   * Class of the enumeration that lists scikit-learn schemes
   */
  protected Class<?> m_learnerEnumClazz;

  /**
   * Holds actual learner enum values
   */
  protected Object[] m_learnerEnumValues;

  /**
   * The instantiated enum for learners
   */
  protected Enum m_learnerEnumVal;

  /**
   * Tag values for learners
   */
  protected Tag[] m_tagsLearner;

  /**
   * Constructor
   *
   * @param schemeName the name of the scheme this PythonClassifierScheme should provide
   * @throws Exception if the scheme can't be handled/isn't supported
   */
  public PythonClassifierScheme( String schemeName ) throws Exception {
    super( schemeName );

    instantiatePythonClassifier( schemeName );
  }

  /**
   * Instantiates the wrapped ScikitLearnClassifier and configures it to use the specified scheme.
   *
   * @param schemeName the name of the scheme to instantiate
   * @throws Exception if a problem occurs
   */
  protected void instantiatePythonClassifier( String schemeName ) throws Exception {
    m_scheme =
        (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.sklearn.ScikitLearnClassifier" );

    // get the inner enum
    m_learnerEnumClazz =
        WekaPackageClassLoaderManager.forName( "weka.classifiers.sklearn.ScikitLearnClassifier$Learner" );
    m_learnerEnumValues = m_learnerEnumClazz.getEnumConstants();
    m_tagsLearner = new Tag[m_learnerEnumValues.length];
    for ( Object o : m_learnerEnumValues ) {
      m_tagsLearner[( (Enum) o ).ordinal()] = new Tag( ( (Enum) o ).ordinal(), o.toString() );
    }

    if ( schemeName.equalsIgnoreCase( "Logistic regression" ) ) {
      m_pythonLearner = "LogisticRegression";
    } else if ( schemeName.equalsIgnoreCase( "Naive Bayes" ) ) {
      m_pythonLearner = "BernoulliNB";
    } else if ( schemeName.equalsIgnoreCase( "Naive Bayes multinomial" ) ) {
      m_pythonLearner = "MultinomialNB";
    } else if ( schemeName.equalsIgnoreCase( "Decision tree classifier" ) ) {
      m_pythonLearner = "DecisionTreeClassifier";
    } else if ( schemeName.equalsIgnoreCase( "Decision tree regressor" ) ) {
      m_pythonLearner = "DecisionTreeRegressor";
    } else if ( schemeName.equalsIgnoreCase( "Linear regression" ) ) {
      m_pythonLearner = "LinearRegression";
    } else if ( schemeName.equalsIgnoreCase( "Support vector classifier" ) ) {
      m_pythonLearner = "SVC";
    } else if ( schemeName.equalsIgnoreCase( "Support vector regressor" ) ) {
      m_pythonLearner = "SVR";
    } else if ( schemeName.equalsIgnoreCase( "Random forest classifier" ) ) {
      m_pythonLearner = "RandomForestClassifier";
    } else if ( schemeName.equalsIgnoreCase( "Random forest regressor" ) ) {
      m_pythonLearner = "RandomForestRegressor";
    } else if ( schemeName.equalsIgnoreCase( "Gradient boosted trees" ) ) {
      m_pythonLearner = "GradientBoostingClassifier";
    } else {
      throw new UnsupportedSchemeException( "Classification/regression scheme '" + schemeName + "' is unsupported" );
    }

    // System.err.println( "Python learner: " + m_pythonLearner );
    int enumOrdinal = getEnumConstVal( m_pythonLearner );
    m_learnerEnumVal = (Enum) m_learnerEnumValues[enumOrdinal];
    // System.err.println( "Python learner enum: " + m_learnerEnumVal.toString() );
    setLearnerOnScheme( m_scheme, enumOrdinal );
  }

  protected void setLearnerOnScheme( Classifier scikitLearnClassifier, int enumOrdinal )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    SelectedTag tag = new SelectedTag( enumOrdinal, m_tagsLearner );
    Method m = scikitLearnClassifier.getClass().getDeclaredMethod( "setLearner", SelectedTag.class );

    m.invoke( scikitLearnClassifier, tag );
  }

  protected void setLearnerOptsOnScheme( Classifier scikitLearnClassifier, String learnerOpts )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method m = scikitLearnClassifier.getClass().getDeclaredMethod( "setLearnerOpts", String.class );

    m.invoke( scikitLearnClassifier, learnerOpts != null ? learnerOpts : "" );
  }

  protected String getLearnerOptsFromScheme( Classifier scikitLearnClassifier )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String result = "";

    Method m = scikitLearnClassifier.getClass().getDeclaredMethod( "getLearnerOpts" );
    result = (String) m.invoke( scikitLearnClassifier );

    return result;
  }

  protected String getDefaultParametersForLearner( Enum learnerEnumVal )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String result = "";

    Method m = learnerEnumVal.getClass().getDeclaredMethod( "getDefaultParameters" );
    result = (String) m.invoke( learnerEnumVal );

    return result;
  }

  protected int getEnumConstVal( String learnerConstString ) {

    int result = -1;
    for ( Object o : m_learnerEnumValues ) {
      if ( o.toString().equalsIgnoreCase( learnerConstString ) ) {
        result = ( (Enum) o ).ordinal();
        break;
      }
    }

    return result;
  }

  /**
   * Checks whether the scheme can handle the data that will be coming in
   *
   * @param data     the header of the data that will be used for training
   * @param messages a list to store messages describing any problems/warnings for the selected scheme with respect to the incoming data
   * @return true if the scheme can handle the data
   */
  @Override public boolean canHandleData( Instances data, List<String> messages ) {
    if ( data.checkForAttributeType( Attribute.RELATIONAL ) ) {
      messages.add( "Can't handle relational attribute type" );
      return false;
    }

    if ( m_pythonLearner.equals( "MultinomialNB" ) && SchemeUtils
        .checkForAttributeType( data, Attribute.NOMINAL, true ) ) {
      messages.add( "Scikit-learn multinomial naive bayes cannot handle categorical attributes" );
      return false;
    }

    try {
      Classifier finalClassifier = (Classifier) getConfiguredScheme( data );
      finalClassifier.getCapabilities().testWithFail( data );
    } catch ( Exception ex ) {
      messages.add( ex.getMessage() );
      return false;
    }

    addStringAttributeWarningMessageIfNeccessary( data, messages );
    return true;
  }

  /**
   * No python scikit-learn schemes support incremental (row by row) training
   *
   * @return false
   */
  @Override public boolean supportsIncrementalTraining() {
    return false; // no python methods can be trained incrementally
  }

  @Override public Map<String, Object> getSchemeInfo() throws Exception {
    Map<String, Object> schemeMap = new LinkedHashMap<>();
    schemeMap.put( "topLevelClass", "weka.classifiers.sklearn.ScikitLearnClassifier" );
    schemeMap.put( "topLevelSchemeObject", m_scheme );
    Map<String, Map<String, Object>> propertyList = new LinkedHashMap<>();
    schemeMap.put( "properties", propertyList );
    populatePropertiesFromScheme( propertyList );
    addDefaultsForSchemeIfNecessary( propertyList );

    return schemeMap;
  }

  protected void populatePropertiesFromScheme( Map<String, Map<String, Object>> propertyList ) throws WekaException {
    try {
      String learnerParams = getLearnerOptsFromScheme( m_scheme );
      if ( learnerParams != null && learnerParams.length() > 0 ) {
        String[] params = learnerParams.split( "," );
        for ( String param : params ) {
          String[] parts = param.split( "=" );
          if ( parts.length != 2 ) {
            continue;
          }
          String name = parts[0].trim();
          String value = parts[1].trim();
          Map<String, Object> propMap = new LinkedHashMap<>();
          propMap.put( "name", name );
          propMap.put( "label", name );
          propMap.put( "pythonProp", true );
          propMap.put( "type", "string" );
          propMap.put( "value", value );

          propertyList.put( name, propMap );
        }
      }
    } catch ( Exception ex ) {
      throw new WekaException( ex );
    }
  }

  protected void addDefaultsForSchemeIfNecessary( Map<String, Map<String, Object>> propertyList ) throws WekaException {
    try {
      String defaults = getDefaultParametersForLearner( m_learnerEnumVal );
      defaults = defaults.replace( "\t", "" ).replace( "\n", "" );
      String[] params = defaults.split( "," );
      for ( String param : params ) {
        String[] parts = param.split( "=" );
        if ( parts.length != 2 ) {
          continue;
        }
        String name = parts[0].trim();
        String value = parts[1].trim();
        if ( propertyList.containsKey( name ) ) {
          continue; // don't overwrite with a default value if already set!
        }
        Map<String, Object> propMap = new LinkedHashMap<>();
        propMap.put( "name", name );
        propMap.put( "label", name );
        propMap.put( "pythonProp", true );
        propMap.put( "type", "string" );
        if ( ( m_pythonLearner.equalsIgnoreCase( "SVC" ) || m_pythonLearner.equalsIgnoreCase( "SVR" ) ) && name
            .equalsIgnoreCase( "gamma" ) ) {
          // old versions of scikit learn use 0.0 (default) for gamma to indicate the automatic mode of setting this
          // based on the number of input attributes; later version use 'auto' (default) for this. We set the value
          // to blank here so that the default works in both cases
          propMap.put( "value", "" );
        } else {
          propMap.put( "value", value );
        }

        propertyList.put( name, propMap );
      }
    } catch ( Exception ex ) {
      throw new WekaException( ex );
    }
  }

  /**
   * Configure the underlying scheme using the supplied command-line option settings
   *
   * @param options an array of command-line option settings
   * @throws Exception if a problem occurs
   */
  @Override public void setSchemeOptions( String[] options ) throws Exception {
    if ( m_scheme != null ) {
      ( (OptionHandler) m_scheme ).setOptions( options );
    }
  }

  /**
   * Get the underlying scheme's command line option settings. This may be different from those
   * that could be obtained from scheme returned by {@code getConfiguredScheme()}, as the configured
   * scheme might be a wrapper (meta classifier) around the underlying scheme.
   *
   * @return the options of the underlying scheme
   */
  @Override public String[] getSchemeOptions() {
    if ( m_scheme != null ) {
      return ( (OptionHandler) m_scheme ).getOptions();
    }
    return null;
  }

  /**
   * Set underlying scheme parameters from a map of parameter values. Note that this will set only primitive parameter
   * types on the scheme. It does not process nested objects. This method is used primarily by the GUI editor dialogs. Use
   * setSchemeOptions() to handle all parameters (including those on nested complex objects).
   *
   * @param parameters a map of scheme parameters to set
   * @throws Exception if a problem occurs.
   */
  @Override public void setSchemeParameters( Map<String, Map<String, Object>> parameters ) throws Exception {
    setPythonLearnerOptions( parameters );

    // other options (if any)
    if ( parameters.size() > 0 ) {
      SchemeUtils.setSchemeParameters( m_scheme, parameters );
    }
  }

  /**
   * Return the underlying predictive scheme, configured and ready to use. The incoming training data
   * is supplied so that the scheme can decide (based on data characteristics) whether the underlying scheme
   * needs to be combined with data filters in order to be applicable to the data. E.g. The user might have selected
   * logistic regression which, in the given engine, can only support binary class problems. At execution time, the
   * incoming data could have more than two class labels, in which case the underlying scheme will need to be wrapped
   * in a MultiClassClassifier.
   *
   * @param incomingHeader the header of the incoming training data
   * @return the underlying predictive scheme
   * @throws Exception if there is a problem configuring the scheme
   */
  @Override public Object getConfiguredScheme( Instances incomingHeader ) throws Exception {
    Classifier finalScheme = adjustForSamplingAndPreprocessing( incomingHeader, m_scheme );
    // boolean stringToWVInPlay = checkForFilter( finalScheme, ".StringToWordVector" );
    boolean discretizeInPlay = checkForFilter( finalScheme, ".Discretize" );

    if ( m_pythonLearner.equals( "BernoulliNB" ) ) {
      boolean containsNumeric = SchemeUtils.checkForAttributeType( incomingHeader, Attribute.NUMERIC, true );
      boolean containsNominal = SchemeUtils.checkForAttributeType( incomingHeader, Attribute.NOMINAL, true );
      if ( containsNumeric && containsNominal ) {
        if ( !discretizeInPlay ) {
          // need to apply discretization
          FilteredClassifier temp = new FilteredClassifier();
          temp.setClassifier( m_scheme );
          temp.setFilter( new Discretize() );
          if ( finalScheme instanceof FilteredClassifier ) {
            ( (FilteredClassifier) finalScheme ).setClassifier( temp );
          } else {
            finalScheme = temp;
          }
        }
      } else if ( containsNumeric && !discretizeInPlay ) {
        // only numeric attributes - switch to GaussianNB
        Classifier
            adjustedScheme =
            (Classifier) WekaPackageClassLoaderManager
                .objectForName( "weka.classifiers.sklearn.ScikitLearnClassifier" );
        int enumOrdinal = getEnumConstVal( "GaussianNB" );
        setLearnerOnScheme( adjustedScheme, enumOrdinal );
        finalScheme = adjustForSamplingAndPreprocessing( incomingHeader, adjustedScheme );
      }
    } else if ( m_pythonLearner.equals( "GaussianNB" ) ) {
      if ( SchemeUtils.checkForAttributeType( incomingHeader, Attribute.NOMINAL, true ) ) {
        // have to switch to BernouliNB
        Classifier
            adjustedScheme =
            (Classifier) WekaPackageClassLoaderManager
                .objectForName( "weka.classifiers.sklearn.ScikitLearnClassifier" );
        int enumOrdinal = getEnumConstVal( "BernoulliNB" );
        setLearnerOnScheme( adjustedScheme, enumOrdinal );

        // params moving from Gaussian to Bernouli are OK
        String learnerOpts = getLearnerOptsFromScheme( m_scheme );
        setLearnerOptsOnScheme( adjustedScheme, learnerOpts );

        // changed the base learner, so need to do any necessary adjustments for sampling and preprocessing again
        finalScheme = adjustForSamplingAndPreprocessing( incomingHeader, adjustedScheme );
        discretizeInPlay = checkForFilter( finalScheme, ".Discretize" );

        // discretization needed now?
        if ( SchemeUtils.checkForAttributeType( incomingHeader, Attribute.NUMERIC, true ) && !discretizeInPlay ) {
          // Classifier temp = finalScheme;
          FilteredClassifier temp = new FilteredClassifier();
          temp.setFilter( new Discretize() );
          temp.setClassifier( adjustedScheme );
          if ( finalScheme instanceof FilteredClassifier ) {
            ( (FilteredClassifier) finalScheme ).setClassifier( temp );
          } else {
            finalScheme = temp;
          }
        }
      }
    }

    // TODO could do something for MultinomialNB. If no string atts then convert to one of the other
    // NBs (based on number of numeric vs nominal perhaps?).

    return finalScheme;
  }

  protected void setPythonLearnerOptions( Map<String, Map<String, Object>> parameters ) throws WekaException {
    StringBuilder b = new StringBuilder();
    List<String> keysToRemove = new ArrayList<>();
    for ( Map<String, Object> p : parameters.values() ) {
      if ( p.get( "pythonProp" ) != null && !Const.isEmpty( p.get( "value" ).toString() ) ) {
        b.append( p.get( "name" ) ).append( "=" ).append( p.get( "value" ) ).append( "," );
        keysToRemove.add( p.get( "name" ).toString() );
      }
    }

    b.setLength( b.length() - 1 );
    for ( String k : keysToRemove ) {
      parameters.remove( k );
    }
    try {
      setLearnerOptsOnScheme( m_scheme, b.toString() );
    } catch ( Exception ex ) {
      throw new WekaException( ex );
    }
  }
}
