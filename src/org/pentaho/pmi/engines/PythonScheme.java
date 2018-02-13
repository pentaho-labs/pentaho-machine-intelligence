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

import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;

import java.util.Arrays;
import java.util.List;

/**
 * Implementation of a {@code Scheme} for the python engine.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public abstract class PythonScheme extends Scheme {

  /** A list of those schemes that are not available in Python scikit-learn */
  protected static List<String> s_excludedSchemes = Arrays.asList( "Naive Bayes incremental" );

  /**
   * Constructor
   *
   * @param schemeName the name of the underlying scheme
   */
  public PythonScheme( String schemeName ) {
    super( schemeName );
  }

  /**
   * Static factory method for obtaining a {@code Scheme} instance that encapsulates a scikit-learn implementation of
   * the named scheme
   *
   * @param schemeName the name of the scheme to get
   * @return a {@code Scheme} object
   * @throws Exception if a problem occurs
   */
  protected static Scheme getSupervisedPythonScheme( String schemeName ) throws Exception {
    if ( SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !s_excludedSchemes.contains( schemeName ) ) {
      return new PythonClassifierScheme( schemeName );
    } else {
      // TODO clusterers?
    }

    throw new UnsupportedSchemeException(
        "The scikit-learn (Python) engine does not support the " + schemeName + " scheme." );
  }
}
