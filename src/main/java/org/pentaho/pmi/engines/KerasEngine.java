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
