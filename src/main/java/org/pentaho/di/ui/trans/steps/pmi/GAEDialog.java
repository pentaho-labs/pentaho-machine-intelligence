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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.ui.core.PropsUI;
import weka.core.OptionHandler;
import weka.core.Utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements an SWT version of Weka's GenericArrayEditor. This version only handles Object array elements.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class GAEDialog extends Dialog {

  protected PropsUI m_props;
  protected Shell m_parent;
  protected Shell m_shell;

  protected Button m_chooseBut;
  protected Button m_addBut;
  protected Label m_currentObjectLabel;

  protected Button m_deleteBut;
  protected Button m_editBut;
  protected Button m_upBut;
  protected Button m_downBut;

  protected org.eclipse.swt.widgets.List m_arrayList;

  protected VariableSpace m_vars;
  protected Control m_lastControl;

  protected Object m_arrayToEdit;
  protected Class<?> m_arrayElementClassType;
  protected String m_currentObjectConfig; // the currently selected object's config to display in the label at the top
  protected int m_returnValue;

  public GAEDialog( Shell shell, int i, Object arrayToEdit, Class<?> arrayElementClassType, VariableSpace vars )
      throws Exception {
    super( shell, i );

    m_arrayToEdit = arrayToEdit;
    m_arrayElementClassType = arrayElementClassType;
    m_parent = shell;
    m_vars = vars;
    m_props = PropsUI.getInstance();
  }

  public int open() {
    Display display = m_parent.getDisplay();
    m_shell = new Shell( m_parent, 2160 );
    m_props.setLook( m_shell );
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = 5;
    formLayout.marginHeight = 5;
    m_shell.setLayout( formLayout );

    String title = m_arrayElementClassType.toString();
    title = title.substring( title.lastIndexOf( '.' ) + 1 );
    m_shell.setText( title );

    buildList();

    m_shell.addShellListener( new ShellAdapter() {
      @Override public void shellClosed( ShellEvent shellEvent ) {
        cancel();
      }
    } );

    m_shell.layout();
    m_shell.pack();
    m_shell.open();

    while ( !m_shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }

    return m_returnValue;
  }

  public Object getArray() {
    return m_arrayToEdit;
  }

  private void cancel() {
    m_returnValue = SWT.CANCEL;
    this.dispose( false );
  }

  public void dispose( boolean updateArray ) {
    if ( updateArray ) {
      String[] items = m_arrayList.getItems();

      if ( Array.getLength( m_arrayToEdit ) != items.length ) {
        m_arrayToEdit = Array.newInstance( m_arrayElementClassType, items.length );
      }

      try {
        for ( int i = 0; i < items.length; i++ ) {
          String element = items[i];
          String[] parts = Utils.splitOptions( element );
          String clName = parts[0];
          parts[0] = "";
          Object toSet = Utils.forName( null, clName, parts );
          Array.set( m_arrayToEdit, i, toSet );
        }
      } catch ( Exception ex ) {
        ex.printStackTrace();
      }
    }
    m_shell.dispose();
  }

  protected void buildList() {
    int numElements = Array.getLength( m_arrayToEdit );
    final List<String> elements = new ArrayList<>();
    for ( int i = 0; i < numElements; i++ ) {
      Object entry = Array.get( m_arrayToEdit, i );
      String clazz = entry.getClass().getCanonicalName();
      String opts = Utils.joinOptions( ( (OptionHandler) entry ).getOptions() );
      elements.add( clazz + " " + opts );
    }
    m_currentObjectConfig = numElements > 0 ? elements.get( 0 ) : "<none>";
    String labelText = m_currentObjectConfig;
    if ( labelText.indexOf( ' ' ) > 0 ) {
      labelText = labelText.substring( 0, labelText.indexOf( ' ' ) );
    }

    FormData fd = new FormData();
    m_currentObjectLabel = new Label( m_shell, SWT.RIGHT );
    m_props.setLook( m_currentObjectLabel );
    m_currentObjectLabel.setText( labelText );
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 50, 0 );
    fd.top = new FormAttachment( 0, 0 );
    m_currentObjectLabel.setLayoutData( fd );

    m_chooseBut = new Button( m_shell, SWT.PUSH );
    m_props.setLook( m_chooseBut );
    m_chooseBut.setText( "Choose..." );
    fd = new FormData();
    fd.left = new FormAttachment( m_currentObjectLabel, 4 );
    fd.right = new FormAttachment( 75, 0 );
    fd.top = new FormAttachment( 0, 0 );
    m_chooseBut.setLayoutData( fd );

    m_chooseBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        try {
          m_chooseBut.setEnabled( false );
          m_addBut.setEnabled( false );
          m_deleteBut.setEnabled( false );
          m_editBut.setEnabled( false );
          m_upBut.setEnabled( false );
          m_downBut.setEnabled( false );
          GOETree
              treeDialog =
              new GOETree( getParent(), SWT.OK | SWT.CANCEL, m_arrayElementClassType.getCanonicalName() );
          int result = treeDialog.open();
          if ( result == SWT.OK ) {
            Object selectedTreeValue = treeDialog.getSelectedTreeObject();
            if ( selectedTreeValue != null ) {
              m_currentObjectConfig = selectedTreeValue.getClass().getCanonicalName() + " ";
              m_currentObjectConfig += Utils.joinOptions( ( (OptionHandler) selectedTreeValue ).getOptions() );
              String labelText = m_currentObjectConfig.substring( 0, m_currentObjectConfig.indexOf( ' ' ) );
              m_currentObjectLabel.setText( labelText );
            }
          }
        } catch ( Exception ex ) {
          // TODO popup error dialog
          ex.printStackTrace();
        } finally {
          m_chooseBut.setEnabled( true );
          m_addBut.setEnabled( true );
          m_deleteBut.setEnabled( true );
          m_editBut.setEnabled( true );
          m_upBut.setEnabled( true );
          m_downBut.setEnabled( true );
        }
      }
    } );

    m_addBut = new Button( m_shell, SWT.PUSH );
    m_props.setLook( m_addBut );
    m_addBut.setText( "Add" );
    fd = new FormData();
    fd.left = new FormAttachment( m_chooseBut, 4 );
    fd.top = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    m_addBut.setLayoutData( fd );

    m_addBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        try {
          m_chooseBut.setEnabled( false );
          m_addBut.setEnabled( false );
          m_deleteBut.setEnabled( false );
          m_editBut.setEnabled( false );
          m_upBut.setEnabled( false );
          m_downBut.setEnabled( false );

          // insert before this index (if set)
          int selectedListIndex = m_arrayList.getSelectionIndex();
          if ( selectedListIndex < 0 ) {
            m_arrayList.add( m_currentObjectConfig );
          } else {
            m_arrayList.add( m_currentObjectConfig, selectedListIndex );
          }
        } catch ( Exception ex ) {
          // TODO popup error dialog
          ex.printStackTrace();
        } finally {
          m_chooseBut.setEnabled( true );
          m_addBut.setEnabled( true );
          m_deleteBut.setEnabled( true );
          m_editBut.setEnabled( true );
          m_upBut.setEnabled( true );
          m_downBut.setEnabled( true );
        }
      }
    } );

    m_arrayList = new org.eclipse.swt.widgets.List( m_shell, SWT.BORDER | SWT.V_SCROLL );
    m_arrayList.setItems( elements.toArray( new String[elements.size()] ) );
    m_props.setLook( m_arrayList );
    fd = new FormData();
    fd.top = new FormAttachment( m_chooseBut, 4 );
    fd.left = new FormAttachment( 0, 0 );
    fd.right = new FormAttachment( 100, 0 );
    fd.height = m_arrayList.getItemHeight() * 5;
    m_arrayList.setLayoutData( fd );

    List<Button> buttons = new ArrayList<>();
    m_deleteBut = new Button( m_shell, SWT.PUSH );
    m_props.setLook( m_deleteBut );
    m_deleteBut.setText( "Delete" );
    buttons.add( m_deleteBut );

    m_deleteBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        try {
          m_chooseBut.setEnabled( false );
          m_addBut.setEnabled( false );
          m_deleteBut.setEnabled( false );
          m_editBut.setEnabled( false );
          m_upBut.setEnabled( false );
          m_downBut.setEnabled( false );

          int index = m_arrayList.getSelectionIndex();
          if ( index >= 0 ) {
            m_arrayList.remove( index );
          }
        } catch ( Exception ex ) {
          ex.printStackTrace();
        } finally {
          m_chooseBut.setEnabled( true );
          m_addBut.setEnabled( true );
          m_deleteBut.setEnabled( true );
          m_editBut.setEnabled( true );
          m_upBut.setEnabled( true );
          m_downBut.setEnabled( true );
        }
      }
    } );

    m_editBut = new Button( m_shell, SWT.PUSH );
    m_props.setLook( m_editBut );
    m_editBut.setText( "Edit..." );
    buttons.add( m_editBut );

    m_editBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        try {
          m_chooseBut.setEnabled( false );
          m_addBut.setEnabled( false );
          m_deleteBut.setEnabled( false );
          m_editBut.setEnabled( false );
          m_upBut.setEnabled( false );
          m_downBut.setEnabled( false );

          int index = m_arrayList.getSelectionIndex();
          if ( index >= 0 ) {
            String configToEdit = m_arrayList.getItem( index );
            String[] parts = Utils.splitOptions( configToEdit );
            String className = parts[0];
            parts[0] = "";
            Object objectToEdit = Utils.forName( null, className, parts );
            GOEDialog dialog = new GOEDialog( GAEDialog.this.getParent(), SWT.OK | SWT.CANCEL, objectToEdit, m_vars );
            dialog.open();

            String newConfig = className + " " + Utils.joinOptions( ( (OptionHandler) objectToEdit ).getOptions() );
            m_arrayList.setItem( index, newConfig );
          }
        } catch ( Exception ex ) {
          ex.printStackTrace();
        } finally {
          m_chooseBut.setEnabled( true );
          m_addBut.setEnabled( true );
          m_deleteBut.setEnabled( true );
          m_editBut.setEnabled( true );
          m_upBut.setEnabled( true );
          m_downBut.setEnabled( true );
        }
      }
    } );

    m_upBut = new Button( m_shell, SWT.PUSH );
    m_props.setLook( m_upBut );
    m_upBut.setText( "Up" );
    buttons.add( m_upBut );
    m_upBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        try {
          m_chooseBut.setEnabled( false );
          m_addBut.setEnabled( false );
          m_deleteBut.setEnabled( false );
          m_editBut.setEnabled( false );
          m_upBut.setEnabled( false );
          m_downBut.setEnabled( false );

          int index = m_arrayList.getSelectionIndex();
          if ( index >= 1 ) {
            String configToMove = m_arrayList.getItem( index );
            String configToSwapWith = m_arrayList.getItem( index - 1 );
            m_arrayList.setItem( index - 1, configToMove );
            m_arrayList.setItem( index, configToSwapWith );
            m_arrayList.setSelection( index - 1 );
          }
        } catch ( Exception ex ) {
          ex.printStackTrace();
        } finally {
          m_chooseBut.setEnabled( true );
          m_addBut.setEnabled( true );
          m_deleteBut.setEnabled( true );
          m_editBut.setEnabled( true );
          m_upBut.setEnabled( true );
          m_downBut.setEnabled( true );
        }
      }
    } );

    m_downBut = new Button( m_shell, SWT.PUSH );
    m_props.setLook( m_downBut );
    m_downBut.setText( "Down" );
    buttons.add( m_downBut );

    m_downBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        try {
          m_chooseBut.setEnabled( false );
          m_addBut.setEnabled( false );
          m_deleteBut.setEnabled( false );
          m_editBut.setEnabled( false );
          m_upBut.setEnabled( false );
          m_downBut.setEnabled( false );

          int index = m_arrayList.getSelectionIndex();
          if ( index < m_arrayList.getItemCount() - 1 ) {
            String configToMove = m_arrayList.getItem( index );
            String configToSwapWith = m_arrayList.getItem( index + 1 );
            m_arrayList.setItem( index + 1, configToMove );
            m_arrayList.setItem( index, configToSwapWith );
            m_arrayList.setSelection( index + 1 );
          }
        } catch ( Exception ex ) {
          ex.printStackTrace();
        } finally {
          m_chooseBut.setEnabled( true );
          m_addBut.setEnabled( true );
          m_deleteBut.setEnabled( true );
          m_editBut.setEnabled( true );
          m_upBut.setEnabled( true );
          m_downBut.setEnabled( true );
        }
      }
    } );

    Button okBut = new Button( m_shell, SWT.PUSH );
    okBut.setText( "OK" );
    m_props.setLook( okBut );
    buttons.add( okBut );
    okBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        dispose( true );
      }
    } );

    Button cancelBut = new Button( m_shell, SWT.PUSH );
    cancelBut.setText( "Cancel" );
    m_props.setLook( cancelBut );
    buttons.add( cancelBut );
    cancelBut.addSelectionListener( new SelectionAdapter() {
      @Override public void widgetSelected( SelectionEvent selectionEvent ) {
        super.widgetSelected( selectionEvent );
        dispose( false );
      }
    } );

    BaseSupervisedPMIStepDialog
        .positionBottomButtons( m_shell, buttons.toArray( new Button[buttons.size()] ), 4, m_arrayList );
  }

  public List<String> getListContents() {
    return null;
  }
}
