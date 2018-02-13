package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMILinearRegression", image = "WEKAS.svg", name = "PMI Linear Regression", description = "Train and evaluate a linear regression model", categoryDescription = "Data Mining" )
public class PMILinearRegression extends BaseSupervisedPMIStepMeta {

  public PMILinearRegression() {
    setSchemeName( "Linear regression" );
  }
}
