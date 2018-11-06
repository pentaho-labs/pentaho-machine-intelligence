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

import org.pentaho.pmi.EngineNotAvailableException;
import org.pentaho.pmi.PMIEngine;
import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.core.WekaPackageClassLoaderManager;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Implementation of a PMI engine that uses the R MLR package.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class REngine extends PMIEngine {

  /**
   * Engine name
   */
  public static final String ENGINE_NAME = "R - MLR";

  /**
   * Engine class
   */
  public static final String ENGINE_CLASS = REngine.class.getCanonicalName();

  /**
   * True if the necessary requirements for R (R itself, rJava and various environment variables) are present
   */
  protected static boolean s_engineAvailable;

  static {
    try {
      Class<?> rsessionClass = WekaPackageClassLoaderManager.forName( "weka.core.RSession" );

      Method availMeth = rsessionClass.getDeclaredMethod( "rAvailable" );
      s_engineAvailable = (Boolean) availMeth.invoke( null );

    } catch ( Exception ex ) {
      ex.printStackTrace();
    }
  }

  /**
   * Get the name of this engine
   *
   * @return the name of this engine
   */
  @Override public String engineName() {
    return ENGINE_NAME;
  }

  /**
   * Returns true if the R engine is available
   *
   * @param messages a list to store error messages/info in
   * @return true if the R engine is available
   */
  @Override public boolean engineAvailable( List<String> messages ) {
    return s_engineAvailable;
  }

  /**
   * Returns true if the named scheme is supported by the R engine
   *
   * @param schemeName the name of the scheme to check
   * @return true if the scheme is supported
   */
  @Override public boolean supportsScheme( String schemeName ) {
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !RScheme.s_excludedSchemes
        .contains( schemeName );
  }

  /**
   * Get a {@code Scheme} object that encapsulates the named scheme.
   *
   * @param schemeName the name of the scheme to get
   * @return a {@code Scheme} object
   * @throws EngineNotAvailableException if this engine is not available for some reason
   * @throws UnsupportedSchemeException  if the named scheme is not supported by the R engine
   */
  @Override public Scheme getScheme( String schemeName )
      throws EngineNotAvailableException, UnsupportedSchemeException {
    if ( !s_engineAvailable ) {
      throw new EngineNotAvailableException( engineName() + " is not available!" );
    }
    try {
      return RScheme.getSupervisedRScheme( schemeName );
    } catch ( Exception ex ) {
      throw new UnsupportedSchemeException( ex );
    }
  }
}
