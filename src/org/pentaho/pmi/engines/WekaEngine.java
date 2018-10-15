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

import org.pentaho.pmi.PMIEngine;
import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;

import java.util.List;

/**
 * Implementation of a PMI engine that uses the Weka machine learning library.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class WekaEngine extends PMIEngine {

  /**
   * Name of the engine
   */
  public static final String ENGINE_NAME = "Weka";

  /**
   * Engine class
   */
  public static final String ENGINE_CLASS = WekaEngine.class.getCanonicalName();

  /**
   * Get the name of this engine
   *
   * @return the name of this engine
   */
  @Override public String engineName() {
    return ENGINE_NAME;
  }

  /**
   * Returns true if this engine is currently available
   *
   * @param messages a list to store error messages/info in
   * @return true if Weka is available
   */
  @Override public boolean engineAvailable( List<String> messages ) {
    // Weka will always be available :-)
    // This could change if schemes are added that reside in Weka packages, and those
    // packages are not installed
    return true;
  }

  /**
   * Returns true if Weka supports the named scheme
   *
   * @param schemeName the name of the scheme to check
   * @return true if Weka supports the named scheme
   */
  @Override public boolean supportsScheme( String schemeName ) {
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !WekaScheme.s_excludedSchemes
        .contains( schemeName );
  }

  /**
   * Returns a Scheme object, using Weka as the underlying engine, for the named scheme.
   *
   * @param schemeName the name of the scheme to get
   * @return a Scheme object
   * @throws UnsupportedSchemeException if the named scheme is not supported by Weka
   */
  @Override public Scheme getScheme( String schemeName ) throws UnsupportedSchemeException {
    return WekaScheme.getSupervisedWekaScheme( schemeName );
  }
}
