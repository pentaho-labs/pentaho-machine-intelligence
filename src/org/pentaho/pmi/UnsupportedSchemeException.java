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
 * Exception to be thrown when a scheme is specified that is not supported by a given engine.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class UnsupportedSchemeException extends Exception {

  /**
   * Constructor without a message
   */
  public UnsupportedSchemeException() {
    super();
  }

  /**
   * Constructor with a message
   *
   * @param message the message for the exception
   */
  public UnsupportedSchemeException( String message ) {
    super( message );
  }

  /**
   * Constructor with message and cause
   *
   * @param message the message for the exception
   * @param cause   the root cause Throwable
   */
  public UnsupportedSchemeException( String message, Throwable cause ) {
    this( message );
    initCause( cause );
    fillInStackTrace();
  }

  /**
   * Constructor with cause argument
   *
   * @param cause the root cause Throwable
   */
  public UnsupportedSchemeException( Throwable cause ) {
    this( cause.getMessage(), cause );
  }
}
