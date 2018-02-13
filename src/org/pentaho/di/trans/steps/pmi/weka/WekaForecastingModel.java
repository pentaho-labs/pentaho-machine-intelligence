/*******************************************************************************
 * Pentaho Data Science
 * <p/>
 * Copyright (c) 2002-2017 Hitachi Vantara. All rights reserved.
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

package org.pentaho.di.trans.steps.pmi.weka;

import java.io.Serializable;
import java.util.List;

import org.pentaho.di.core.logging.LogChannelInterface;

import weka.classifiers.timeseries.AbstractForecaster;
import weka.classifiers.timeseries.TSForecaster;
import weka.classifiers.timeseries.core.ConfidenceIntervalForecaster;
import weka.core.Instances;

/**
 * Abstract wrapper class for a Weka Forecaster.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision$
 */
public class WekaForecastingModel implements Serializable {

  /**
   * For serialization
   */
  private static final long serialVersionUID = -65688685303674846L;

  // The header of the Instances presented to the buildForecaster() method
  private Instances m_header;

  protected TSForecaster m_model;

  /**
   * Creates a new <code>WekaForecastingModel</code> instance.
   *
   * @param model the actual Weka model to enacpsulate
   */
  public WekaForecastingModel( TSForecaster model ) {
    setModel( model );
  }

  /**
   * Set the log to pass on to the model.
   * require logging.
   *
   * @param log the log to use
   */
  public void setLog( LogChannelInterface log ) {

  }

  /**
   * Set the Instances header
   *
   * @param header an <code>Instances</code> value
   */
  public void setHeader( Instances header ) {
    m_header = header;
  }

  /**
   * Get the header of the Instances that was used
   * build this Weka model
   *
   * @return an <code>Instances</code> value
   */
  public Instances getHeader() {
    return m_header;
  }

  /**
   * Set the forecasting model
   *
   * @param model the forecasting model
   */
  public void setModel( TSForecaster model ) {
    m_model = model;
  }

  /**
   * Get the forecasting model
   *
   * @return the forecasting model as an object
   */
  public TSForecaster getModel() {
    return m_model;
  }

  /**
   * Get a list of the field names that the encapsulated forecaster
   * can predict.
   *
   * @return a list of predicted field names.
   */
  public List<String> getForecastedFields() {
    List<String> fields = AbstractForecaster.
        stringToList( m_model.getFieldsToForecast() );
    return fields;
  }

  /**
   * Returns true if the encapsulated model can and is producing
   * confidence intervals on its forcasted values.
   *
   * @return true if the forecaster produces confidence limits
   * for its predicted values
   */
  public boolean isProducingConfidenceIntervals() {
    if ( m_model == null ) {
      return false;
    }

    if ( !( m_model instanceof ConfidenceIntervalForecaster ) ) {
      return false;
    }

    return ( (ConfidenceIntervalForecaster) m_model ).isProducingConfidenceIntervals();
  }

  /**
   * Return a textual description of the encapsulated forecasting model
   *
   * @return a textual description of the encapsulated forecasting model
   */
  public String toString() {
    if ( m_model == null ) {
      return "No encapsulated scoring model!!!";
    }

    return m_model.toString();
  }
}
