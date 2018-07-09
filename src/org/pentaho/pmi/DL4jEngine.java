package org.pentaho.pmi;

import weka.core.WekaPackageClassLoaderManager;

import java.util.List;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class DL4jEngine extends PMIEngine {

  @Override public String engineName() {
    return "DL4jEngine";
  }

  @Override public boolean engineAvailable( List<String> messages ) {

    try {
      WekaPackageClassLoaderManager.forName( "weka.classifiers.functions.Dl4jMlpClassifier" );
    } catch ( ClassNotFoundException e ) {
      messages.add( e.getMessage() );
    }

    return false;
  }

  @Override public boolean supportsScheme( String schemeName ) {
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !DL4jScheme.s_excludedSchemes
        .contains( schemeName );
  }

  @Override public Scheme getScheme( String schemeName )
      throws EngineNotAvailableException, UnsupportedSchemeException {
    return null;
  }
}
