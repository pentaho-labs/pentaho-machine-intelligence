package org.pentaho.pmi.engines;

import org.pentaho.pmi.EngineNotAvailableException;
import org.pentaho.pmi.PMIEngine;
import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.core.WekaPackageClassLoaderManager;

import java.util.List;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class DL4jEngine extends PMIEngine {

  /**
   * Name of the engine
   */
  public static final String ENGINE_NAME = "DL4j";

  public static final String ENGINE_CLASS = DL4jEngine.class.getCanonicalName();

  @Override public String engineName() {
    return ENGINE_NAME;
  }

  @Override public boolean engineAvailable( List<String> messages ) {

    try {
      WekaPackageClassLoaderManager.forName( "weka.classifiers.functions.Dl4jMlpClassifier" );
      return true;
    } catch ( ClassNotFoundException e ) {
      if ( messages != null ) {
        messages.add( e.getMessage() );
      }
    }

    return false;
  }

  @Override public boolean supportsScheme( String schemeName ) {
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !DL4jScheme.s_excludedSchemes
        .contains( schemeName );
  }

  @Override public Scheme getScheme( String schemeName )
      throws EngineNotAvailableException, UnsupportedSchemeException {

    if ( !engineAvailable( null ) ) {
      throw new EngineNotAvailableException( engineName() + " is not available!" );
    }

    try {
      return DL4jScheme.getSupervisedDlL4jScheme( schemeName );
    } catch ( Exception ex ) {
      throw new UnsupportedSchemeException( ex );
    }
  }
}
