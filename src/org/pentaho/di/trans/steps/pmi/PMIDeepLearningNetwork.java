package org.pentaho.di.trans.steps.pmi;

import org.pentaho.di.core.annotations.Step;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
@Step( id = "PMIDeepLearningNetwork", image = "WEKAS.svg", name = "PMI Deep learning network", description = "Train and evaluate a deep learning network model", categoryDescription = "Data Mining" )
public class PMIDeepLearningNetwork  extends BaseSupervisedPMIStepMeta {

  public PMIDeepLearningNetwork() {
    setSchemeName( "Deep learning network" );
  }
}
