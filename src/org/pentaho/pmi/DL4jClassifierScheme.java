package org.pentaho.pmi;

import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.WekaPackageClassLoaderManager;

import java.util.List;
import java.util.Map;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class DL4jClassifierScheme extends SupervisedScheme {

  protected Classifier m_underlyingScheme;

  public DL4jClassifierScheme( String schemeName ) throws Exception {
    super( schemeName );

    if ( schemeName.equalsIgnoreCase( "Multi-layer perceptron classifier" ) ) {

    } else if ( schemeName.equalsIgnoreCase( "Multi-layer perceptron regressor" ) ) {

    } else if ( schemeName.equalsIgnoreCase( "Logistic regression" ) ) {

    } else if ( schemeName.equalsIgnoreCase( "Support vector classifier" ) ) {

    } else if ( schemeName.equalsIgnoreCase( "Support vector regressor" ) ) {

    } else if ( schemeName.equalsIgnoreCase( "Deep learning network" ) ) {

    } else {
      throw new UnsupportedSchemeException( "DL4j engine does not support " + schemeName );
    }
  }

  protected void instantiateDL4jClassifier( String schemeName ) throws Exception {
    m_underlyingScheme =
        (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.functions.Dl4jMlpClassifier" );

    if ( schemeName.equalsIgnoreCase( "Logistic regression" ) ) {

    }
  }

  @Override public boolean canHandleData( Instances data, List<String> messages ) {
    return false;
  }

  @Override public boolean supportsIncrementalTraining() {
    return false;
  }

  @Override public boolean canHandleStringAttributes() {
    return false;
  }

  @Override public Map<String, Object> getSchemeInfo() throws Exception {
    return null;
  }

  @Override public void setSchemeOptions( String[] options ) throws Exception {

  }

  @Override public String[] getSchemeOptions() {
    return new String[0];
  }

  @Override public void setSchemeParameters( Map<String, Map<String, Object>> schemeParameters ) throws Exception {

  }

  @Override public Object getConfiguredScheme( Instances trainingHeader ) throws Exception {
    return null;
  }
}
