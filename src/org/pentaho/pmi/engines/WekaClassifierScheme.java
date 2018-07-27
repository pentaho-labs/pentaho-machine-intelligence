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

import org.pentaho.pmi.SchemeUtils;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.classifiers.Classifier;
import weka.classifiers.UpdateableClassifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.classifiers.functions.LinearRegression;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.MultilayerPerceptron;
import weka.classifiers.functions.SGD;
import weka.classifiers.meta.LogitBoost;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.M5P;
import weka.classifiers.trees.REPTree;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.SelectedTag;
import weka.core.Tag;
import weka.core.Utils;
import weka.core.WekaPackageClassLoaderManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of a native Weka classification scheme
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class WekaClassifierScheme extends SupervisedScheme {

  /**
   * SVM type C-SVC (classification).
   */
  public static final int SVMTYPE_C_SVC = 0;

  /**
   * SVM type nu-SVC (classification).
   */
  public static final int SVMTYPE_NU_SVC = 1;

  /**
   * SVM type one-class SVM (classification).
   */
  public static final int SVMTYPE_ONE_CLASS_SVM = 2;

  /**
   * SVM type epsilon-SVR (regression).
   */
  public static final int SVMTYPE_EPSILON_SVR = 3;

  /**
   * SVM type nu-SVR (regression).
   */
  public static final int SVMTYPE_NU_SVR = 4;

  /**
   * SVM types for underlying libsvm implementation of support vector machines
   */
  public static final Tag[]
      TAGS_SVMTYPE =
      { new Tag( SVMTYPE_C_SVC, "C-SVC (classification)" ), new Tag( SVMTYPE_NU_SVC, "nu-SVC (classification)" ),
          new Tag( SVMTYPE_ONE_CLASS_SVM, "one-class SVM (classification)" ),
          new Tag( SVMTYPE_EPSILON_SVR, "epsilon-SVR (regression)" ),
          new Tag( SVMTYPE_NU_SVR, "nu-SVR (regression)" ) };

  /**
   * The actual scheme to use
   */
  protected Classifier m_underlyingScheme;

  /**
   * Constructor for a WekaClassifierScheme
   *
   * @param schemeName the name of the learning algorithm to be encapsulated in this WekaClassifierScheme
   * @throws UnsupportedSchemeException if the named scheme is not supported in Weka
   */
  public WekaClassifierScheme( String schemeName ) throws UnsupportedSchemeException {
    super( schemeName );

    if ( schemeName.equalsIgnoreCase( "Logistic regression" ) ) {
      m_underlyingScheme = new Logistic();
    } else if ( schemeName.equalsIgnoreCase( "Naive Bayes" ) ) {
      m_underlyingScheme = new NaiveBayes();
    } else if ( schemeName.equalsIgnoreCase( "Naive Bayes multinomial" ) ) {
      m_underlyingScheme = new NaiveBayesMultinomial();
    } else if ( schemeName.equalsIgnoreCase( "Naive Bayes incremental" ) ) {
      m_underlyingScheme = new NaiveBayesUpdateable();
    } else if ( schemeName.equalsIgnoreCase( "Decision tree classifier" ) ) {
      m_underlyingScheme = new J48();
    } else if ( schemeName.equalsIgnoreCase( "Decision tree regressor" ) ) {
      m_underlyingScheme = new M5P();
    } else if ( schemeName.equalsIgnoreCase( "Linear regression" ) ) {
      // TODO could use libLINEAR?
      m_underlyingScheme = new LinearRegression();
    } else if ( schemeName.equalsIgnoreCase( "Support vector classifier" ) ) {
      // need LibSVM (no SMO in OEM Weka)!
      try {
        m_underlyingScheme =
            (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.functions.LibSVM" );

        // no need to set svm type - default is C-SVC for classification
      } catch ( Exception e ) {
        throw new UnsupportedSchemeException( e );
      }
    } else if ( schemeName.equalsIgnoreCase( "Support vector regressor" ) ) {
      // need LibSVM!
      try {
        m_underlyingScheme =
            (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.functions.LibSVM" );
        setSVMTypeForLibSVM( SVMTYPE_EPSILON_SVR ); // switch to support vector for regression
      } catch ( Exception e ) {
        throw new UnsupportedSchemeException( e );
      }
    } else if ( schemeName.toLowerCase().startsWith( "random forest" ) ) {
      m_underlyingScheme = new RandomForest();
      ( (RandomForest) m_underlyingScheme ).setNumExecutionSlots( Runtime.getRuntime().availableProcessors() );
    } else if ( schemeName.equalsIgnoreCase( "Gradient boosted trees" ) ) {
      m_underlyingScheme = new LogitBoost();
      REPTree tree = new REPTree();
      tree.setMaxDepth( 5 );
      tree.setNoPruning( true );
      tree.setInitialCount( 1 );
      ( (LogitBoost) m_underlyingScheme ).setClassifier( tree );
      ( (LogitBoost) m_underlyingScheme ).setWeightThreshold( 95 );
    } else if ( schemeName.equalsIgnoreCase( "Incremental logistic regression (sgd)" ) ) {
      m_underlyingScheme = new SGD();
      ( (SGD) m_underlyingScheme ).setLossFunction( new SelectedTag( SGD.LOGLOSS, SGD.TAGS_SELECTION ) );
    } else if ( schemeName.equalsIgnoreCase( "Incremental SVM (sgd)" ) ) {
      m_underlyingScheme = new SGD();
      ( (SGD) m_underlyingScheme ).setLossFunction( new SelectedTag( SGD.HINGE, SGD.TAGS_SELECTION ) );
      // user can adjust loss function to epsilon insensitive for SVM regression
    } else if ( schemeName.equalsIgnoreCase( "Incremental Naive Bayes" ) ) {
      m_underlyingScheme = new NaiveBayes();
    } else if ( schemeName.equalsIgnoreCase( "Multi-layer perceptron classifier" ) || schemeName
        .equalsIgnoreCase( "Multi-layer perceptron regressor" ) ) {
      m_underlyingScheme = new MultilayerPerceptron();
    } /* else if ( schemeName.equalsIgnoreCase( "Deep learning network" ) ) {
      try {
        m_underlyingScheme =
            (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.functions.Dl4jMlpClassifier" );
      } catch ( Exception e ) {
        throw new UnsupportedSchemeException( e );
      }
    } */ else {
      throw new UnsupportedSchemeException(
          "Classification/regression scheme '" + schemeName + "' is unsupported in Weka" );
    }
  }

  /**
   * Uses reflection to set the type of SVM on the Weka LibSVM wrapper
   *
   * @param svmTagInt the type of SVM
   * @throws Exception is a problem occurs
   */
  protected void setSVMTypeForLibSVM( int svmTagInt ) throws Exception {
    /* SelectedTag tag = new SelectedTag( svmTagInt, TAGS_SVMTYPE );
    Method m = m_underlyingScheme.getClass().getDeclaredMethod( "setSVMType", SelectedTag.class );

    m.invoke( m_underlyingScheme, tag ); */

    String[] opts = { "-S", "" + svmTagInt };
    ( (OptionHandler) m_underlyingScheme ).setOptions( opts );
  }

  /**
   * Returns true if this underlying learning algorithm can handle the incoming data
   *
   * @param data     the header of the data that will be used for training
   * @param messages a list to store messages describing any problems/warnings for the selected scheme with respect to the incoming data
   * @return
   */
  @Override public boolean canHandleData( Instances data, List<String> messages ) {
    // can basically handle all Weka types (given standard list of schemes) except relational data. String data could
    // be handled by SGDText/NBMText, or could wrap in FilteredClassifier with STWV.

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
   * Returns true if the underlying WEKA classifier/regressor supports incremental training
   *
   * @return return true if the underlying WEKA scheme supports incremental training
   */
  @Override public boolean supportsIncrementalTraining() {
    return m_underlyingScheme instanceof UpdateableClassifier;
  }

  /**
   * Returns true if the configured scheme can directly handle string attributes
   *
   * @return true if the configured scheme can directly handle string attributes
   */
  @Override public boolean canHandleStringAttributes() {
    if ( getSchemeName().equalsIgnoreCase( "Deep learning network" ) ) {

      // TODO really need to make a reflective call to see if the instance iterator is an
      // ImageInstanceIterator or a text-related iterator. This is the only time that string attributes
      // can be handled directly
      return true;
    }
    return false;
  }

  @Override public Map<String, Object> getSchemeInfo() throws Exception {
    return SchemeUtils.getSchemeParameters( m_underlyingScheme );
  }

  /**
   * Sets command-line options on the underlying scheme
   *
   * @param options an array of command-line option settings
   * @throws Exception if a problem occurs
   */
  @Override public void setSchemeOptions( String[] options ) throws Exception {
    if ( m_underlyingScheme != null ) {
      ( (OptionHandler) m_underlyingScheme ).setOptions( options );
    }
  }

  /**
   * Gets command-line options for the underlying scheme
   *
   * @return command line options
   */
  @Override public String[] getSchemeOptions() {
    if ( m_underlyingScheme != null ) {
      return ( (OptionHandler) m_underlyingScheme ).getOptions();
    }
    return null;
  }

  /**
   * Sets scheme options from a map of parameter/values
   *
   * @param parameters the options to set on the underlying scheme
   * @throws Exception if a problem occurs
   */
  public void setSchemeParameters( Map<String, Map<String, Object>> parameters ) throws Exception {
    SchemeUtils.setSchemeParameters( m_underlyingScheme, parameters );
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
    // TODO if SGD-based methods are selected will need to be wrapped in MultiClassClassifierIncremental

    if ( m_underlyingScheme instanceof MultilayerPerceptron ) {
      ( (MultilayerPerceptron) m_underlyingScheme ).setGUI( false ); // make sure GUI is not turned on!
    }
    return adjustForSamplingAndPreprocessing( incomingHeader, m_underlyingScheme );
  }
}
