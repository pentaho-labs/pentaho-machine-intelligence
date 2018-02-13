package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIDecisionTreeClassifier", image = "WEKAS.svg", name = "PMI Decision Tree Classifier", description = "Train and evaluate a decision tree for classification model", categoryDescription = "Data Mining")
public class PMIDecisionTreeClassifier extends BaseSupervisedPMIStepMeta {

  public PMIDecisionTreeClassifier() {
    setSchemeName( "Decision tree classifier" );
  }
}
