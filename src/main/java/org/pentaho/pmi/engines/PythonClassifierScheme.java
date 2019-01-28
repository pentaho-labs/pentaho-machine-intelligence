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
    } else if ( schemeName.equalsIgnoreCase( "Multi-layer perceptron classifier" ) ) {
      m_pythonLearner = "MLPClassifier";
    } else if ( schemeName.equalsIgnoreCase( "Multi-layer perceptron regressor" ) ) {
      m_pythonLearner = "MLPRegressor";
    } else if ( schemeName.equalsIgnoreCase( "Extreme gradient boosting classifier" ) ) {
      m_pythonLearner = "XGBClassifier";
    } else if ( schemeName.equalsIgnoreCase( "Extreme gradient boosting regressor" ) ) {
      m_pythonLearner = "XGBRegressor";
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

  protected int getLearnerFromScheme( Classifier scikitLearnClassifier )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method m = scikitLearnClassifier.getClass().getDeclaredMethod( "getLearner" );

    Object result = m.invoke( scikitLearnClassifier );
    SelectedTag tag = (SelectedTag) result;

    return tag.getSelectedTag().getID();
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

  /**
   * scikit-learn schemes do not support resumable iterative training
   *
   * @return false
   */
  @Override public boolean supportsResumableTraining() {
    return false;
  }

  /**
   * Returns true if the configured scheme can directly handle string attributes
   *
   * @return true if the configured scheme can directly handle string attributes
   */
  @Override public boolean canHandleStringAttributes() {
    return false;
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
      learnerParams = parentheticOptionWithCommasHack( learnerParams );
      if ( learnerParams != null && learnerParams.length() > 0 ) {
        String[] params = learnerParams.split( "," );
        for ( String param : params ) {
          String[] parts = param.split( "=" );
          if ( parts.length != 2 ) {
            continue;
          }
          String name = parts[0].trim();
          String value = parts[1].trim();
          if ( value.contains( "(" ) && value.contains( ")" ) ) {
            value = value.replace( "*", "," );
          }
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

  protected String parentheticOptionWithCommasHack( String source ) {
    return source.replaceAll( ",(?=[^()]*\\))", "*" );
  }

  protected void addDefaultsForSchemeIfNecessary( Map<String, Map<String, Object>> propertyList ) throws WekaException {
    try {
      String defaults = getDefaultParametersForLearner( m_learnerEnumVal );
      defaults = defaults.replace( "\t", "" ).replace( "\n", "" );
      defaults = parentheticOptionWithCommasHack( defaults );
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
        if ( value.contains( "(" ) && value.contains( ")" ) ) {
          value = value.replace( "*", "," );
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

  public void setConfiguredScheme( Object scheme ) throws Exception {
    if ( scheme instanceof FilteredClassifier ) {
      scheme = ( (FilteredClassifier) scheme ).getClassifier();
    }
    String schemeClass = scheme.getClass().getCanonicalName();
    if ( !schemeClass.equals( "weka.classifiers.sklearn.ScikitLearnClassifier" ) ) {
      throw new Exception( "Supplied configured scheme is not of the correct type" );
    }

    int enumOrdinal = getEnumConstVal( m_pythonLearner );
    int configOrdinal = getLearnerFromScheme( (Classifier) scheme );

    if ( enumOrdinal != configOrdinal ) {
      throw new Exception(
          "Configured scheme type '" + m_learnerEnumValues[configOrdinal] + "' is not equal to " + m_pythonLearner );
    }

    // Just copy over option settings from the supplied scheme, so that we avoid consuming
    // memory for large trained models (model gets loaded again when transformation is executed)
    ( (OptionHandler) m_scheme ).setOptions( ( (OptionHandler) scheme ).getOptions() );
    // m_scheme = (Classifier) scheme;
    m_pythonLearner = m_learnerEnumValues[configOrdinal].toString();
  }
}
