package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIDecisionTreeRegressor", image = "WEKAS.svg", name = "PMI Decision Tree Regressor", description = "Train and evaluate a decision tree for regression model", categoryDescription = "Data Mining" )
public class PMIDecisionTreeRegressor extends BaseSupervisedPMIStepMeta {

  public PMIDecisionTreeRegressor() {
    setSchemeName( "Decision tree regressor" );
  }
}
