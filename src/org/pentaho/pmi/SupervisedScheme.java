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

import weka.classifiers.Classifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.RemoveType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.pentaho.pmi.SchemeUtils.filterConfigsToList;

/**
 * Abstract base class for supervised learning schemes provided by underlying ML engines.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public abstract class SupervisedScheme extends Scheme {

  /**
   * Property name for specifying additional supervised schemes. This is a comma-separated list of scheme names, and
   * is added to s_pluginClassifierSchemeList
   */
  public static final String ADDITIONAL_SUPERVISED_SCHEMES_PROPERTY_KEY = "org.pentaho.pmi.supervised.schemes";

  /**
   * Environment variable name for specifying additional supervised schemes. This is a comma-separated list of scheme names, and
   * is added to s_pluginClassifierSchemeList
   */
  public static final String ADDITIONAL_SUPERVISED_SCHEMES_ENV_KEY = "ORG_PENTAHO_PMI_SUPERVISED_SCHEMES";

  /**
   * The current set of supervised classifiers and regressors supported
   */
  public static final List<String>
      s_defaultClassifierSchemeList =
      Arrays.asList( "Logistic regression", "Naive Bayes", "Naive Bayes multinomial", "Naive Bayes incremental",
          "Decision tree classifier", "Decision tree regressor", "Linear regression", "Support vector classifier",
          "Support vector regressor", "Random forest classifier", "Random forest regressor", "Gradient boosted trees" );

  /**
   * Additional scheme support from plugins. Scheme implementations from built-in engines would need to be modified to
   * support these; Engines (and hence Scheme implementations) from plugins might provide these
   */
  public static final List<String> s_pluginClassifierSchemeList = new ArrayList<>();

  /* Add any user-specified/plugin schemes */
  static {
    String schemesNames = System.getProperty( ADDITIONAL_SUPERVISED_SCHEMES_PROPERTY_KEY, "" );
    if ( schemesNames.length() == 0 ) {
      schemesNames = System.getenv( ADDITIONAL_SUPERVISED_SCHEMES_ENV_KEY );
    }
    if ( schemesNames != null && schemesNames.length() > 0 ) {
      String[] schemes = schemesNames.split( "," );
      for ( String s : schemes ) {
        s_pluginClassifierSchemeList.add( s.trim() );
      }
    }
  }

  /**
   * Constructor that sets the name of this scheme
   *
   * @param schemeName the name of this scheme
   */
  public SupervisedScheme( String schemeName ) {
    super( schemeName );
  }

  /**
   * Takes the currently configured classifier and returns an adjusted one (i.e. wrapped in a weka.classifiers.meta.FilteredClassifier)
   * that applies any sampling and preprocessing.
   *
   * @param incomingStructure the structure of the incoming data
   * @param scheme            the currently configured classifier
   * @return a classifier that has been adjusted, as necessary, to apply any sampling and preprocessing operations.
   * @throws Exception if a problem occurs
   */
  protected Classifier adjustForSamplingAndPreprocessing( Instances incomingStructure, Classifier scheme )
      throws Exception {
    Classifier result = scheme;

    // Schemes should call this method first from getConfiguredScheme().
    // getConfiguredScheme then might add additional filters (e.g. Discretize for BernouliNB in python).

    // sampling/class balancing first
    if ( m_samplingConfigs.size() > 0 ) {
      if ( !( scheme instanceof FilteredClassifier ) ) {
        result = new FilteredClassifier();
        ( (FilteredClassifier) result ).setClassifier( scheme );
        MultiFilter multiFilter = new MultiFilter();
        List<Filter> samplers = filterConfigsToList( m_samplingConfigs );
        multiFilter.setFilters( samplers.toArray( new Filter[samplers.size()] ) );
        ( (FilteredClassifier) result ).setFilter( multiFilter );
      } else {
        Filter existing = ( (FilteredClassifier) result ).getFilter();
        if ( !( existing instanceof MultiFilter ) ) {
          MultiFilter multiFilter = new MultiFilter();
          List<Filter> samplers = filterConfigsToList( m_samplingConfigs );
          samplers.add( existing );
          multiFilter.setFilters( samplers.toArray( new Filter[samplers.size()] ) );
          ( (FilteredClassifier) result ).setFilter( multiFilter );
        } else {
          MultiFilter multiFilter = (MultiFilter) existing;
          List<Filter> samplers = filterConfigsToList( m_samplingConfigs );
          samplers.addAll( Arrays.asList( multiFilter.getFilters() ) );
          multiFilter.setFilters( samplers.toArray( new Filter[samplers.size()] ) );
        }
      }
    }

    // now other preprocessing - STWV, discretize (others?)
    if ( m_preprocessingConfigs.size() > 0 ) {
      if ( !( result instanceof FilteredClassifier ) ) {
        result = new FilteredClassifier();
        ( (FilteredClassifier) result ).setClassifier( scheme );
        MultiFilter multiFilter = new MultiFilter();
        List<Filter> preprocessors = filterConfigsToList( m_preprocessingConfigs );
        multiFilter.setFilters( preprocessors.toArray( new Filter[preprocessors.size()] ) );
        ( (FilteredClassifier) result ).setFilter( multiFilter );
      } else {
        Filter existing = ( (FilteredClassifier) result ).getFilter();
        if ( !( existing instanceof MultiFilter ) ) {
          MultiFilter multiFilter = new MultiFilter();
          List<Filter> preprocessors = filterConfigsToList( m_preprocessingConfigs );
          preprocessors.add( existing );
          multiFilter.setFilters( preprocessors.toArray( new Filter[preprocessors.size()] ) );
          ( (FilteredClassifier) result ).setFilter( multiFilter );
        } else {
          MultiFilter multiFilter = (MultiFilter) existing;
          List<Filter> preprocessors = filterConfigsToList( m_preprocessingConfigs );
          preprocessors.addAll( Arrays.asList( multiFilter.getFilters() ) );
          multiFilter.setFilters( preprocessors.toArray( new Filter[preprocessors.size()] ) );
        }
      }
    }

    // Check to see if string attributes need to be dropped
    if ( SchemeUtils.checkForAttributeType( incomingStructure, Attribute.STRING, true ) ) {
      if ( !( result instanceof FilteredClassifier ) ) {
        result = new FilteredClassifier();
        ( (FilteredClassifier) result ).setClassifier( scheme );
        MultiFilter multiFilter = new MultiFilter();
        RemoveType removeType = new RemoveType();
        removeType.setAttributeType( new SelectedTag( Attribute.STRING, RemoveType.TAGS_ATTRIBUTETYPE ) );
        Filter[] filters = new Filter[1];
        filters[0] = removeType;
        multiFilter.setFilters( filters );
        ( (FilteredClassifier) result ).setFilter( multiFilter );
      } else {
        if ( !checkForFilter( result, ".StringToWordVector" ) ) {
          MultiFilter multiFilter = (MultiFilter) ( (FilteredClassifier) result ).getFilter();
          List<Filter> filters = new ArrayList<>();
          filters.addAll( Arrays.asList( multiFilter.getFilters() ) );
          RemoveType removeType = new RemoveType();
          removeType.setAttributeType( new SelectedTag( Attribute.STRING, RemoveType.TAGS_ATTRIBUTETYPE ) );
          filters.add( 0, removeType );
          multiFilter.setFilters( filters.toArray( new Filter[filters.size()] ) );
        }
      }
    }

    return result;
  }

  /**
   * Checks a given classifier to see if a named filter is already being used.
   *
   * @param classifier the classifier to check
   * @param filterName the name of the filter to check for
   * @return true if the named filter is already in play
   */
  protected static boolean checkForFilter( Classifier classifier, String filterName ) {
    if ( !( classifier instanceof FilteredClassifier ) ) {
      return false;
    }

    Filter filter = ( (FilteredClassifier) classifier ).getFilter();
    List<Filter> filters = new ArrayList<>();
    if ( !( filter instanceof MultiFilter ) ) {
      filters.add( filter );
    } else {
      filters.addAll( Arrays.asList( ( (MultiFilter) filter ).getFilters() ) );
    }

    for ( Filter f : filters ) {
      if ( f.getClass().getCanonicalName().endsWith( filterName ) ) {
        return true;
      }
    }
    return false;
  }
}
