/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jsp.launching;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.internal.core.stringsubstitution.IValueVariable;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.internal.launching.JavaLocalApplicationLaunchConfigurationDelegate;
import org.eclipse.jsp.JspUIPlugin;

/**
 * Launch delegate for a local Tomcat server
 */
public class TomcatLaunchDelegate extends JavaLocalApplicationLaunchConfigurationDelegate {

	/**
	 * Identifier for Tomcat launch configurations.
	 */
	public static final String ID_TOMCAT_LAUNCH_CONFIGURATION_TYPE = "org.eclipse.jsp.TomcatConfigurationType"; //$NON-NLS-1$
	
	/**
	 * Identifier for Tomcat classpath provider.
	 */
	public static final String ID_TOMCAT_CLASSPATH_PROVIDER = "org.eclipse.jsp.tomcatClasspathProvider"; //$NON-NLS-1$
		
	/**
	 * Launch configuration attribute - value is path to local installation of Tomcat.
	 * The path may be encoded in a launch variable.
	 */
	public static final String ATTR_CATALINA_HOME = "org.eclipse.jsp.CATALINA_HOME"; //$NON-NLS-1$
	
	/**
	 * Launch configuration attribute - value is a workspace relative path to the root
	 * folder of a web application.
	 */
	public static final String ATTR_WEB_APP_ROOT= "org.eclipse.jsp.WEB_APP_ROOT"; //$NON-NLS-1$	

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
		super.launch(configuration, mode, launch, monitor);
		
		// set default stratum
		IJavaDebugTarget target = (IJavaDebugTarget)launch.getDebugTarget();
		if (target != null) {
			// only available in debug mode
			target.setDefaultStratum("JSP"); //$NON-NLS-1$	\
		}
	}

	/**
	 * Constructs a new launch delegate
	 */
	public TomcatLaunchDelegate() {
		super();
	}

	/**
	 * Returns the value of the <code>${catalina_home}</code> launch variable.
	 * 
	 * @return the value of the <code>${catalina_home}</code> launch variable
	 * @exception CoreException if the variable or value is undefined
	 */
	public static String getCatalinaHome() throws CoreException {
		IValueVariable variable = DebugPlugin.getDefault().getStringVariableManager().getValueVariable("catalina_home"); //$NON-NLS-1$
		IStatus err = null;
		if (variable == null) {
			err = new Status(IStatus.ERROR, JspUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(), 0, LaunchingMessages.getString("TomcatLaunchDelegate.9"), null); //$NON-NLS-1$
		} else {
			String home = variable.getValue();	
			if (home != null && home.length() > 0) {
				File file = new File(home);
				if (file.exists() && file.isDirectory()) {
					return home;
				} else {
					err = new Status(IStatus.ERROR, JspUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(), 0, MessageFormat.format(LaunchingMessages.getString("TomcatLaunchDelegate.7"), new String[]{home}), null); //$NON-NLS-1$
				}
			} else {
				err = new Status(IStatus.ERROR, JspUIPlugin.getDefault().getDescriptor().getUniqueIdentifier(), 0, LaunchingMessages.getString("TomcatLaunchDelegate.8"), null); //$NON-NLS-1$
			}
		}
		throw new CoreException(err);
	}
}
