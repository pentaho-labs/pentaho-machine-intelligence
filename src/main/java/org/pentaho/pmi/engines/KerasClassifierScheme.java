package org.pentaho.pmi.engines;

import org.pentaho.pmi.SchemeUtils;
import org.pentaho.pmi.SupervisedScheme;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.WekaPackageClassLoaderManager;

import java.util.List;
import java.util.Map;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class KerasClassifierScheme extends SupervisedScheme {

  protected Classifier m_underlyingScheme;

  public KerasClassifierScheme( String schemeName ) throws Exception {
    super( schemeName );

    instantiateKerasClassifier( schemeName );
  }

  @Override public boolean canHandleData( Instances data, List<String> messages ) {
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

  @Override public boolean supportsIncrementalTraining() {
    return false;
  }

  @Override public boolean supportsResumableTraining() {
    return false;
  }

  @Override public boolean canHandleStringAttributes() {
    return true;
  }

  @Override public boolean supportsEnvironmentVariables() {
    return true;
  }

  @Override public Map<String, Object> getSchemeInfo() throws Exception {
    return SchemeUtils.getSchemeParameters( m_underlyingScheme );
  }

  @Override public void setSchemeOptions( String[] options ) throws Exception {
    if ( m_underlyingScheme != null ) {
      ( (OptionHandler) m_underlyingScheme ).setOptions( options );
    }
  }

  @Override public String[] getSchemeOptions() {
    if ( m_underlyingScheme != null ) {
      return ( (OptionHandler) m_underlyingScheme ).getOptions();
    }
    return null;
  }

  @Override public void setSchemeParameters( Map<String, Map<String, Object>> schemeParameters ) throws Exception {
    SchemeUtils.setSchemeParameters( m_underlyingScheme, schemeParameters );
  }

  @Override public Object getConfiguredScheme( Instances trainingHeader ) throws Exception {
    return adjustForSamplingAndPreprocessing( trainingHeader, m_underlyingScheme );
  }

  @Override public void setConfiguredScheme( Object scheme ) throws Exception {
    if ( !scheme.getClass().getCanonicalName().equals( "weka.classifiers.keras.KerasZooClassifier" ) ) {
      throw new Exception( "Supplied configured scheme is not a KerasZooClassifier" );
    }
    ( (OptionHandler) m_underlyingScheme ).setOptions( ( (OptionHandler) scheme ).getOptions() );
  }

  protected void instantiateKerasClassifier( String schemeName ) throws Exception {
    m_schemeName = schemeName;
    m_underlyingScheme =
        (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.keras.KerasZooClassifier" );
  }
}
