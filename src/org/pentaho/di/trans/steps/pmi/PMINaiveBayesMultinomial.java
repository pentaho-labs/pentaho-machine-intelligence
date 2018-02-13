package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMINaiveBayesMultinomial", image = "WEKAS.svg", name = "PMI Naive Bayes Multinomial", description = "Train and evaluate a multinomial naive Bayes model", categoryDescription = "Data Mining")
public class PMINaiveBayesMultinomial extends BaseSupervisedPMIStepMeta {

  public PMINaiveBayesMultinomial() {
    setSchemeName( "Naive Bayes multinomial" );
  }
}
