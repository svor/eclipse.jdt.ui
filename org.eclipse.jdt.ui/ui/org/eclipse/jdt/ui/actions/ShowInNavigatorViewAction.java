/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.actions;

import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ISetSelectionTarget;
import org.eclipse.ui.views.navigator.ResourceNavigator;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.OpenActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

/**
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.0
 */
public class ShowInNavigatorViewAction extends SelectionDispatchAction {

	private JavaEditor fEditor;
	
	/**
	 * Creates a new <code>ShowInNavigatorViewAction</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public ShowInNavigatorViewAction(UnifiedSite site) {
		super(site);
		setText(ActionMessages.getString("ShowInNavigatorView.label")); //$NON-NLS-1$
	}

	/**
	 * Creates a new <code>ShowInNavigatorViewAction</code>.
	 * <p>
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * </p>
	 */
	public ShowInNavigatorViewAction(JavaEditor editor) {
		this(UnifiedSite.create(editor.getEditorSite()));
		fEditor= editor;
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void selectionChanged(ITextSelection selection) {
		setEnabled(fEditor != null);
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(getResource(getElement(selection)) != null); 
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			setEnabled(false);
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void run(ITextSelection selection) {
		IJavaElement element= SelectionConverter.codeResolveOrInputHandled(fEditor, 
			getShell(), getDialogTitle(), ActionMessages.getString("ShowInNavigatorView.dialog.message")); //$NON-NLS-1$
		if (element != null)
			run(element);
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void run(IStructuredSelection selection) {
		run(getElement(selection));
	}
	
	private void run(IJavaElement element) {
		try {
			IResource resource= getResource(element);
			if (resource == null)
				return;
			IWorkbenchPage page= getSite().getWorkbenchWindow().getActivePage();	
			IViewPart view= page.showView(IPageLayout.ID_RES_NAV);
			if (view instanceof ISetSelectionTarget) {
				ISelection selection= new StructuredSelection(resource);
				((ISetSelectionTarget)view).selectReveal(selection);
			}
		} catch(PartInitException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.getString("ShowInNavigatorView.error.activation_failed")); //$NON-NLS-1$
		} catch(JavaModelException e) {
			ExceptionHandler.handle(e, getShell(), getDialogTitle(), ActionMessages.getString("ShowInNavigatorView.error.conversion_failed")); //$NON-NLS-1$
		}
	}
	
	private IJavaElement getElement(IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;
		Object element= selection.getFirstElement();
		if (!(element instanceof IJavaElement))
			return null;
		return (IJavaElement)element;
	}
	
	private IResource getResource(IJavaElement element) throws JavaModelException {
		element= OpenActionUtil.getElementToOpen(element);
		if (element == null)
			return null;
		return element.getCorrespondingResource();
	}
	
	private static String getDialogTitle() {
		return ActionMessages.getString("ShowInNavigatorView.dialog.title"); //$NON-NLS-1$
	}	
}
