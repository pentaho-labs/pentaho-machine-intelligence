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
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.classifiers.Classifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.SelectedTag;
import weka.core.Tag;
import weka.core.Utils;
import weka.core.WekaException;
import weka.core.WekaPackageClassLoaderManager;
import weka.filters.Filter;
import weka.filters.MultiFilter;
import weka.filters.unsupervised.attribute.NominalToBinary;
import weka.filters.unsupervised.attribute.RemoveUseless;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements classification and regression schemes based on the MLR R package. Uses the MLRClassifier wrapper classifier
 * from the RPlugin Weka package.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class RClassifierScheme extends SupervisedScheme {

  /**
   * A list of schemes that are not supported by R's MLR package
   */
  protected static List<String> s_excludedSchemes = Arrays.asList( "Naive Bayes incremental" );

  protected static final String R_CLASSIF_LIBLINEARL1LOGREG = "classif.LiblineaRL1LogReg"; // L1 regularized
  protected static final String R_CLASSIF_LIBLINEARL2LOGREG = "classif.LiblineaRL2LogReg"; // L2 regularized
  protected static final String R_CLASSIF_NAIVE_BAYES = "classif.naiveBayes";
  protected static final String R_CLASSIF_RPART = "classif.rpart";
  protected static final String R_REGR_RPART = "regr.rpart";
  protected static final String R_REGR_LM = "regr.lm";
  protected static final String R_CLASSIF_SVM = "classif.svm";
  protected static final String R_REGR_SVM = "regr.svm";
  protected static final String R_CLASSIF_RANDOM_FOREST = "classif.randomForest";
  protected static final String R_REGR_RANDOM_FOREST = "regr.randomForest";
  protected static final String R_CLASSIF_GBM = "classif.gbm";
  protected static final String R_CLASSIF_NNET = "classif.nnet";
  protected static final String R_REGR_NNET = "classif.nnet";

  protected static Tag[] TAGS_LEARNER;

  /**
   * The wrapper for the underlying R MLR scheme
   */
  protected Classifier m_scheme;

  protected Tag m_mlrLearner;

  /**
   * Constructor
   *
   * @param schemeName the name of the underlying scheme
   * @throws Exception if a problem occurs
   */
  public RClassifierScheme( String schemeName ) throws Exception {
    super( schemeName );

    instantiateMLRClassifier( schemeName );
  }

  /**
   * Instantiates the MLRClassifier and configures it to use the named scheme
   *
   * @param schemeName the name of the underlying scheme
   * @throws Exception if a problem occurs
   */
  protected void instantiateMLRClassifier( String schemeName ) throws Exception {
    m_scheme = (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.mlr.MLRClassifier" );
    // setLogMessagesFromR( m_scheme, true );

    // get the tags array
    Class<?> learnerClazz = m_scheme.getClass();
    Field tagsField = learnerClazz.getDeclaredField( "TAGS_LEARNER" );
    TAGS_LEARNER = (Tag[]) tagsField.get( null );

    if ( schemeName.equals( "Logistic regression" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_LIBLINEARL1LOGREG );
    } else if ( schemeName.equalsIgnoreCase( "Naive Bayes" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_NAIVE_BAYES );
    } else if ( schemeName.equalsIgnoreCase( "Naive Bayes multinomial" ) ) {
      throw new UnsupportedSchemeException(
          "The R engine (MLR) does not support the naive Bayes multinomial classifier" );
    } else if ( schemeName.equalsIgnoreCase( "Decision tree classifier" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_RPART );
    } else if ( schemeName.equalsIgnoreCase( "Decision tree regressor" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_REGR_RPART );
    } else if ( schemeName.equalsIgnoreCase( "Linear regression" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_REGR_LM );
    } else if ( schemeName.equalsIgnoreCase( "Support vector classifier" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_SVM );
    } else if ( schemeName.equalsIgnoreCase( "Support vector regressor" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_REGR_SVM );
    } else if ( schemeName.equalsIgnoreCase( "Random forest classifier" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_RANDOM_FOREST );
    } else if ( schemeName.equalsIgnoreCase( "Random forest regressor" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_REGR_RANDOM_FOREST );
    } else if ( schemeName.equalsIgnoreCase( "Gradient boosted trees" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_GBM );
    } else if ( schemeName.equalsIgnoreCase( "Multi-layer perceptron classifier" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_NNET );
    } else if ( schemeName.equalsIgnoreCase( "Multi-layer perceptron regressor" ) ) {
      m_mlrLearner = findApplicableTagForScheme( R_REGR_NNET );
    } else {
      throw new UnsupportedSchemeException( "Classification/regression scheme '" + schemeName + "' is unsupported" );
    }

    setLearnerOnScheme( m_scheme, m_mlrLearner.getID() );

    // now call getCapabilities() to force any R package installs required.
    // ( (CapabilitiesHandler) m_scheme ).getCapabilities();
  }

  protected Tag findApplicableTagForScheme( String readableRPackage ) throws WekaException {
    Tag result = null;

    for ( Tag t : TAGS_LEARNER ) {
      if ( t.getReadable().equalsIgnoreCase( readableRPackage ) ) {
        result = t;
        break;
      }
    }

    if ( result == null ) {
      throw new WekaException( "Unable to find appropriate MLR learner tag for " + readableRPackage );
    }

    return result;
  }

  protected void setLearnerOnScheme( Classifier mlrClassifier, int schemeConst )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    SelectedTag tag = new SelectedTag( schemeConst, TAGS_LEARNER );

    Method m = mlrClassifier.getClass().getDeclaredMethod( "setRLearner", SelectedTag.class );
    m.invoke( mlrClassifier, tag );
  }

  protected void setLearnerOptsOnScheme( Classifier mlrClassifier, String learnerOpts )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method m = mlrClassifier.getClass().getDeclaredMethod( "setLearnerParams", String.class );

    m.invoke( mlrClassifier, learnerOpts != null ? learnerOpts : "" );
  }

  protected void setLogMessagesFromR( Classifier mlrClassifier, boolean log )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method m = mlrClassifier.getClass().getDeclaredMethod( "setLogMessagesFromR", boolean.class );
    m.invoke( mlrClassifier, log );
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

    // TODO - checks for the different R classifiers re capabilities

    addStringAttributeWarningMessageIfNeccessary( data, messages );
    return true;
  }

  /**
   * No MLR R schemes support incremental (row by row) training
   *
   * @return false
   */
  @Override public boolean supportsIncrementalTraining() {
    return false; // incremental training is not supported
  }

  /**
   * MLR schemes do not support resumable iterative training
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

  /**
   * Get a map of meta information about the scheme and its parameters, useful for building editor dialogs
   *
   * @return a map of meta information about the scheme and its parameters
   * @throws Exception if a problem occurs
   */
  @Override public Map<String, Object> getSchemeInfo() throws Exception {
    Map<String, Object> schemeMap = new LinkedHashMap<>();
    schemeMap.put( "topLevelClass", "weka.classifiers.mlr.MLRClassifier" );
    schemeMap.put( "topLevelSchemeObject", m_scheme );
    Map<String, Map<String, Object>> propertyList = new LinkedHashMap<>();
    populatePropertiesForScheme( propertyList );
    schemeMap.put( "properties", propertyList );

    return schemeMap;
  }

  protected void populatePropertiesForScheme( Map<String, Map<String, Object>> propertyList ) throws WekaException {
    try {
      String learnerParams = getLearnerOptsFromScheme( m_scheme );
      String[] paramParts = new String[0];
      if ( learnerParams != null && learnerParams.length() > 0 ) {
        paramParts = learnerParams.split( "," );
      }
      if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL1LOGREG ) || m_mlrLearner.getReadable()
          .equalsIgnoreCase( R_CLASSIF_LIBLINEARL2LOGREG ) ) {
        Map<String, Object> propMap = new LinkedHashMap<>();
        propMap.put( "name", "Use L2 regularization" );
        propMap.put( "label", "Use L2 regularization" );
        propMap.put( "tip-text", "Use L2 regularization instead of L1" );
        propMap.put( "rProp", true );
        propMap.put( "meta-prop", true );
        propMap.put( "type", "boolean" );
        propMap.put( "value", !m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL1LOGREG ) );

        propertyList.put( "Use L2 regularization", propMap );
      } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_NAIVE_BAYES ) ) {
        Map<String, Object> propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Laplace", "Laplace", "Laplace adjustment to use " + "(default = 0)",
            true, false, "string", null, learnerParams, "laplace", "0" );
        propMap.put( "name", "Laplace" );
        propMap.put( "label", "Laplace" );
        propMap.put( "tip-text", "Laplace adjustment to use (default = 0)" );
        propMap.put( "rProp", true );
        propMap.put( "meta-prop", false );
        propMap.put( "type", "string" );

        String laplaceValue = getLearnerParamValueSimple( paramParts, "laplace" );
        propMap.put( "value", laplaceValue != null ? laplaceValue : "0" );
        propertyList.put( "Laplace", propMap );
      } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_RPART ) || m_mlrLearner.getReadable()
          .equalsIgnoreCase( R_REGR_RPART ) ) {
        Map<String, Object> propMap = new LinkedHashMap<>();
        if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_RPART ) ) {
          propMap.put( "name", "Prior" );
          propMap.put( "label", "Prior" );
          propMap.put( "tip-text", "Prior class probabilities (comma separated; must sum to 1)" );
          propMap.put( "rProp", true );
          propMap.put( "meta-prop", false );
          propMap.put( "type", "string" );

          // get any existing param value for Prior by parsing the complex parms=list(...
          String existingVal = getLearnerParamValueComplex( learnerParams, "Prior" );
          if ( existingVal != null ) {
            existingVal = existingVal.substring( existingVal.indexOf( "c(" ) );
            existingVal = existingVal.replace( "c(", "" );
            existingVal = existingVal.substring( 0, existingVal.indexOf( ")" ) );
            existingVal = existingVal.trim();
          }
          propMap.put( "value", existingVal != null ? existingVal : "" );
          propertyList.put( "Prior", propMap );

          propMap = new LinkedHashMap<>();
          propMap.put( "name", "Splitting criteria" );
          propMap.put( "label", "Splitting criteria" );
          propMap.put( "tip-text", "Node splitting criteria" );
          propMap.put( "rProp", true );
          propMap.put( "meta-prop", false );
          propMap.put( "type", "pick-list" );
          propMap.put( "pick-list-values", "gini,information" );

          // get any existing param value for Prior by parsing the complex parms=list(...
          existingVal = getLearnerParamValueComplex( learnerParams, "split" );
          if ( existingVal != null ) {
            String[] parts = existingVal.split( "=" );
            if ( parts.length >= 2 ) {
              existingVal = parts[1];
              existingVal = existingVal.replace( "\"", "" );
              existingVal = existingVal.replace( ")", "" );
              if ( existingVal.indexOf( "," ) > 0 ) {
                existingVal = existingVal.substring( 0, existingVal.indexOf( ',' ) );
              }
              existingVal = existingVal.trim();
            }
          }
          propMap.put( "value", existingVal != null ? existingVal : "gini" );
          propertyList.put( "Splitting criteria", propMap );
        }

        // rpart.control stuff (doesn't have to be assembled into a control object - can
        // be passed directly as arguments to rpart...
        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Min split", "Min split",
            "The minimum number of observations that must exist in a node in order for a split to be attempted.", true,
            false, "string", null, learnerParams, "minsplit", "" );
        propertyList.put( "Min split", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Min bucket", "Min bucket",
            "The minimum number of observations in any terminal <leaf> node. If only one of "
                + "minbucket or minsplit is specified, the code either sets minsplit to minbucket*3 or minbucket to "
                + "minsplit/3, as appropriate.", true, false, "string", null, learnerParams, "minbucket", "" );
        propertyList.put( "Min bucket", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Complexity parameter", "Complexity parameter",
            "Complexity parameter. Any split that does not decrease the overall lack of fit by a factor of cp "
                + "is not attempted.", true, false, "string", null, learnerParams, "cp", "" );
        propertyList.put( "Complexity parameter", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Max compete", "Max compete",
            "The number of competitor splits retained in the output. It is useful to know not "
                + "just which split was chosen, but which variable came in second, third, etc.", true, false, "string",
            null, learnerParams, "maxcompete", "" );
        propertyList.put( "Max compete", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Max surrogate", "Max surrogate",
            "The number of surrogate splits retained in the output. If this is set to zero the "
                + "compute time will be reduced, since approximately half of the computational time (other than setup) is "
                + "used in the search for surrogate splits.", true, false, "string", null, learnerParams,
            "maxsurrogate", "" );
        propertyList.put( "Max surrogate", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Use surrogate", "Use surrogate",
            "How to use surrogates in the splitting process. 0 means display only; an observation "
                + "with a missing value for the primary split rule is not sent further down the tree. 1 means use "
                + "surrogates, in order, to split subjects missing the primary variable; if all surrogates are missing the "
                + "observation is not split. For value 2 ,if all surrogates are missing, then send the observation in the "
                + "majority direction. A value of 0 corresponds to the action of tree, and 2 to the recommendations of "
                + "Breiman et.al (1984).", true, false, "pick-list", "0,1,2", learnerParams, "usesurrogate", "" );
        propertyList.put( "Use surrogate", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Num cross-validations", "Num cross-validations",
            "Number of cross-validations to perform", true, false, "string", null, learnerParams, "xval", "" );
        propertyList.put( "Num cross-validations", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Surrogate style", "Surrogate style",
            "Controls the selection of a best surrogate. If set to 0 (default) the program uses the total "
                + "number of correct classification for a potential surrogate variable, if set to 1 it uses the "
                + "percent correct, calculated over the non-missing values of the surrogate. The first option more "
                + "severely penalizes covariates with a large number of missing values.", true, false, "pick-list",
            "0,1", learnerParams, "surrogatestyle", "" );
        propertyList.put( "Surrogate style", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Max depth", "Max depth",
            "Set the maximum depth of any node of the final tree, with the root node counted as depth 0. "
                + "Values greater than 30 rpart will give nonsense results on 32-bit machines.", true, false, "string",
            null, learnerParams, "maxdepth", "" );
        propertyList.put( "Max depth", propMap );

      } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_REGR_LM ) ) {
        // no parameters for lm!
      } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_SVM ) || m_mlrLearner.getReadable()
          .equalsIgnoreCase( R_REGR_SVM ) ) {
        Map<String, Object> propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Type", "Type", "Type of SVM (default = C-classification", true, false,
            "pick-list", m_mlrLearner.getReadable().equals( R_CLASSIF_SVM ) ? "C-classification,nu-classification" :
                "eps-regression,nu-regression", learnerParams, "type",
            m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_SVM ) ? "C-classification" : "eps-regression" );
        propertyList.put( "Type", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Kernel", "Kernel",
            "The kernel used in training and predicting (default = radial)", true, false, "pick-list",
            "linear,polynomial,radial,sigmoid", learnerParams, "kernel", "radial" );
        propertyList.put( "Kernel", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Degree", "Degree",
            "Parameter needed for kernel type polynomial (default=3)", true, false, "string", null, learnerParams,
            "degree", "3" );
        propertyList.put( "Degree", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Gamma", "Gamma",
            "Parameter needed for all kernels except linear (default=1/(data dimension))", true, false, "string", null,
            learnerParams, "gamma", "" );
        propertyList.put( "Gamma", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Coef0", "Coef0",
            "parameter needed for kernels of type polynomial and sigmoid (default 0)", true, false, "string", null,
            learnerParams, "coef0", "0" );
        propertyList.put( "Coef0", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "C", "C",
            "Cost of constraints violation (default: 1)---it is the 'C' constant of the regularization term "
                + "in the Lagrange formulation", true, false, "string", null, learnerParams, "cost", "1" );
        propertyList.put( "C", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Nu", "Nu", "Parameter needed for nu-classification", true, false,
            "string", null, learnerParams, "nu", "" );
        propertyList.put( "Nu", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Cache size", "Cache size", "Cache memory in MB (default=40)", true,
            false, "string", null, learnerParams, "cachesize", "40" );
        propertyList.put( "Cahce size", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Tolerance", "Tolerance",
            "Tolerance of terminaltion criterion (default = 0.001)", true, false, "string", null, learnerParams,
            "tolerance", "0.001" );
        propertyList.put( "Tolearance", propMap );

        if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_REGR_SVM ) ) {
          propMap = new LinkedHashMap<>();
          addIndividualRLearnerPropToMap( propMap, "Epsilon", "Epsilon",
              "Epsilon in the insensitive-loss function (default=0.1)", true, false, "string", null, learnerParams,
              "epsilon", "0.1" );
          propertyList.put( "Epsilon", propMap );
        }

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Shrinking", "Shrinking",
            "Option whether to use the shrinking hueristics (default=true)", true, false, "boolean", null,
            learnerParams, "shrinking", true );
        propertyList.put( "Shrinking", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Cross-validation", "Cross-validation",
            "If a integer value k>0 is specified, a k-fold cross validation on the training data is performed "
                + "to assess the quality of the model: the accuracy rate for classification and the Mean Squared Error "
                + "for regression", true, false, "string", null, learnerParams, "cross", "0" );
        propertyList.put( "Cross-validation", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Fitted", "Fitted",
            "Logical indicating whether the fitted values should be computed and included in the model or "
                + "not (default = TRUE)", true, false, "boolean", null, learnerParams, "fitted", true );
        propertyList.put( "Fitted", propMap );
      } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_RANDOM_FOREST ) || m_mlrLearner.getReadable()
          .equalsIgnoreCase( R_REGR_RANDOM_FOREST ) ) {
        Map<String, Object> propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Number of trees", "Number of trees",
            "Number of trees to grow. his should not be set to too small a number, to ensure that every input row gets predicted at least a few times.",
            true, false, "string", null, learnerParams, "ntree", "500" );
        propertyList.put( "Number of trees", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Number of variables", "Number of variables",
            "Number of variables randomly sampled as candidates at each split. Note that the default values "
                + "are different for classification (sqrt(p) where p is number of variables in mlr_data (input data "
                + "frame)) and regression (p/3)", true, false, "string", null, learnerParams, "mtry",
            //"floor(sqrt(ncol(mlr_data)))" );
            "" );
        propertyList.put( "Number of variables", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Sampling with replacement", "Sampling with replacement",
            "Should sampling of cases be done with or without replacement?", true, false, "boolean", null,
            learnerParams, "replace", true );
        propertyList.put( "Sampling with replacement", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Sample size", "Sample size", "Size of sample to draw.", true, false,
            "string", null, learnerParams, "sampsize",
            // "nrow(mlr_data)" );
            "" );
        propertyList.put( "Sample size", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Node size", "Node size",
            "Minimum size of terminal nodes. Setting this to a larger number causes smaller trees to be grown (and thus take less time).",
            true, false, "string", null, learnerParams, "nodesize",
            m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_RANDOM_FOREST ) ? "1" : "5" );
        propertyList.put( "Node size", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Max nodes", "Max nodes",
            "Maximum number of terminal nodes trees in the forest can have. If not given, trees are grown to the"
                + " maximum possible (subject to limits by Node size). If set larger than maximum possible, a warning is"
                + " issued.", true, false, "string", null, learnerParams, "maxnodes", "" );
        propertyList.put( "Max nodes", propMap );
      } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_GBM ) ) {
        Map<String, Object> propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Number of trees", "Number of trees",
            "The total number of trees to fit. This is equivalent to the number of iterations.", true, false, "string",
            null, learnerParams, "n.trees", "100" );
        propertyList.put( "Number of trees", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Max depth", "Max depth",
            "The maximum depth of the trees (i.e. maximum depth of variable interactions. 1 implies an additive "
                + "model, 2 implies a model with up to 2-way interactions, etc.).", true, false, "string", null,
            learnerParams, "interaction.depth", "3" );
        propertyList.put( "Max depth", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Max num observations", "Max num observations",
            "Minimum number of observations in the trees terminal nodes.", true, false, "string", null, learnerParams,
            "n.minobsinnode", "" );
        propertyList.put( "Max num observations", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Shrinkage", "Shrinkage",
            "A shrinkage parameter applied to each tree in the expansion. Also known as the learning rate or "
                + "step-size reduction.", true, false, "string", null, learnerParams, "shrinkage", "" );
        propertyList.put( "Shrinkage", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Bag fraction", "Bag fraction",
            "The fraction of the training set observations randomly selected to propose the next tree in the "
                + "expansion. This introduces randomnesses into the model fit.", true, false, "string", null,
            learnerParams, "bag.fraction", "1" );
        propertyList.put( "Bag fraction", propMap );
      } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_NNET ) || m_mlrLearner.getReadable()
          .equalsIgnoreCase( R_REGR_NNET ) ) {
        Map<String, Object> propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Number of units in the hidden layer",
            "Number of units in the hidden layer", "Number of units in the hidden layer", true, false, "string", null,
            learnerParams, "size", "3" );
        propertyList.put( "Number of units in the hidden layer", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Add skip-layer connections", "Add skip-layer connections",
            "Add skip-layer connections from input to output", true, false, "boolean", null, learnerParams, "skip",
            false );
        propertyList.put( "Add skip-layer connections", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Decay", "Decay", "Parameter for weight decay", true, false, "string",
            null, learnerParams, "decay", "0" );
        propertyList.put( "Decay", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Maximum iterations", "Maximum iterations",
            "Maximum number of iterations", true, false, "string", null, learnerParams, "maxit", "100" );
        propertyList.put( "Maximum iterations", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Abstol", "Abstol",
            "Stop if the fit criterion falls below abstol, indicating an essentially perfect fit", true, false,
            "string", null, learnerParams, "abstol", "1.0e-4" );
        propertyList.put( "Abstol", propMap );

        propMap = new LinkedHashMap<>();
        addIndividualRLearnerPropToMap( propMap, "Reltol", "Reltol",
            "Stop if the optimizer is unable to reduce the fit criternion by a factor of at " + "least 1 - reltol",
            true, false, "string", null, learnerParams, "reltol", "1.0e-8" );
        propertyList.put( "Reltol", propMap );
      }
    } catch ( Exception ex ) {
      throw new WekaException( ex );
    }
  }

  protected void setRLearnerOptions( Map<String, Map<String, Object>> parameters ) throws WekaException {
    StringBuilder b = new StringBuilder();

    if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL1LOGREG ) || m_mlrLearner.getReadable()
        .equalsIgnoreCase( R_CLASSIF_LIBLINEARL2LOGREG ) ) {
      Map<String, Object> regularizationProp = parameters.get( "Use L2 regularization" );
      if ( regularizationProp != null ) {
        Boolean value = regularizationProp.get( "value" ).toString().equalsIgnoreCase( "true" );

        if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL1LOGREG ) && value != null && value ) {
          // switch to L2 version
          m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_LIBLINEARL2LOGREG );
        } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL2LOGREG ) && ( value == null
            || !value ) ) {
          // switch to L1 version
          m_mlrLearner = findApplicableTagForScheme( R_CLASSIF_LIBLINEARL1LOGREG );
        }
      }
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_NAIVE_BAYES ) ) {
      assembleRLearnerOptionsSimpleList( parameters, b, new ArrayList<String>() );
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_RPART ) ) {
      assembleRLearnerOptionsRPart( parameters, b, false );
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_REGR_RPART ) ) {
      assembleRLearnerOptionsRPart( parameters, b, true );
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_REGR_LM ) ) {
      // Nothing to do for lm!
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_SVM ) || m_mlrLearner.getReadable()
        .equalsIgnoreCase( R_REGR_SVM ) ) {
      assembleRLearnerOptionsSimpleList( parameters, b, Arrays.asList( "type", "kernel" ) );
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_RANDOM_FOREST ) || m_mlrLearner.getReadable()
        .equalsIgnoreCase( R_REGR_RANDOM_FOREST ) ) {
      assembleRLearnerOptionsSimpleList( parameters, b, new ArrayList<String>() );
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_GBM ) ) {
      assembleRLearnerOptionsSimpleList( parameters, b, new ArrayList<String>() );
    } else if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_NNET ) || m_mlrLearner.getReadable()
        .equalsIgnoreCase( R_REGR_NNET ) ) {
      assembleRLearnerOptionsSimpleList( parameters, b, new ArrayList<String>() );
    }

    if ( b.length() > 0 ) {
      try {
        setLearnerOptsOnScheme( m_scheme, b.toString() );
      } catch ( Exception ex ) {
        throw new WekaException( ex );
      }
    }
  }

  protected void assembleRLearnerOptionsRPart( Map<String, Map<String, Object>> parameters, StringBuilder b,
      boolean isRegression ) {
    if ( !isRegression ) {
      Map<String, Object> priorProp = parameters.get( "Prior" );
      Map<String, Object> splitProp = parameters.get( "Splitting criteria" );
      if ( priorProp != null || splitProp != null ) {
        String priorVal = priorProp != null ? priorProp.get( "value" ).toString() : null;
        String splitVal = splitProp != null ? splitProp.get( "value" ).toString() : null;
        if ( !Const.isEmpty( priorVal ) || !Const.isEmpty( splitVal ) ) {
          b.append( "parms=list(" );

          if ( priorProp != null && !Const.isEmpty( priorVal ) ) {
            b.append( "prior=c(" ).append( priorVal ).append( ")" );
          }

          if ( splitProp != null && !Const.isEmpty( splitVal ) ) {
            if ( b.charAt( b.length() - 1 ) == ')' ) {
              b.append( "," );
            }
            b.append( "split=" ).append( "\"" ).append( splitVal ).append( "\"" );
          }

          b.append( ")" );
        }
      }
    }

    // TODO control = rpart.control options
    if ( b.length() > 0 ) {
      b.append( "," );
    }

    Map<String, Object> aProp = parameters.get( "Min split" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "minsplit=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Min bucket" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "minbucket=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Complexity parameter" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "cp=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Max compete" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "maxcompete=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Max surrogate" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "maxsurrogate=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Use surrogate" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "usesurrogate=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Num cross-validations" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "xval=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Surrogate style" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "surrogatestyle=" ).append( aProp.get( "value" ) ).append( "," );
    }
    aProp = parameters.get( "Max depth" );
    if ( aProp != null && !Const.isEmpty( aProp.get( "value" ).toString() ) ) {
      b.append( "maxdepth=" ).append( aProp.get( "value" ) );
    }
    if ( b.charAt( b.length() - 1 ) == ',' ) {
      b.setLength( b.length() - 1 );
    }
  }

  protected void assembleRLearnerOptionsSimpleList( Map<String, Map<String, Object>> parameters, StringBuilder b,
      List<String> quoteList ) {
    for ( Map<String, Object> p : parameters.values() ) {
      Boolean rProp = (Boolean) p.get( "rProp" );
      Boolean metaProp = (Boolean) p.get( "meta-prop" );
      String type = p.get( "type" ).toString();
      if ( ( rProp == null || !rProp ) || ( metaProp != null && metaProp ) ) {
        continue;
      }
      String name = p.get( "param-name" ).toString();
      String val = p.get( "value" ).toString();
      if ( type.equalsIgnoreCase( "boolean" ) ) {
        val = val.toUpperCase();
      }
      /* if ( quoteList.contains( name ) ) {
        name = "'" + name + "'";
      } */
      if ( !Const.isEmpty( val ) ) {
        if ( quoteList.contains( name ) && !val.startsWith( "'" ) ) {
          b.append( name ).append( "=" ).append( "'" ).append( val ).append( "'" ).append( "," );
        } else {
          b.append( name ).append( "=" ).append( val ).append( "," );
        }
      }
    }
    if ( b.length() > 0 ) {
      b.setLength( b.length() - 1 );
    }
  }

  protected String getLearnerParamValueSimple( String[] params, String paramName ) {
    String result = null;
    for ( String p : params ) {
      if ( p.toLowerCase().startsWith( paramName.toLowerCase() ) ) {
        String[] parts = p.split( "=" );
        if ( parts.length == 2 ) {
          result = parts[1];
        }
      }
    }
    return result;
  }

  protected String getLearnerParamValueComplex( String paramString, String paramName ) {
    String result = null;

    if ( paramString.contains( paramName ) ) {
      result = paramString.substring( paramString.indexOf( paramName ) );
    }

    return result;
  }

  protected void addIndividualRLearnerPropToMap( Map<String, Object> propMap, String name, String label, String tipText,
      boolean rProp, boolean metaProp, String type, String pickValues, String learnerParams, String paramName,
      Object defaultVal ) {
    propMap.put( "name", name );
    propMap.put( "label", label );
    propMap.put( "tip-text", tipText );
    propMap.put( "rProp", rProp );
    propMap.put( "meta-prop", metaProp );
    propMap.put( "param-name", paramName );
    propMap.put( "type", type );
    if ( pickValues != null ) {
      propMap.put( "pick-list-values", pickValues );
    }

    String existingVal = getLearnerParamValueComplex( learnerParams, paramName );
    if ( existingVal != null ) {
      String[] parts = existingVal.split( "," );
      if ( parts.length > 0 ) {
        existingVal = parts[0].trim();
        existingVal = existingVal.split( "=" )[1].trim();
      }
    }
    propMap.put( "value", existingVal != null ? existingVal : defaultVal );
  }

  /**
   * Configure the underlying scheme using the supplied command-line option settings
   *
   * @param options an array of command-line option settings
   * @throws Exception if a problem occurs
   */
  @Override public void setSchemeOptions( String[] options ) throws Exception {
    if ( m_scheme != null ) {
      if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL1LOGREG ) ) {
        // check for the meta option for L2 regularization
        try {
          if ( Utils.getFlag( "meta-l2", options ) ) {
            Tag tempTag = findApplicableTagForScheme( R_CLASSIF_LIBLINEARL2LOGREG );
            setLearnerOnScheme( m_scheme, tempTag.getID() );
            m_mlrLearner = tempTag;
          }
        } catch ( Exception e ) {
          e.printStackTrace();
        }
      }

      ( (OptionHandler) m_scheme ).setOptions( options );
    }
  }

  protected String getLearnerOptsFromScheme( Classifier mlrClassifier )
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String result = "";

    Method m = mlrClassifier.getClass().getDeclaredMethod( "getLearnerParams" );
    result = (String) m.invoke( mlrClassifier );

    return result;
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
      String[] schemeOpts = ( (OptionHandler) m_scheme ).getOptions();
      List<String> opts = new ArrayList<>( Arrays.asList( schemeOpts ) );
      if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL2LOGREG ) ) {
        // add the meta option for L2 regularization
        opts.add( "-meta-l2" );
      }

      return opts.toArray( new String[opts.size()] );
    }
    return null;
  }

  /**
   * Set underlying scheme parameters from a map of parameter values. Note that this will set only primitive parameter
   * types on the scheme. It does not process nested objects. This method is used primarily by the GUI editor dialogs. Use
   * setSchemeOptions() to handle all parameters (including those on nested complex objects).
   *
   * @param schemeParameters a map of scheme parameters to set
   * @throws Exception if a problem occurs.
   */
  @Override public void setSchemeParameters( Map<String, Map<String, Object>> schemeParameters ) throws Exception {
    setRLearnerOptions( schemeParameters );
  }

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
  @Override public Object getConfiguredScheme( Instances trainingHeader ) throws Exception {
    Classifier finalScheme = adjustForSamplingAndPreprocessing( trainingHeader, m_scheme );
    if ( m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_LIBLINEARL1LOGREG ) || m_mlrLearner.getReadable()
        .equalsIgnoreCase( R_CLASSIF_LIBLINEARL2LOGREG ) || m_mlrLearner.getReadable().equalsIgnoreCase( R_CLASSIF_NNET ) ||
    m_mlrLearner.getReadable().equalsIgnoreCase( R_REGR_NNET )) {
      boolean removeUselessInPlay = checkForFilter( finalScheme, ".RemoveUseless" );
      if ( !( finalScheme instanceof FilteredClassifier ) ) {
        finalScheme = new FilteredClassifier();
        ( (FilteredClassifier) finalScheme ).setClassifier( m_scheme );
        ( (FilteredClassifier) finalScheme ).setFilter( new MultiFilter() );
      }
      MultiFilter filter = (MultiFilter) ( (FilteredClassifier) finalScheme ).getFilter();
      List<Filter> currentFilters = new ArrayList<>();
      currentFilters.addAll( Arrays.asList( filter.getFilters() ) );

      if ( !removeUselessInPlay ) {
        currentFilters.add( new RemoveUseless() );
        currentFilters.add( new NominalToBinary() );
      } else {
        currentFilters.add( new NominalToBinary() );
      }
      filter.setFilters( currentFilters.toArray( new Filter[currentFilters.size()] ) );
    }

    return finalScheme;
  }
}
