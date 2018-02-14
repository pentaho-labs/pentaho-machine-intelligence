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
