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

package org.pentaho;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.w3c.dom.Node;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for generating step metadata for Step's with options that are simple bean properties and
 * are annotated with the SimpleStepOption annotation.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class MetaHelper {

  protected static List<PropertyDescriptor> getPropertyStuff( Object target ) throws IntrospectionException {
    List<PropertyDescriptor> stuff = new ArrayList<>();

    BeanInfo bi = Introspector.getBeanInfo( target.getClass() );
    PropertyDescriptor[] props = bi.getPropertyDescriptors();

    for ( PropertyDescriptor p : props ) {
      Method getter = p.getReadMethod();
      Method setter = p.getWriteMethod();
      if ( getter == null || setter == null ) {
        continue;
      }

      SimpleStepOption opt = getter.getAnnotation( SimpleStepOption.class );
      if ( opt == null ) {
        opt = setter.getAnnotation( SimpleStepOption.class );
      }

      if ( opt == null ) {
        continue;
      }
      stuff.add( p );
    }

    return stuff;
  }

  public static StringBuilder getXMLForTarget( Object target )
      throws IntrospectionException, InvocationTargetException, IllegalAccessException {
    List<PropertyDescriptor> props = getPropertyStuff( target );
    StringBuilder builder = new StringBuilder();

    for ( PropertyDescriptor p : props ) {
      Method getter = p.getReadMethod();
      String name = p.getDisplayName();

      Object value = getter.invoke( target );
      if ( value instanceof String ) {
        builder.append( XMLHandler.addTagValue( name, (String) value ) );
      } else if ( value instanceof Integer ) {
        builder.append( XMLHandler.addTagValue( name, (Integer) value ) );
      } else if ( value instanceof Long ) {
        builder.append( XMLHandler.addTagValue( name, (Long) value ) );
      } else if ( value instanceof Double ) {
        builder.append( XMLHandler.addTagValue( name, (Double) value ) );
      } else if ( value instanceof Float ) {
        builder.append( XMLHandler.addTagValue( name, (Float) value ) );
      } else if ( value instanceof Boolean ) {
        builder.append( XMLHandler.addTagValue( name, (Boolean) value ) );
      }
    }

    return builder;
  }

  public static void loadXMLForTarget( Node stepnode, Object target )
      throws KettleXMLException, IntrospectionException, InvocationTargetException, IllegalAccessException {
    List<PropertyDescriptor> props = getPropertyStuff( target );

    for ( PropertyDescriptor p : props ) {
      Method setter = p.getWriteMethod();
      Method getter = p.getReadMethod();
      String name = p.getDisplayName();
      Object forValueType = getter.invoke( target );

      String toSet = XMLHandler.getTagValue( stepnode, name );

      if ( forValueType instanceof String ) {
        setter.invoke( target, toSet );
      } else if ( forValueType instanceof Integer ) {
        setter.invoke( target, new Integer( toSet.toString() ) );
      } else if ( forValueType instanceof Long ) {
        setter.invoke( target, new Long( toSet.toString() ) );
      } else if ( forValueType instanceof Double ) {
        setter.invoke( target, new Double( toSet.toString() ) );
      } else if ( forValueType instanceof Float ) {
        setter.invoke( target, new Float( toSet.toString() ) );
      } else if ( forValueType instanceof Boolean ) {
        setter.invoke( target, toSet.toString().equalsIgnoreCase( "Y" ) );
      }
    }
  }

  public static void saveRepForTarget( Repository rep, ObjectId id_transformation, ObjectId id_step, Object target )
      throws KettleException, IntrospectionException, InvocationTargetException, IllegalAccessException {
    List<PropertyDescriptor> props = getPropertyStuff( target );

    for ( PropertyDescriptor p : props ) {
      Method getter = p.getReadMethod();
      String name = p.getDisplayName();

      Object value = getter.invoke( target );
      if ( value instanceof String ) {
        rep.saveStepAttribute( id_transformation, id_step, name, (String) value );
      } else if ( value instanceof Integer ) {
        rep.saveStepAttribute( id_transformation, id_step, name, (Integer) value );
      } else if ( value instanceof Long ) {
        rep.saveStepAttribute( id_transformation, id_step, name, (Long) value );
      } else if ( value instanceof Double ) {
        rep.saveStepAttribute( id_transformation, id_step, name, (Double) value );
      } else if ( value instanceof Float ) {
        rep.saveStepAttribute( id_transformation, id_step, name, (Float) value );
      } else if ( value instanceof Boolean ) {
        rep.saveStepAttribute( id_transformation, id_step, name, (Boolean) value );
      }
    }
  }

  public static void readRepForTarget( Repository rep, ObjectId id_step, Object target )
      throws KettleException, IntrospectionException, InvocationTargetException, IllegalAccessException {
    List<PropertyDescriptor> props = getPropertyStuff( target );

    for ( PropertyDescriptor p : props ) {
      Method setter = p.getWriteMethod();
      Method getter = p.getReadMethod();
      String name = p.getDisplayName();
      Object forValueType = getter.invoke( target );

      String toSet = rep.getStepAttributeString( id_step, name );
      if ( forValueType instanceof String ) {
        setter.invoke( target, toSet );
      } else if ( forValueType instanceof Integer ) {
        setter.invoke( target, new Integer( toSet ) );
      } else if ( forValueType instanceof Long ) {
        setter.invoke( target, new Long( toSet.toString() ) );
      } else if ( forValueType instanceof Double ) {
        setter.invoke( target, new Double( toSet.toString() ) );
      } else if ( forValueType instanceof Float ) {
        setter.invoke( target, new Float( toSet.toString() ) );
      } else if ( forValueType instanceof Boolean ) {
        setter.invoke( target, toSet.toString().equalsIgnoreCase( "Y" ) );
      }
    }
  }
}
