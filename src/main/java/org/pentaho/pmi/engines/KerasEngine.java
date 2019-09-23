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
public class KerasEngine extends PMIEngine {

  /**
   * Name of the engine
   */
  public static final String ENGINE_NAME = "Keras";

  /**
   * Engine class
   */
  public static final String ENGINE_CLASS = KerasEngine.class.getCanonicalName();

  @Override public String engineName() {
    return ENGINE_NAME;
  }

  @Override public boolean engineAvailable( List<String> messages ) {
    try {
      WekaPackageClassLoaderManager.forName( "weka.classifiers.keras.KerasZooClassifier" );
      return true;
    } catch ( ClassNotFoundException e ) {
      messages.add( e.getMessage() );
    }
    return false;
  }

  @Override public boolean supportsScheme( String schemeName ) {
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !KerasScheme.s_excludedSchemes
        .contains( schemeName );
  }

  @Override public Scheme getScheme( String schemeName )
      throws EngineNotAvailableException, UnsupportedSchemeException {
    try {
      return KerasScheme.getSupervisedKerasScheme( schemeName );
    } catch ( Exception e ) {
      throw new UnsupportedSchemeException( e );
    }
  }
}
