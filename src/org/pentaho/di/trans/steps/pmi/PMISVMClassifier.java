package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMISVMClassifier", image = "WEKAS.svg", name = "PMI Support Vector Classifier", description = "Train and evaluate a support vector classification model", categoryDescription = "Data Mining")
public class PMISVMClassifier extends BaseSupervisedPMIStepMeta {

  public PMISVMClassifier() {
    setSchemeName( "Support vector classifier" );
  }
}
