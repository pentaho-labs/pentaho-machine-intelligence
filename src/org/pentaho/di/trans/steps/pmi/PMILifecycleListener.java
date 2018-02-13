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

package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.KettleLifecyclePlugin;
import org.pentaho.di.core.lifecycle.KettleLifecycleListener;
import org.pentaho.di.core.lifecycle.LifecycleException;
import weka.core.Version;
import weka.core.WekaPackageManager;
import weka.core.packageManagement.VersionPackageConstraint;

import java.util.List;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@KettleLifecyclePlugin( id = "PMILifecycleListener", name = "PMIScoringLifecycleListener" )
public class PMILifecycleListener implements KettleLifecycleListener {
  @Override public void onEnvironmentInit() throws LifecycleException {
    System.setProperty( "weka.core.logging.Logger", "weka.core.logging.ConsoleLogger" );

    // TODO replace this by some code that somehow locates the pdm jar file in plugins/steps/pmi/lib
    // This allows the Spark engine to locate the main weka.jar file for use in the Spark execution environment
    System.setProperty( "weka.jar.filename", "pdm-ce-3.8.1.1.jar" );

    // check that the required packages are installed (and possibly install if not)
    try {
      // make sure the package metadata cache is established first
      WekaPackageManager.establishCacheIfNeeded( System.out );

      // python dependency
      weka.core.packageManagement.Package
          pythonPackage =
          weka.core.WekaPackageManager.getInstalledPackageInfo( "wekaPython" );
      if ( pythonPackage == null ) {
        String latestCompatibleVersion = getLatestVersion( "wekaPython" );
        System.out.println(
            "[PMI] wekaPython package is not installed - attempting to install version " + latestCompatibleVersion );
        WekaPackageManager.installPackageFromRepository( "wekaPython", latestCompatibleVersion, System.out );
      }

      // R dependency
      weka.core.packageManagement.Package rPackage = weka.core.WekaPackageManager.getInstalledPackageInfo( "RPlugin" );
      if ( rPackage == null ) {
        String latestCompatibleVersion = getLatestVersion( "RPlugin" );
        System.out.println(
            "[PMI] RPlugin package is not installed - attempting to install version " + latestCompatibleVersion );
        WekaPackageManager.installPackageFromRepository( "RPlugin", latestCompatibleVersion, System.out );
      }

      // Weka dependency
      weka.core.packageManagement.Package
          libSVMPackage =
          weka.core.WekaPackageManager.getInstalledPackageInfo( "LibSVM" );
      if ( libSVMPackage == null ) {
        String latestCompatibleVersion = getLatestVersion( "LibSVM" );
        System.out.println(
            "[PMI] libSVM package is not installed - attempting to install version " + latestCompatibleVersion );
        WekaPackageManager.installPackageFromRepository( "LibSVM", latestCompatibleVersion, System.out );
      }

      // MLlib dependencies
      weka.core.packageManagement.Package
          distributedWekaBasePackage =
          weka.core.WekaPackageManager.getInstalledPackageInfo( "distributedWekaBase" );
      if ( distributedWekaBasePackage == null ) {
        String latestCompatibleVersion = getLatestVersion( "distributedWekaBase" );
        System.out.println( "[PMI] distributed Weka base package is not installed - attempting to install version "
            + latestCompatibleVersion );
        WekaPackageManager.installPackageFromRepository( "distributedWekaBase", latestCompatibleVersion, System.out );
      }

      weka.core.packageManagement.Package
          distributedWekaSparkDevPackage =
          weka.core.WekaPackageManager.getInstalledPackageInfo( "distributedWekaSparkDev" );
      if ( distributedWekaSparkDevPackage == null ) {
        String latestCompatibleVersion = getLatestVersion( "distributedWekaSparkDev" );
        System.out.println( "[PMI] distributed Weka Spark package is not installed - attempting to install version "
            + latestCompatibleVersion );
        WekaPackageManager
            .installPackageFromRepository( "distributedWekaSparkDev", latestCompatibleVersion, System.out );
      }
    } catch ( Exception e ) {
      e.printStackTrace();
    }

    weka.core.WekaPackageManager.loadPackages( false );
  }

  @Override public void onEnvironmentShutdown() {
    // Noop
  }

  protected String getLatestVersion( String packageName ) throws Exception {
    String version = "none";
    List<Object> availableVersions = WekaPackageManager.getRepositoryPackageVersions( packageName );
    // version numbers will be in descending sorted order from the
    // repository. We want the most recent version that is compatible
    // with the base weka install
    for ( Object v : availableVersions ) {
      weka.core.packageManagement.Package
          versionedPackage =
          WekaPackageManager.getRepositoryPackageInfo( packageName, v.toString() );
      if ( versionedPackage.isCompatibleBaseSystem() ) {
        version = versionedPackage.getPackageMetaDataElement( VersionPackageConstraint.VERSION_KEY ).toString();
        break;
      }
    }

    if ( version.equals( "none" ) ) {
      throw new Exception(
          "Was unable to find a version of '" + packageName + "' that is compatible with Weka " + Version.VERSION );
    }

    return version;
  }
}
