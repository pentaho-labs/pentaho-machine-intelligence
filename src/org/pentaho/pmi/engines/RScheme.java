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
 * Implementation of a {@code Scheme} for the MLR R engine.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public abstract class RScheme extends Scheme {

  /** A list of those schemes that are not available in MLR */
  protected static List<String> s_excludedSchemes = Arrays.asList( "Naive Bayes incremental", "Naive Bayes multinomial" );

  /**
   * Constructor
   *
   * @param schemeName the name of the underlying scheme to wrap
   */
  protected RScheme( String schemeName ) {
    super( schemeName );
  }

  /**
   * Static factory method for obtaining a {@code Scheme} instance that encapsulates an MRL R implementation of the
   * named scheme
   *
   * @param schemeName the name of the scheme to get
   * @return a {@code Scheme} object
   * @throws Exception if a problem occurs
   */
  protected static Scheme getSupervisedRScheme( String schemeName ) throws Exception {
    if ( SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !s_excludedSchemes.contains( schemeName ) ) {
      return new RClassifierScheme( schemeName );
    } else {
      // TODO clusterers?
    }
    throw new UnsupportedSchemeException( "The R engine (MLR) does not support the " + schemeName + " scheme." );
  }
}
