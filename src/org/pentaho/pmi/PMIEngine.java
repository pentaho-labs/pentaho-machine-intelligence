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

package org.pentaho.pmi;

import org.pentaho.pmi.engines.MLlibEngine;
import org.pentaho.pmi.engines.PythonEngine;
import org.pentaho.pmi.engines.REngine;
import org.pentaho.pmi.engines.WekaEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for an engine.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 */
public abstract class PMIEngine {

  /**
   * property name for specifying which engines are supported. If not set, then the ENV variable is checked, and then
   * all engines are, at least initially, assumed to be available. Can be used to specify that new engines are available
   * as well as limiting the default engines. The format is a comma separated list of engine name:fully qualified engine class
   */
  protected static final String SUPPORTED_ENGINE_PROPERTY_KEY = "org.pentaho.pmi.engines";

  /**
   * Environment variable name for specifying which engines are supported. The java property takes precedence over this
   * variable. If neither are set, then all engines are, at least initially, assumed to be available. Can be used to
   * specify that new engines are available as well as limiting the default engines. The format is a comma separated
   * list of engine name:fully qualified engine class
   */
  protected static final String SUPPORTED_ENGINE_ENV_KEY = "ORG_PENTAHO_PMI_ENGINES";

  protected static Map<String, String> s_availableEngines = new LinkedHashMap<>();

  public synchronized static void init() {
    if ( s_availableEngines.size() == 0 ) {

      String engineNames = System.getProperty( SUPPORTED_ENGINE_PROPERTY_KEY, "" );
      if ( engineNames.length() == 0 ) {
        engineNames = System.getenv( SUPPORTED_ENGINE_ENV_KEY );
      }
      List<String> availableEngines = new ArrayList<>();
      if ( engineNames != null && engineNames.length() > 0 ) {
        String[] names = engineNames.split( "," );
        for ( String n : names ) {
          String[] eParts = n.split( ":" );
          String eName = eParts[0].trim();
          String eClass = eParts[1].trim();
          try {
            instantiateEngine( eClass );
            s_availableEngines.put( eName, eClass );
          } catch ( Exception e ) {
            e.printStackTrace();
          }
        }
      }

      // nothing specified in the supported engine key, so add all the defaults
      if ( availableEngines.size() == 0 ) {
        s_availableEngines.put( WekaEngine.ENGINE_NAME, WekaEngine.ENGINE_CLASS );
        s_availableEngines.put( PythonEngine.ENGINE_NAME, PythonEngine.ENGINE_CLASS );
        s_availableEngines.put( REngine.ENGINE_NAME, REngine.ENGINE_CLASS );
        s_availableEngines.put( MLlibEngine.ENGINE_NAME, MLlibEngine.ENGINE_CLASS );
      }
    }
  }

  /**
   * Get a list of available engine names
   *
   * @return a list of available engine names
   */
  public static List<String> getEngineNames() {
    init();

    List<String> result = new ArrayList<>();
    result.addAll( s_availableEngines.keySet() );
    return result;
  }

  /**
   * Attempts to instantiate a named PMIEngine
   *
   * @param engineClass the name of the class to instantiate
   * @return a PMIEngine object
   * @throws ClassNotFoundException if the class can't be located
   * @throws IllegalAccessException if a problem occurs
   * @throws InstantiationException if the engine can't be instantiated
   */
  public static PMIEngine instantiateEngine( String engineClass )
      throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    Object engine = Class.forName( engineClass ).newInstance();

    if ( !( engine instanceof PMIEngine ) ) {
      throw new InstantiationException( engineClass + " is not a subclass of PMIEngine!" );
    }

    return (PMIEngine) engine;
  }

  /**
   * Get the named engine
   *
   * @param name the name of the engine to get
   * @return the named engine
   * @throws UnsupportedEngineException  if the named engine is not known/supported
   */
  public static PMIEngine getEngine( String name ) throws UnsupportedEngineException {
    String engineClass = s_availableEngines.get( name );
    if ( engineClass != null ) {
      try {
        return instantiateEngine( engineClass );
      } catch ( Exception ex ) {
        throw new UnsupportedEngineException( ex );
      }
    }

    throw new UnsupportedEngineException( "Unknown engine '" + name + "'" );
  }

  /**
   * Get the name of the engine supported by this concrete implementation
   *
   * @return the name of the engine supported by this concrete implementation
   */
  public abstract String engineName();

  /**
   * Performs checks to see if the engine is available to use (e.g. installed, environment variables set etc.)
   *
   * @param messages a list to store error messages/info in
   * @return true if this engine is available
   */
  public abstract boolean engineAvailable( List<String> messages );

  /**
   * Returns true if this concrete implementation of an engine supports the named predictive scheme
   *
   * @param schemeName the name of the scheme to check
   * @return true if this engine supports the scheme
   */
  public abstract boolean supportsScheme( String schemeName );

  /**
   * Get the named scheme
   *
   * @param schemeName the name of the scheme to get
   * @return the scheme
   * @throws EngineNotAvailableException if this engine is not available
   * @throws UnsupportedSchemeException  if this concrete engine implementation does not support the named scheme
   */
  public abstract Scheme getScheme( String schemeName ) throws EngineNotAvailableException, UnsupportedSchemeException;
}
