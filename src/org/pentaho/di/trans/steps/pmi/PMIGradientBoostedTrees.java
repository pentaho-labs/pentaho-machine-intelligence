package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * Meta class for the PMI gradient boosted trees scheme.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIGradientBoostedTrees", image = "WEKAS.svg", name = "PMI Gradient Boosted Trees", description = "Train and evaluate a gradient boosted trees model", categoryDescription = "Data Mining")
public class PMIGradientBoostedTrees extends BaseSupervisedPMIStepMeta {

  public PMIGradientBoostedTrees() {
    setSchemeName( "Gradient boosted trees" );
  }
}
