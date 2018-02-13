/*******************************************************************************
 * Pentaho Data Science
 * <p/>
 * Copyright (c) 2002-2018 Hitachi Vantara. All rights reserved.
 * <p/>
 * ******************************************************************************
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

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
