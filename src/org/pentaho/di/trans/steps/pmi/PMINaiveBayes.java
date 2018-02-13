package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMINaiveBayes", image = "WEKAS.svg", name = "PMI Naive Bayes", description = "Train and evaluate a naive Bayes model", categoryDescription = "Data Mining")
public class PMINaiveBayes extends BaseSupervisedPMIStepMeta {

  public PMINaiveBayes() {
    setSchemeName( "Naive Bayes" );
  }
}
