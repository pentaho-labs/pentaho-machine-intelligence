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

import org.pentaho.pmi.EngineNotAvailableException;
import org.pentaho.pmi.PMIEngine;
import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.core.WekaPackageClassLoaderManager;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Implementation of a PMI engine that uses the Python Scikit-Learn machine learning library.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class PythonEngine extends PMIEngine {

  /**
   * the name of this engine
   */
  public static final String ENGINE_NAME = "Python - Scikit-learn";

  /** Engine class */
  public static final String ENGINE_CLASS = PythonEngine.class.getCanonicalName();

  /**
   * Indicates whether the Weka wekaPython package is available (and cpython is available)
   */
  protected static boolean s_engineAvailable;

  static {
    try {
      Class<?> pythonSessionClass = WekaPackageClassLoaderManager.forName( "weka.python.PythonSession" );

      Method availMeth = pythonSessionClass.getDeclaredMethod( "pythonAvailable" );
      Boolean result = (Boolean) availMeth.invoke( null );

      if ( !result ) {
        // try initializing
        Method initMeth = pythonSessionClass.getDeclaredMethod( "initSession", String.class, boolean.class );
        result = (Boolean) initMeth.invoke( null, "python", false );
      }

      s_engineAvailable = result;
    } catch ( Exception ex ) {
      ex.printStackTrace();
    }
  }

  /**
   * Return the name of this engine
   *
   * @return the name of this engine
   */
  @Override public String engineName() {
    return ENGINE_NAME;
  }

  /**
   * Returns true if the python engine is available
   *
   * @param messages a list to store error messages/info in
   * @return true if the python engine is available
   */
  @Override public boolean engineAvailable( List<String> messages ) {
    return s_engineAvailable;
  }

  /**
   * Returns true if the named scheme is supported by the wekaPython engine
   *
   * @param schemeName the name of the scheme to check
   * @return true if the named scheme is supported
   */
  @Override public boolean supportsScheme( String schemeName ) {
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName )
        && !PythonScheme.s_excludedSchemes.contains( schemeName );
  }

  /**
   * Returns a Scheme object, using Python Scikit-Learn as the underlying engine, for the named scheme.
   *
   * @param schemeName the name of the scheme to get
   * @return a Scheme object
   * @throws EngineNotAvailableException if this engine is not available for some reason
   * @throws UnsupportedSchemeException if the named scheme is not supported
   */
  @Override public Scheme getScheme( String schemeName ) throws EngineNotAvailableException, UnsupportedSchemeException {
    if ( !s_engineAvailable ) {
      throw new EngineNotAvailableException( engineName() + " is not available!" );
    }

    try {
      return PythonScheme.getSupervisedPythonScheme( schemeName );
    } catch ( Exception ex ) {
      throw new UnsupportedSchemeException( ex );
    }
  }
}
