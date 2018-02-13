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

package org.pentaho.pmi;

/**
 * Exception to be thrown when an engine is specified that is not currently supported by PMI
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class UnsupportedEngineException extends Exception {

  /**
   * Constructor without a message
   */
  public UnsupportedEngineException() {
    super();
  }

  /**
   * Constructor with a message
   *
   * @param message the message for the exception
   */
  public UnsupportedEngineException( String message ) {
    super( message );
  }

  /**
   * Constructor with message and cause
   *
   * @param message the message for the exception
   * @param cause   the root cause Throwable
   */
  public UnsupportedEngineException( String message, Throwable cause ) {
    this( message );
    initCause( cause );
    fillInStackTrace();
  }

  /**
   * Constructor with cause argument
   *
   * @param cause the root cause Throwable
   */
  public UnsupportedEngineException( Throwable cause ) {
    this( cause.getMessage(), cause );
  }
}
