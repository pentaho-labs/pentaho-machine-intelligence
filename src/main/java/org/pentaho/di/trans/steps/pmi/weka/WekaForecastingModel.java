/*******************************************************************************
 * Pentaho Data Science
 * <p/>
 * Copyright (c) 2002-2018 Hitachi Vantara. All rights reserved.
 * <p/>
 * ******************************************************************************
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 * <p/>
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
