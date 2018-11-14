package org.pentaho.pmi.engines;

import org.pentaho.pmi.Scheme;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;

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
      Arrays.asList( "Naive Bayes", "Naive Bayes incremental", "Naive Bayes multinomial", "Decision tree classifier",
          "Decision tree regressor", "Random forest classifier", "Random forest regressor", "Gradient boosted trees",
          "Support vector regressor", "Multi-layer perceptron classifier", "Multi-layer perceptron regressor");

  public static Scheme getSupervisedDlL4jScheme( String schemeName ) throws Exception {
    if ( SupervisedScheme.s_defaultClassifierSchemeList.contains( schemeName ) && !s_excludedSchemes
        .contains( schemeName ) ) {
      return new DL4jClassifierScheme( schemeName );
    } else {
      // TODO do not support unsupervised schemes yet
    }
    throw new UnsupportedSchemeException( "The DL4j engine does not support the " + schemeName + " scheme." );
  }

}
