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
 * Base class for schemes implemented in MLlib. Only supports supervised classification and regression so far
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public abstract class MLlibScheme extends Scheme {

  /**
   * A list of those global schemes that are not supported in Spark MLlib
   */
  protected static List<String>
      s_excludedSchemes =
      Arrays.asList( "Naive Bayes incremental", "Support vector regression" );

  /**
   * Constructor
   *
   * @param schemeName the name of the scheme
   */
  public MLlibScheme( String schemeName ) {
    super( schemeName );
  }

  /**
   * Static factory method for obtaining {@code Scheme} objects encapsulating particular MLlib implementations
   *
   * @param schemeName the name of the scheme to get
   * @return a {@code Scheme} object
   * @throws Exception if a problem occurs
   */
  public static Scheme getSupervisedMLlibScheme( String schemeName ) throws Exception {
    if ( SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !s_excludedSchemes.contains( schemeName ) ) {
      return new MLlibClassifierScheme( schemeName );
    } else {
      // TODO MLlib package does not provide clusterers yet
    }
    throw new UnsupportedSchemeException( "The Spark MLlib engine does not support the " + schemeName + " scheme." );
  }
}
