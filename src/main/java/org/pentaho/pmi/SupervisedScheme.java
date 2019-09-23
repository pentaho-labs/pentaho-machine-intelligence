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
          "Support vector regressor", "Random forest classifier", "Random forest regressor", "Gradient boosted trees",
          "Multi-layer perceptron classifier", "Multi-layer perceptron regressor", "Deep learning network",
          "Extreme gradient boosting classifier", "Extreme gradient boosting regressor" );

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
   * Subclass should return true if the scheme can handle environment variables with respect to its options
   *
   * @return This default method returns false, as just about all classifiers/clusterers in Weka do not (yet) handle
   * environment variables
   */
  @Override public boolean supportsEnvironmentVariables() {
    return false;
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
    if ( !canHandleStringAttributes() && SchemeUtils
        .checkForAttributeType( incomingStructure, Attribute.STRING, true ) ) {
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
