package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIXGBClassifier", image = "WEKAS.svg", name = "PMI Extreme Boosting Classifier", description = "Train and evaluate an extreme gradient boosting (xgboost) classifier", categoryDescription = "Data Mining" )
public class PMIXGBClassifier extends BaseSupervisedPMIStepMeta {

  public PMIXGBClassifier() {
    setSchemeName( "Extreme gradient boosting classifier" );
  }
}
