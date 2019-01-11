package org.pentaho.pmi.engines;

import org.pentaho.pmi.SchemeUtils;
import org.pentaho.pmi.SupervisedScheme;
import org.pentaho.pmi.UnsupportedSchemeException;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.Utils;
import weka.core.WekaPackageClassLoaderManager;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class DL4jClassifierScheme extends SupervisedScheme {

  protected Classifier m_underlyingScheme;
  protected String m_schemeName;

  public DL4jClassifierScheme( String schemeName ) throws Exception {
    super( schemeName );

    instantiateDL4jClassifier( schemeName );
  }

  protected void instantiateDL4jClassifier( String schemeName ) throws Exception {
    m_schemeName = schemeName;
    m_underlyingScheme =
        (Classifier) WekaPackageClassLoaderManager.objectForName( "weka.classifiers.functions.Dl4jMlpClassifier" );

    if ( schemeName.equalsIgnoreCase( "Logistic regression" ) ) {
      // output layer needs log likelihood loss
      // need to add dense layer with number of outputs = number of independent variables
      // set bias updater to Adam with LR 0.01
      // set updater Adam's LR to 0.01
      // set epochs to 50?

      setOptionsForObject( (OptionHandler) m_underlyingScheme,
          "-iterator \"weka.dl4j.iterators.instance.DefaultInstanceIterator -bs 16\" "
              + "-numEpochs 50 -layer \"weka.dl4j.layers.DenseLayer -nOut 0 -activation \\\"weka.dl4j.activations.ActivationReLU \\\" "
              + "-name \\\"Dense layer\\\"\" -layer \"weka.dl4j.layers.OutputLayer "
              + "-lossFn \\\"weka.dl4j.lossfunctions.LossNegativeLogLikelihood \\\" -nOut 0 "
              + "-activation \\\"weka.dl4j.activations.ActivationSoftmax \\\" -name \\\"Output layer\\\"\"" );

      Object neuralNetConfig = getProp( m_underlyingScheme, "getNeuralNetConfiguration" );
      setOptionsForObject( (OptionHandler) neuralNetConfig,
          "-biasInit 0.0 -biasUpdater \"weka.dl4j.updater.Adam -beta1MeanDecay 0.9 -beta2VarDecay 0.999 "
              + "-epsilon 1.0E-8 -lr 0.01 -lrSchedule \\\"weka.dl4j.schedules.ConstantSchedule -scheduleType EPOCH\\\"\" "
              + "-dist \"weka.dl4j.distribution.Disabled \" -dropout \"weka.dl4j.dropout.Disabled \" "
              + "-gradientNormalization None -gradNormThreshold 1.0 -l1 NaN -l2 NaN -minimize "
              + "-algorithm STOCHASTIC_GRADIENT_DESCENT -updater \"weka.dl4j.updater.Adam -beta1MeanDecay 0.9 "
              + "-beta2VarDecay 0.999 -epsilon 1.0E-8 -lr 0.01 -lrSchedule \\\"weka.dl4j.schedules.ConstantSchedule "
              + "-scheduleType EPOCH\\\"\" -weightInit XAVIER -weightNoise \"weka.dl4j.weightnoise.Disabled \"" );
    } else if ( schemeName.equalsIgnoreCase( "Linear regression" ) ) {
      setOptionsForObject( (OptionHandler) m_underlyingScheme,
          "-iterator \"weka.dl4j.iterators.instance.DefaultInstanceIterator -bs 16\" "
              + "-numEpochs 50 -layer \"weka.dl4j.layers.DenseLayer -nOut 0 -activation \\\"weka.dl4j.activations.ActivationReLU \\\" "
              + "-name \\\"Dense layer\\\"\" -layer \"weka.dl4j.layers.OutputLayer "
              + "-lossFn \\\"weka.dl4j.lossfunctions.LossMSE \\\" -nOut 0 "
              + "-activation \\\"weka.dl4j.activations.ActivationSoftmax \\\" -name \\\"Output layer\\\"\"" );
      Object neuralNetConfig = getProp( m_underlyingScheme, "getNeuralNetConfiguration" );
      setOptionsForObject( (OptionHandler) neuralNetConfig,
          "-biasInit 0.0 -biasUpdater \"weka.dl4j.updater.Adam -beta1MeanDecay 0.9 -beta2VarDecay 0.999 "
              + "-epsilon 1.0E-8 -lr 0.01 -lrSchedule \\\"weka.dl4j.schedules.ConstantSchedule -scheduleType EPOCH\\\"\" "
              + "-dist \"weka.dl4j.distribution.Disabled \" -dropout \"weka.dl4j.dropout.Disabled \" "
              + "-gradientNormalization None -gradNormThreshold 1.0 -l1 NaN -l2 NaN -minimize "
              + "-algorithm STOCHASTIC_GRADIENT_DESCENT -updater \"weka.dl4j.updater.Adam -beta1MeanDecay 0.9 "
              + "-beta2VarDecay 0.999 -epsilon 1.0E-8 -lr 0.01 -lrSchedule \\\"weka.dl4j.schedules.ConstantSchedule "
              + "-scheduleType EPOCH\\\"\" -weightInit XAVIER -weightNoise \"weka.dl4j.weightnoise.Disabled \"" );
    } else if ( schemeName.equalsIgnoreCase( "Support vector classifier" ) ) {
      setOptionsForObject( (OptionHandler) m_underlyingScheme,
          "-iterator \"weka.dl4j.iterators.instance.DefaultInstanceIterator -bs 16\" "
              + "-numEpochs 50 -layer \"weka.dl4j.layers.DenseLayer -nOut 0 -activation \\\"weka.dl4j.activations.ActivationReLU \\\" "
              + "-name \\\"Dense layer\\\"\" -layer \"weka.dl4j.layers.OutputLayer "
              + "-lossFn \\\"weka.dl4j.lossfunctions.LossHinge \\\" -nOut 0 "
              + "-activation \\\"weka.dl4j.activations.ActivationSoftmax \\\" -name \\\"Output layer\\\"\"" );

      Object neuralNetConfig = getProp( m_underlyingScheme, "getNeuralNetConfiguration" );
      setOptionsForObject( (OptionHandler) neuralNetConfig,
          "-biasInit 0.0 -biasUpdater \"weka.dl4j.updater.Adam -beta1MeanDecay 0.9 -beta2VarDecay 0.999 "
              + "-epsilon 1.0E-8 -lr 0.01 -lrSchedule \\\"weka.dl4j.schedules.ConstantSchedule -scheduleType EPOCH\\\"\" "
              + "-dist \"weka.dl4j.distribution.Disabled \" -dropout \"weka.dl4j.dropout.Disabled \" "
              + "-gradientNormalization None -gradNormThreshold 1.0 -l1 NaN -l2 NaN -minimize "
              + "-algorithm STOCHASTIC_GRADIENT_DESCENT -updater \"weka.dl4j.updater.Adam -beta1MeanDecay 0.9 "
              + "-beta2VarDecay 0.999 -epsilon 1.0E-8 -lr 0.01 -lrSchedule \\\"weka.dl4j.schedules.ConstantSchedule "
              + "-scheduleType EPOCH\\\"\" -weightInit XAVIER -weightNoise \"weka.dl4j.weightnoise.Disabled \"" );
    } else if ( schemeName.equalsIgnoreCase( "Deep learning network" ) ) {
      // nothing to do here, as this is maximum flexibility to the user to adjust layers, hyper-paramenters etc.
    } else {
      throw new UnsupportedSchemeException( "DL4j engine does not support " + schemeName );
    }
  }

  protected void setOptionsForObject( OptionHandler target, String optionString ) throws Exception {
    target.setOptions( Utils.splitOptions( optionString ) );
  }

  protected Object getProp( Object target, String getName ) throws Exception {
    Method m = target.getClass().getDeclaredMethod( getName );

    return m.invoke( target );
  }

  @Override public boolean canHandleData( Instances data, List<String> messages ) {
    try {
      Classifier finalClassifier = (Classifier) getConfiguredScheme( data );
      finalClassifier.getCapabilities().testWithFail( data );
    } catch ( Exception ex ) {
      messages.add( ex.getMessage() );
      return false;
    }

    addStringAttributeWarningMessageIfNeccessary( data, messages );
    return true;
  }

  @Override public boolean supportsIncrementalTraining() {
    return false;
  }

  /**
   * WekaDl4jMlpClassifier is an IterativeClassifier and therefore supports resumable training
   *
   * @return true
   */
  @Override public boolean supportsResumableTraining() {
    return true;
  }

  @Override public boolean canHandleStringAttributes() {
    // TODO really need to make a reflective call to see if the instance iterator is an
    // ImageInstanceIterator or a text-related iterator. This is the only time that string attributes
    // can be handled directly
    return true;
  }

  @Override public Map<String, Object> getSchemeInfo() throws Exception {
    return SchemeUtils.getSchemeParameters( m_underlyingScheme );
  }

  @Override public void setSchemeOptions( String[] options ) throws Exception {
    if ( m_underlyingScheme != null ) {
      ( (OptionHandler) m_underlyingScheme ).setOptions( options );
    }
  }

  @Override public String[] getSchemeOptions() {
    if ( m_underlyingScheme != null ) {
      return ( (OptionHandler) m_underlyingScheme ).getOptions();
    }
    return null;
  }

  @Override public void setSchemeParameters( Map<String, Map<String, Object>> schemeParameters ) throws Exception {
    SchemeUtils.setSchemeParameters( m_underlyingScheme, schemeParameters );
  }

  @Override public Object getConfiguredScheme( Instances trainingHeader ) throws Exception {

    // set outputs from single dense hidden layer to be equal to the number of independent variables
    // for all network structures except for unrestricted deep learning network.
    if ( !m_schemeName.equalsIgnoreCase( "Deep learning network" ) ) {
      int numInputs = trainingHeader.numAttributes() - 1;
      Object layerArray = getProp( m_underlyingScheme, "getLayers" );
      // dense layer will be first in the array, unless user has done something weird
      Object denseLayer = Array.get( layerArray, 0 );

      setOptionsForObject( (OptionHandler) denseLayer, "-nOut " + numInputs );
    }

    return adjustForSamplingAndPreprocessing( trainingHeader, m_underlyingScheme );
  }

  public void setConfiguredScheme( Object scheme ) throws Exception {
    if ( !scheme.getClass().getCanonicalName().equals( "weka.classifiers.functions.Dl4jMlpClassifier" ) ) {
      throw new Exception( "Supplied configured scheme is not a Dl4jMlpClassifier" );
    }
    // Just copy over option settings from the supplied scheme, so that we avoid consuming
    // memory for large trained models (model gets loaded again when transformation is executed)
    ((OptionHandler) m_underlyingScheme).setOptions( ((OptionHandler) scheme).getOptions() );
    // m_underlyingScheme = (Classifier) scheme;
  }
}
