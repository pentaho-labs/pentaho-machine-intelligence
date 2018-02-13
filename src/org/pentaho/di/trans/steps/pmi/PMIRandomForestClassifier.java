package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIRandomForestClassifier", image = "WEKAS.svg", name = "PMI Random Forest Classifier", description = "Train and evaluate a random forest classification model", categoryDescription = "Data Mining")
public class PMIRandomForestClassifier extends BaseSupervisedPMIStepMeta {

  public PMIRandomForestClassifier() {
    setSchemeName( "Random forest classifier" );
  }
}
