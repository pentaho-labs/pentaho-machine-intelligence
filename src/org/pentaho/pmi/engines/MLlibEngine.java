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

package org.pentaho.pmi.engines;

import org.pentaho.pmi.PMIEngine;
import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.core.WekaPackageClassLoaderManager;

import java.util.Arrays;
import java.util.List;

/**
 * Engine for Spark MLlib. Uses the MLlib integration provided by Weka's distributedWekaSparkDev package.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class MLlibEngine extends PMIEngine {

  /** Name of the engine */
  public static final String ENGINE_NAME = "Spark - MLlib";

  /** Engine class */
  public static final String ENGINE_CLASS = MLlibEngine.class.getCanonicalName();

  /** A list of those schemes that are not available in MLlib */
  protected static List<String> s_excludedSchemes = Arrays.asList( "Naive Bayes incremental", "Support vector regressor" );

  /**
   * Get the name of the engine
   *
   * @return the name of the engine
   */
  @Override public String engineName() {
    return ENGINE_NAME;
  }

  /**
   * Returns true if the Spark MLlib engine is available
   *
   * @param messages a list to store error messages/info in
   * @return true if the Spark engine is available
   */
  @Override public boolean engineAvailable( List<String> messages ) {

    try {
      WekaPackageClassLoaderManager.forName( "weka.classifiers.mllib.MLlibClassifier" );
      return true;
    } catch ( ClassNotFoundException e ) {
      messages.add( e.getMessage() );
    }

    return false;
  }

  /**
   * Returns true if the named scheme is supported by the Spark MLlib engine
   *
   * @param schemeName the name of the scheme to check
   * @return true if the named scheme is supported
   */
  @Override public boolean supportsScheme( String schemeName ) {
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !s_excludedSchemes.contains( schemeName );
  }

  /**
   * Get an instance of {@code Scheme} that encapsulates the named scheme in Spark MLlib
   *
   * @param schemeName the name of the scheme to get
   * @return a {@code Scheme} object
   * @throws UnsupportedSchemeException
   */
  @Override public Scheme getScheme( String schemeName ) throws UnsupportedSchemeException {
    try {
      return MLlibScheme.getSupervisedMLlibScheme( schemeName );
    } catch ( Exception ex ) {
      throw new UnsupportedSchemeException( ex );
    }
  }
}
