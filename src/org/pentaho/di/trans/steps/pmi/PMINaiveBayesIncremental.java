package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMINaiveBayesIncremental", image = "WEKAS.svg", name = "PMI Naive Bayes incremental", description = "Train and evaluate an incremental naive Bayes classifier", categoryDescription = "Data Mining" )
public class PMINaiveBayesIncremental extends BaseSupervisedPMIStepMeta {

  public PMINaiveBayesIncremental() {
    setSchemeName( "Naive Bayes incremental" );
  }
}
