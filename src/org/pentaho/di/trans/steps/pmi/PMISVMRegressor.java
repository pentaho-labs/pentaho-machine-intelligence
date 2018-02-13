package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMISVMRegressor", image = "WEKAS.svg", name = "PMI Support Vector Regressor", description = "Train and evaluate a support vector regressor model", categoryDescription = "Data Mining")
public class PMISVMRegressor extends BaseSupervisedPMIStepMeta {

  public PMISVMRegressor() {
    setSchemeName( "Support vector regressor" );
    // setSchemeCommandLineOptions( "-S 3" );
  }
}
