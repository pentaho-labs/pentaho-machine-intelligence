package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIRandomForestRegressor", image = "WEKAS.svg", name = "PMI Random Forest Regressor", description = "Train and evaluate a random forest regression model", categoryDescription = "Data Mining" )
public class PMIRandomForestRegressor extends BaseSupervisedPMIStepMeta {

  public PMIRandomForestRegressor() {
    setSchemeName( "Random forest regressor" );
  }
}
