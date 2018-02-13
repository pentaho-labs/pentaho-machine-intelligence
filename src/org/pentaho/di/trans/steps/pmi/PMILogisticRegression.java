package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMILogisticRegression", image = "WEKAS.svg", name = "PMI Logistic Regression", description = "Train and evaluate a logistic regression model", categoryDescription = "Data Mining" )
public class PMILogisticRegression extends BaseSupervisedPMIStepMeta {

  public PMILogisticRegression() {
    setSchemeName( "Logistic regression" );
  }
}
