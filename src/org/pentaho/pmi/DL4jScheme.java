package org.pentaho.pmi;

import java.util.Arrays;
import java.util.List;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class DL4jScheme {

  /**
   * A list of those global schemes that are not supported in Spark MLlib 1.6
   */
  protected static List<String>
      s_excludedSchemes =
      Arrays.asList( "Naive Bayes", "Naive Bayes incremental", "Naive Bayes multinomial", "Decision tree classfier",
          "Decision tree regressor", "Random forest classifier", "Random forest regressor", "Gradient boosted trees" );

  public static Scheme getSupervisedDlL4jScheme( String schemeName ) throws Exception {
    if ( SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !s_excludedSchemes
        .contains( schemeName ) ) {

    } else {
      // TODO do not support unsupervised schemes yet
    }
    throw new UnsupportedSchemeException( "The DL4j engine does not support the " + schemeName + " scheme." );
  }

}
