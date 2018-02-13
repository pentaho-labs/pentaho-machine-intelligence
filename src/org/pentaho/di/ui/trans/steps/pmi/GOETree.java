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

package org.pentaho.di.ui.trans.steps.pmi;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.pentaho.di.core.Const;
import org.pentaho.di.ui.trans.step.BaseStepDialog;
import weka.core.ClassDiscovery;
import weka.core.OptionHandler;
import weka.core.PluginManager;
import weka.core.Utils;
import weka.core.logging.Logger;
import weka.gui.GenericPropertiesCreator;
import weka.gui.HierarchyPropertyParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static weka.gui.GenericObjectEditor.sortClassesByRoot;

/**
 * Implements a tree dialog for selecting an object to edit
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class GOETree extends Dialog {

  protected static boolean s_goeInitialized;

  protected Shell m_parent;
  protected Shell m_shell;

  protected int m_returnValue;
  protected String m_baseType;

  protected Object m_selectedTreeObject;

  protected Control m_lastControl;

  protected List<TreeItem> m_leafNodes = new ArrayList<>();

  public GOETree( Shell shell, int i, String baseType ) throws Exception {
    super( shell, i );
    initGOEProps();

    m_parent = shell;
    m_baseType = baseType;
  }

  public int open() {
    Display display = m_parent.getDisplay();
    m_shell = new Shell( m_parent, 2160 );

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = 5;
    formLayout.marginHeight = 5;
    m_shell.setLayout( formLayout );

    String truncatedBaseType = m_baseType.substring( m_baseType.lastIndexOf( "." ) + 1 );
    m_shell.setText( truncatedBaseType );

    createTree();

    m_shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent shellEvent ) {
        cancel();
      }
    } );

    // add buttons
    List<Button> buttons = new ArrayList<>();
    Button okBut = new Button( m_shell, SWT.PUSH );
    okBut.setText( "OK" );
    // m_props.setLook( okBut );
    okBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        ok();
      }
    } );
    buttons.add( okBut );

    Button cancelBut = new Button( m_shell, SWT.PUSH );
    cancelBut.setText( "Cancel" );
    // m_props.setLook( cancelBut );
    cancelBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        cancel();
      }
    } );
    buttons.add( cancelBut );

    BaseStepDialog.positionBottomButtons( m_shell, buttons.toArray( new Button[buttons.size()] ), 4, m_lastControl );

    //m_shell.layout();
    // m_shell.pack();
    m_shell.setSize( 300, 330 );
    m_shell.open();

    while ( !m_shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }

    return m_returnValue;
  }

  public Object getSelectedTreeObject() {
    return m_selectedTreeObject;
  }

  private void cancel() {
    m_returnValue = SWT.CANCEL;
    m_shell.dispose();
  }

  private void ok() {
    m_returnValue = SWT.OK;
    m_shell.dispose();
  }

  @SuppressWarnings( "unchecked" ) protected void createTree() {
    try {
      Map<String, Object> goeT = createGOETreeForBaseType( m_baseType );

      Tree tree = new Tree( m_shell, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL );
      FormData fd = new FormData();
      fd.top = new FormAttachment( 0, 0 );
      fd.left = new FormAttachment( 0, 0 );
      fd.right = new FormAttachment( 100, -4 );
      fd.bottom = new FormAttachment( 100, -50 );
      tree.setLayoutData( fd );
      tree.setSize( 290, 280 );

      // setup root
      String rootName = (String) goeT.get( "text" );
      if ( !Const.isEmpty( rootName ) ) {
        TreeItem root = createTreeNode( null, tree, goeT );

        // process children
        List<Map<String, Object>> nodeList = (List<Map<String, Object>>) goeT.get( "nodes" );
        if ( nodeList != null && nodeList.size() > 0 ) {
          processChildren( root, nodeList );
        }
      }

      // show all leaves
      for ( TreeItem i : m_leafNodes ) {
        tree.showItem( i );
      }

      tree.addSelectionListener( new SelectionAdapter() {
        @Override public void widgetSelected( SelectionEvent selectionEvent ) {
          super.widgetSelected( selectionEvent );
          Widget selected = selectionEvent.item;
          if ( selected instanceof GOETreeItemLeaf ) {
            try {
              m_selectedTreeObject = ( (GOETreeItemLeaf) selected ).instantiateLeafValue();
              // TODO close dialog here?
            } catch ( Exception e ) {
              e.printStackTrace();
              // TODO popup error
            }
          } else {
            m_selectedTreeObject = null; // non-terminal node
          }
        }
      } );
      m_lastControl = tree;
    } catch ( Exception e ) {
      e.printStackTrace();
      // TODO popup error
    }
  }

  @SuppressWarnings( "unchecked" )
  protected void processChildren( TreeItem parent, List<Map<String, Object>> children ) {
    for ( Map<String, Object> child : children ) {
      TreeItem childT = createTreeNode( parent, null, child );

      List<Map<String, Object>> nodeList = (List<Map<String, Object>>) child.get( "nodes" );
      if ( nodeList != null && nodeList.size() > 0 ) {
        processChildren( childT, nodeList );
      }
    }
  }

  protected TreeItem createTreeNode( TreeItem parent, Tree tree, Map<String, Object> sourceNode ) {
    String nodeName = (String) sourceNode.get( "text" );
    Boolean isLeaf = (Boolean) sourceNode.get( "isLeaf" );
    if ( isLeaf == null ) {
      isLeaf = false;
    }
    String schemeName = (String) sourceNode.get( "fullSpec" );
    if ( schemeName != null ) {
      try {
        schemeName = Utils.splitOptions( schemeName )[0];
      } catch ( Exception e ) {
        e.printStackTrace();
      }
    }

    TreeItem
        node =
        tree != null ? new TreeItem( tree, 0 ) :
            ( isLeaf && !Const.isEmpty( schemeName ) ? new GOETreeItemLeaf( parent, 0, schemeName ) :
                new TreeItem( parent, 0 ) );
    node.setText( nodeName );

    if ( isLeaf && !Const.isEmpty( schemeName ) ) {
      m_leafNodes.add( node );
    }

    return node;
  }

  protected Map<String, Object> createGOETreeForBaseType( String baseType ) throws Exception {

    Map<String, HierarchyPropertyParser> hpps = getClassesFromProperties( baseType );

    Map<String, Object> superRoot = null;
    if ( hpps.size() > 1 ) {
      superRoot = new LinkedHashMap<>();
      superRoot.put( "id", "root" );
      superRoot.put( "text", "root" );
      superRoot.put( "isLeaf", false );
    }

    for ( Map.Entry<String, HierarchyPropertyParser> e : hpps.entrySet() ) {
      HierarchyPropertyParser hpp = e.getValue();

      hpp.goToRoot();
      Map<String, Object> root = createNode( hpp );
      addChildrenToTree( root, hpp );

      if ( superRoot == null ) {
        superRoot = root;
      } else {
        addNodeToNodelist( superRoot, root );
      }
    }

    return superRoot;
  }

  protected void addChildrenToTree( Map<String, Object> tree, HierarchyPropertyParser hpp ) throws Exception {
    for ( int i = 0; i < hpp.numChildren(); i++ ) {
      hpp.goToChild( i );
      Map<String, Object> child = createNode( hpp );
      addNodeToNodelist( tree, child );

      addChildrenToTree( child, hpp );
      hpp.goToParent();
    }
  }

  protected Map<String, Object> createNode( HierarchyPropertyParser hpp ) {
    Map<String, Object> result = new LinkedHashMap<>();
    String fullSchemeName = hpp.fullValue();
    String shortName = hpp.getValue();
    String fullSpec = fullSchemeName;

    if ( hpp.isLeafReached() ) {
      try {
        Object instantiated = Utils.forName( null, fullSchemeName, null );
        if ( instantiated instanceof OptionHandler ) {
          // get default settings
          fullSpec += " " + Utils.joinOptions( ( (OptionHandler) instantiated ).getOptions() );
        }
      } catch ( Exception ex ) {
        ex.printStackTrace();
      }
    }

    result.put( "id", fullSchemeName );
    result.put( "text", shortName );
    result.put( "isLeaf", hpp.isLeafReached() );
    result.put( "fullSpec", fullSpec );
    if ( hpp.isLeafReached() ) {
      result.put( "nodes", new ArrayList<Map<String, Object>>() );
    }

    return result;
  }

  @SuppressWarnings( "unchecked" )
  protected void addNodeToNodelist( Map<String, Object> root, Map<String, Object> toAdd ) {
    List<Map<String, Object>> nodeList = (List<Map<String, Object>>) root.get( "nodes" );

    if ( nodeList == null ) {
      nodeList = new ArrayList<>();
      root.put( "nodes", nodeList );
    }

    nodeList.add( toAdd );
  }

  protected Map<String, HierarchyPropertyParser> getClassesFromProperties( String className ) {
    Map<String, HierarchyPropertyParser> hpps = new HashMap<>();

    Set<String> cls = PluginManager.getPluginNamesOfType( className );
    if ( cls == null ) {
      return hpps;
    }
    List<String> toSort = new ArrayList<String>( cls );
    Collections.sort( toSort, new ClassDiscovery.StringCompare() );

    StringBuilder b = new StringBuilder();
    for ( String s : toSort ) {
      b.append( s ).append( "," );
    }
    String listS = b.substring( 0, b.length() - 1 );
    // Hashtable typeOptions =
    // sortClassesByRoot(EDITOR_PROPERTIES.getProperty(className));
    Hashtable<String, String> typeOptions = sortClassesByRoot( listS );
    if ( typeOptions == null ) {
      /*
       * System.err.println("Warning: No configuration property found in\n" +
       * PROPERTY_FILE + "\n" + "for " + className);
       */
    } else {
      try {
        Enumeration<String> enm = typeOptions.keys();
        while ( enm.hasMoreElements() ) {
          String root = enm.nextElement();
          String typeOption = typeOptions.get( root );
          HierarchyPropertyParser hpp = new HierarchyPropertyParser();
          hpp.build( typeOption, ", " );
          hpps.put( root, hpp );
        }
      } catch ( Exception ex ) {
        Logger.log( weka.core.logging.Logger.Level.WARNING, "Invalid property: " + typeOptions );
      }
    }
    return hpps;
  }

  protected static void initGOEProps() throws Exception {
    if ( !s_goeInitialized ) {
      s_goeInitialized = true;
      Properties GOEProps = GenericPropertiesCreator.getGlobalOutputProperties();
      if ( GOEProps == null ) {
        GenericPropertiesCreator creator = new GenericPropertiesCreator();
        if ( creator.useDynamic() ) {
          creator.execute( false );
          GOEProps = creator.getOutputProperties();
        } else {
          GOEProps = Utils.readProperties( "weka/gui/GenericObjectEditor.props" );
        }
      }
    }
  }

  protected static class GOETreeItemLeaf extends TreeItem {

    protected String m_schemeName = "";

    public GOETreeItemLeaf( TreeItem parent, int style, String fullSpec ) {
      super( parent, style );
      setFullSpec( fullSpec );
    }

    public String getFullSpec() {
      return m_schemeName;
    }

    public void setFullSpec( String fullSpec ) {
      m_schemeName = fullSpec;
    }

    public Object instantiateLeafValue() throws Exception {
      if ( m_schemeName == null || m_schemeName.length() == 0 ) {
        return null;
      }

      return Utils.forName( null, m_schemeName, null );
    }

    @Override protected void checkSubclass() {
      // allow subclass
    }
  }
}
