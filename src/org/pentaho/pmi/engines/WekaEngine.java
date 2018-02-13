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

  /** Engine class */
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
    return SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName );
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
