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

package org.pentaho.pmi;

import org.pentaho.di.core.Const;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.OptionMetadata;
import weka.core.SelectedTag;
import weka.core.Tag;
import weka.core.Utils;
import weka.filters.Filter;
import weka.gui.FilePropertyMetadata;
import weka.gui.GenericArrayEditor;
import weka.gui.GenericObjectEditor;
import weka.gui.PasswordProperty;
import weka.gui.ProgrammaticProperty;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility routines for PMI.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class SchemeUtils {

  /**
   * Set parameters on a scheme object using the supplied map of options. Reflection is used to set the parameters.
   *
   * @param target       the scheme object to set options/parameters on
   * @param propertyList a map of option-value settings
   * @throws Exception if a problem occurs
   */
  @SuppressWarnings( "unchecked" ) public static void setSchemeParameters( Object target,
      Map<String, Map<String, Object>> propertyList ) throws Exception {
    // Map<String, Map<String, Object>> propertyList = (Map<String, Map<String, Object>>) parameters.get( "properties" );

    BeanInfo bi = Introspector.getBeanInfo( target.getClass() );
    PropertyDescriptor[] properties = bi.getPropertyDescriptors();
    for ( PropertyDescriptor p : properties ) {
      if ( propertyList.containsKey( p.getName() ) ) {
        Map<String, Object> propDetails = (Map<String, Object>) propertyList.get( p.getName() );

        Object valueToSet = null;
        Method setMethod = p.getWriteMethod();
        Method readMethod = p.getReadMethod();
        Class<?> mType = p.getPropertyType();

        String propType = propDetails.get( "type" ).toString();
        if ( propType.equals( "object" ) || propType.equals( "array" ) ) {
          valueToSet = propDetails.get( "objectValue" );
        } else {
          String value = propDetails.get( "value" ).toString();
          boolean isEnum = propDetails.get( "isEnum" ) == null ? false : (boolean) propDetails.get( "isEnum" );
          valueToSet = stringToValue( value, propType, target, readMethod, isEnum );
        }

        if ( valueToSet != null ) {
          Object[] args = { valueToSet };
          setMethod.invoke( target, args );
        }
      }
    }
  }

  /**
   * Converts a string representation of a parameter value to the actual value.
   *
   * @param value           the string value to convert
   * @param type            the type of the parameter
   * @param beingConfigured the object that will be configured with the parameter value. This is needed to obtain legal
   *                        values for enumerated value types.
   * @param readMethod      the accessor method for the parameter in question
   * @param isEnum          true if the value is an enumerated type
   * @return the converted value
   * @throws Exception if a problem occurs
   */
  protected static Object stringToValue( String value, String type, Object beingConfigured, Method readMethod,
      boolean isEnum ) throws Exception {
    Object result = null;

    if ( type.equalsIgnoreCase( "short" ) ) {
      result = !Const.isEmpty( value ) ? new Short( value ) : null;
    } else if ( type.equalsIgnoreCase( "integer" ) ) {
      result = !Const.isEmpty( value ) ? new Integer( value ) : null;
    } else if ( type.equalsIgnoreCase( "long" ) ) {
      result = !Const.isEmpty( value ) ? new Long( value ) : null;
    } else if ( type.equalsIgnoreCase( "float" ) ) {
      result = !Const.isEmpty( value ) ? new Float( value ) : null;
    } else if ( type.equalsIgnoreCase( "double" ) ) {
      result = !Const.isEmpty( value ) ? new Double( value ) : null;
    } else if ( type.equalsIgnoreCase( "file" ) ) {
      result = !Const.isEmpty( value ) ? new File( value ) : null;
    } else if ( type.equalsIgnoreCase( "boolean" ) ) {
      result = !Const.isEmpty( value ) ? Boolean.parseBoolean( value ) : null;
    } else if ( type.equalsIgnoreCase( "string" ) ) {
      result = value;
    } else if ( type.equalsIgnoreCase( "pick-list" ) ) {
      if ( isEnum ) {
        Object oVal = readMethod.invoke( beingConfigured );
        Class<?> enumClass = ( (Enum) oVal ).getDeclaringClass();

        Method valuesM = enumClass.getMethod( "values" );
        Enum[] values = (Enum[]) valuesM.invoke( null );

        for ( Enum e : values ) {
          if ( e.toString().equals( value ) ) {
            result = e;
            break;
          }
        }
        // result = EnumHelper.valueFromString( enumClass.getCanonicalName(), value );
      } else {
        if ( !Const.isEmpty( value ) ) {
          SelectedTag currV = (SelectedTag) readMethod.invoke( beingConfigured );
          Tag[] tags = currV.getTags();
          result = new SelectedTag( value, tags );
        }
      }
    }

    return result;
  }

  /**
   * Constructs a map of property names and values for the supplied Object.
   *
   * @param target the object to build the property map for
   * @return a map of property and values
   * @throws Exception if a problem occurs
   */
  public static Map<String, Object> getSchemeParameters( Object target ) throws Exception {
    Map<String, Object> schemeMap = new LinkedHashMap<>();

    schemeMap.put( "topLevelClass", target.getClass().getCanonicalName() );
    schemeMap.put( "topLevelSchemeObject", target );
    Map<String, Map<String, Object>> propertyList = new LinkedHashMap<>();
    schemeMap.put( "properties", propertyList );

    PropertyDescriptor[] properties = null;
    MethodDescriptor[] methods = null;
    Object[] values;
    String[] labels;
    String[] tipTexts;

    BeanInfo bi = Introspector.getBeanInfo( target.getClass() );
    properties = bi.getPropertyDescriptors();
    methods = bi.getMethodDescriptors();

    if ( methods != null ) {
      List<String> helpInfo = getHelpInfo( methods, target );

      schemeMap.put( "helpSummary", helpInfo.get( 0 ) );
      schemeMap.put( "helpSynopsis", helpInfo.get( 1 ) );

      int[] propOrdering = new int[properties.length];
      for ( int i = 0; i < propOrdering.length; i++ ) {
        propOrdering[i] = Integer.MAX_VALUE;
      }
      for ( int i = 0; i < properties.length; i++ ) {
        Method getter = properties[i].getReadMethod();
        Method setter = properties[i].getWriteMethod();
        if ( getter == null || setter == null ) {
          continue;
        }
        List<Annotation> annotations = new ArrayList<Annotation>();
        if ( setter.getDeclaredAnnotations().length > 0 ) {
          annotations.addAll( Arrays.asList( setter.getDeclaredAnnotations() ) );
        }
        if ( getter.getDeclaredAnnotations().length > 0 ) {
          annotations.addAll( Arrays.asList( getter.getDeclaredAnnotations() ) );
        }
        for ( Annotation a : annotations ) {
          if ( a instanceof OptionMetadata ) {
            propOrdering[i] = ( (OptionMetadata) a ).displayOrder();
            break;
          }
        }
      }
      int[] sortedPropOrderings = Utils.sort( propOrdering );
      values = new Object[properties.length];
      labels = new String[properties.length];
      tipTexts = new String[properties.length];

      for ( int i = 0; i < properties.length; i++ ) {
        Map<String, Object>
            propertyMap =
            getPropertyMap( target, i, properties, methods, sortedPropOrderings, labels, tipTexts, values );
        if ( propertyMap != null ) {
          propertyList.put( propertyMap.get( "name" ).toString(), propertyMap );
        }
      }
    }

    return schemeMap;
  }

  protected static Map<String, Object> getPropertyMap( Object target, int i, PropertyDescriptor[] properties,
      MethodDescriptor[] methods, int[] sortedPropOrderings, String[] labels, String[] tipTexts, Object[] values ) {

    if ( properties[sortedPropOrderings[i]].isHidden() ) {
      return null;
    }
    String name = properties[sortedPropOrderings[i]].getDisplayName();
    String origName = name;
    Method getter = properties[sortedPropOrderings[i]].getReadMethod();
    Method setter = properties[sortedPropOrderings[i]].getWriteMethod();
    Class<?> type = properties[sortedPropOrderings[i]].getPropertyType();
    if ( getter == null || setter == null ) {
      return null;
    }
    Map<String, Object> propertyMap = new HashMap<String, Object>();
    propertyMap.put( "password", false );

    List<Annotation> annotations = new ArrayList<Annotation>();
    if ( setter.getDeclaredAnnotations().length > 0 ) {
      annotations.addAll( Arrays.asList( setter.getDeclaredAnnotations() ) );
    }
    if ( getter.getDeclaredAnnotations().length > 0 ) {
      annotations.addAll( Arrays.asList( getter.getDeclaredAnnotations() ) );
    }

    boolean skip = false;
    boolean password = false;
    FilePropertyMetadata fileProp = null;
    boolean isFile = false;
    for ( Annotation a : annotations ) {
      if ( a instanceof ProgrammaticProperty ) {
        skip = true; // skip property that is only supposed to be manipulated
        // programatically
        break;
      }

      if ( a instanceof OptionMetadata ) {
        name = ( (OptionMetadata) a ).displayName();
        String tempTip = ( (OptionMetadata) a ).description();
        name = ( (OptionMetadata) a ).displayName();
        int ci = tempTip.indexOf( '.' );
        if ( ci < 0 ) {
          tipTexts[sortedPropOrderings[i]] = tempTip;
        } else {
          tipTexts[sortedPropOrderings[i]] = tempTip.substring( 0, ci );
        }
      }

      if ( a instanceof PasswordProperty ) {
        password = true;
      }

      if ( a instanceof FilePropertyMetadata ) {
        fileProp = (FilePropertyMetadata) a;
        isFile = true;
      }
    }
    if ( skip ) {
      return null;
    }

    try {
      Object[] args = {};
      Object value = getter.invoke( target, args );
      values[sortedPropOrderings[i]] = value;
      PropertyEditor editor = null;
      Class<?> pec = properties[sortedPropOrderings[i]].getPropertyEditorClass();
      if ( pec != null ) {
        try {
          editor = (PropertyEditor) pec.newInstance();
        } catch ( Exception ex ) {
          // drop through
        }
      }
      if ( editor == null ) {
        if ( password && String.class.isAssignableFrom( type ) ) {
          propertyMap.put( "password", true );
        } else if ( fileProp != null || File.class.isAssignableFrom( type ) ) {
          propertyMap.put( "type", "file" );
          isFile = true;
        } else {
          editor = PropertyEditorManager.findEditor( type );
        }
      }

      if ( !password && !isFile && editor == null ) {
        // skip if we can't edit it
        return null;
      }

      if ( value == null ) {
        // skip anything that has a null value
        return null;
      }

      if ( editor instanceof GenericObjectEditor ) {
        propertyMap.put( "type", "object" );
        propertyMap.put( "goeBaseType", type.getName() );
      } else if ( editor instanceof GenericArrayEditor ) {
        propertyMap.put( "type", "array" );
        // determine array type
        Class<?> elementType = getter.getReturnType().getComponentType();
        String typeC = elementType.getName();
        typeC = typeC.substring( typeC.lastIndexOf( '.' ) + 1, typeC.length() );
        if ( typeC.equalsIgnoreCase( "string" ) || typeC.equalsIgnoreCase( "file" ) || typeC
            .equalsIgnoreCase( "integer" ) || typeC.equalsIgnoreCase( "long" ) || typeC.equalsIgnoreCase( "float" )
            || typeC.equalsIgnoreCase( "double" ) || typeC.equalsIgnoreCase( "boolean" ) ) {
          propertyMap.put( "array-type", typeC.toLowerCase() );
        } else {
          // assume GOE edtiable object
          propertyMap.put( "array-type", "object" );
        }
      } else {
        // determine type
        if ( value instanceof Number ) {
          String typeC = value.getClass().getName();
          typeC = typeC.substring( typeC.lastIndexOf( '.' ) + 1, typeC.length() );
          propertyMap.put( "type", typeC.toLowerCase() );
        } else if ( value instanceof String ) {
          propertyMap.put( "type", "string" );
        } else if ( value instanceof Boolean ) {
          propertyMap.put( "type", "boolean" );
        } else if ( value instanceof SelectedTag ) {
          propertyMap.put( "type", "pick-list" );
          Tag[] tags = ( (SelectedTag) value ).getTags();
          String tagList = "";
          for ( Tag t : tags ) {
            tagList += ( "," + t.getReadable() );
          }
          tagList = tagList.substring( 1, tagList.length() );
          propertyMap.put( "pick-list-values", tagList );
        } else if ( value instanceof Enum ) {
          propertyMap.put( "type", "pick-list" );
          propertyMap.put( "isEnum", true );
          Class<?> enumClass = ( (Enum) value ).getDeclaringClass();
          Method valuesM = enumClass.getMethod( "values" );
          Enum[] evals = (Enum[]) valuesM.invoke( null );
          String tagList = "";
          for ( Enum e : evals ) {
            tagList += ( "," + e.toString() );
          }
          tagList = tagList.substring( 1, tagList.length() );
          propertyMap.put( "pick-list-values", tagList );
        }
      }

      if ( tipTexts[sortedPropOrderings[i]] == null ) {
        // now look for a TipText method for this property
        String tipName = origName + "TipText";
        for ( MethodDescriptor m_Method : methods ) {
          String mname = m_Method.getDisplayName();
          Method meth = m_Method.getMethod();
          if ( mname.equals( tipName ) ) {
            if ( meth.getReturnType().equals( String.class ) ) {
              try {
                String tempTip = (String) ( meth.invoke( target, args ) );
                int ci = tempTip.indexOf( '.' );
                if ( ci < 0 ) {
                  tipTexts[sortedPropOrderings[i]] = tempTip;
                } else {
                  tipTexts[sortedPropOrderings[i]] = tempTip.substring( 0, ci );
                }
              } catch ( Exception ex ) {
                // ignore
              }
              break;
            }
          }
        }
      }

      // set the value
      propertyMap.put( "value", value.toString() );
      if ( value instanceof OptionHandler ) {
        String schName = getTextRepresentationOfObjectValue( value );
        propertyMap.put( "value", schName ); // displayable value
        propertyMap.put( "objectValue", value ); // actual underlying value
      } else if ( value instanceof SelectedTag ) {
        propertyMap.put( "value", ( (SelectedTag) value ).getSelectedTag().getReadable() );
      } else if ( editor instanceof GenericArrayEditor ) {
        Class<?> elementType = getter.getReturnType().getComponentType();
        String typeC = elementType.getCanonicalName();
        int numElements = Array.getLength( value );
        propertyMap.put( "value", typeC + " : " + numElements );
        propertyMap.put( "objectValue", value ); // actual underlying array value
      } else if ( editor instanceof GenericObjectEditor ) {
        String schName = getTextRepresentationOfObjectValue( value );
        propertyMap.put( "value", schName );
        propertyMap.put( "objectValue", value );
      }
      // set the method name
      propertyMap.put( "name", origName );
      // set the nice name
      propertyMap.put( "label", name );
      // set the tool tip text
      propertyMap.put( "tip-text", tipTexts[sortedPropOrderings[i]] );

    } catch ( Exception ex ) {
      ex.printStackTrace();
    }

    return propertyMap;
  }

  /**
   * Gets a textual representation of the supplied object. If the object is an OptionHandler, it will return
   * the name of the class + command line option settings; otherwise, it just returns the class name.
   *
   * @param value the object to get a textual representation of
   * @return the textual representation
   */
  public static String getTextRepresentationOfObjectValue( Object value ) {
    if ( value instanceof OptionHandler ) {
      String schName = value.getClass().getCanonicalName();
      schName = schName.substring( schName.lastIndexOf( '.' ) + 1 );
      schName += " " + Utils.joinOptions( ( (OptionHandler) value ).getOptions() );
      return schName;
    } else {
      String schName = value.getClass().getCanonicalName();
      schName = schName.substring( schName.lastIndexOf( '.' ) + 1 );
      return schName;
    }
  }

  protected static List<String> getHelpInfo( MethodDescriptor[] methods, Object target ) {
    boolean firstTip = true;
    Object[] args = {};
    StringBuilder optionsBuff = new StringBuilder();
    StringBuilder helpText = null;
    String summary = null;
    for ( MethodDescriptor method : methods ) {
      String name = method.getDisplayName();
      Method meth = method.getMethod();
      OptionMetadata o = meth.getAnnotation( OptionMetadata.class );

      if ( name.endsWith( "TipText" ) || o != null ) {
        if ( meth.getReturnType().equals( String.class ) || o != null ) {
          try {
            String tempTip = o != null ? o.description() : (String) ( meth.invoke( target, args ) );
            // int ci = tempTip.indexOf('.');
            name = o != null ? o.displayName() : name;

            if ( firstTip ) {
              optionsBuff.append( "OPTIONS\n" );
              firstTip = false;
            }
            tempTip =
                tempTip.replace( "<html>", "" ).replace( "</html>", "" ).replace( "<br>", "\n" )
                    .replace( "<p>", "\n\n" );
            optionsBuff.append( name.replace( "TipText", "" ) ).append( " -- " );
            optionsBuff.append( tempTip ).append( "\n\n" );
          } catch ( Exception ex ) {
            ex.printStackTrace();
          }
        }
      }

      if ( name.equals( "globalInfo" ) ) {
        if ( meth.getReturnType().equals( String.class ) ) {
          try {
            // Object args[] = { };
            String globalInfo = (String) ( meth.invoke( target, args ) );
            summary = globalInfo;
            int ci = globalInfo.indexOf( '.' );
            if ( ci != -1 ) {
              summary = globalInfo.substring( 0, ci + 1 );
            }
            String className = target.getClass().getName();
            className = className.substring( className.lastIndexOf( "." ) + 1 );
            helpText = new StringBuilder( "NAME\n" );
            helpText.append( className ).append( "\n\n" );
            helpText.append( "SYNOPSIS\n" ).append( globalInfo ).append( "\n\n" );

            // TODO full description...

          } catch ( Exception ex ) {
            ex.printStackTrace();
          }
        }
      }
    }

    List<String> results = new ArrayList<>();
    results.add( summary != null ? summary : "" );
    results.add( helpText != null ? helpText.toString() : "" );

    return results;
  }

  /**
   * Checks for the presence of a particular attribute type in the supplied Instances
   *
   * @param data the Instances to check for the presence of a particular type of attribute
   * @param type the type of attribute to check for
   * @param ignoreClass true if the class attribute (if set) should be ignored when checking
   * @return true if the specified attribute type exists in the Instances
   */
  public static boolean checkForAttributeType( Instances data, int type, boolean ignoreClass ) {
    if ( !ignoreClass ) {
      return data.checkForAttributeType( type );
    }

    for ( int i = 0; i < data.numAttributes(); i++ ) {
      if ( i != data.classIndex() ) {
        if ( data.attribute( i ).type() == type ) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Converts a map of filter configurations to a list of instantiated filters.
   *
   * @param configs
   * @return
   * @throws Exception
   */
  public static List<Filter> filterConfigsToList( Map<String, String> configs ) throws Exception {
    List<Filter> result = new ArrayList<>();

    for ( Map.Entry<String, String> e : configs.entrySet() ) {
      String filterName = e.getKey();
      String[] options = Utils.splitOptions( e.getValue() );

      Filter f = (Filter) Utils.forName( Filter.class, filterName, options );
      result.add( f );
    }

    return result;
  }

  /**
   * Converts a list of Filters to a map of filter configurations
   *
   * @param filterList the list of filters to convert
   * @return a map of filter configurations
   */
  public static Map<String, String> filterListToConfigs( List<Filter> filterList ) {
    Map<String, String> configs = new LinkedHashMap<>();

    for ( Filter f : filterList ) {
      String key = f.getClass().getCanonicalName();
      String options = Utils.joinOptions( f.getOptions() );
      configs.put( key, options );
    }

    return configs;
  }
}
