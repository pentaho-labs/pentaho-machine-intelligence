package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIXGBRegressor", image = "WEKAS.svg", name = "PMI Extreme Boosting Regressor", description = "Train and evaluate an extreme gradient boosting (xgboost) regressor", categoryDescription = "PMI" )
public class PMIXGBRegressor extends BaseSupervisedPMIStepMeta {
  public PMIXGBRegressor() {
    setSchemeName( "Extreme gradient boosting regressor" );
  }
}
