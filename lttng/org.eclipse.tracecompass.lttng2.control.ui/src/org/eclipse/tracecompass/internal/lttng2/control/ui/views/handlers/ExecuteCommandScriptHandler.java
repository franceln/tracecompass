/**********************************************************************
 * Copyright (c) 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Bernd Hufmann - Initial API and implementation
 **********************************************************************/
package org.eclipse.tracecompass.internal.lttng2.control.ui.views.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.tracecompass.internal.lttng2.control.ui.Activator;
import org.eclipse.tracecompass.internal.lttng2.control.ui.views.ControlView;
import org.eclipse.tracecompass.internal.lttng2.control.ui.views.dialogs.ISelectCommandScriptDialog;
import org.eclipse.tracecompass.internal.lttng2.control.ui.views.dialogs.TraceControlDialogFactory;
import org.eclipse.tracecompass.internal.lttng2.control.ui.views.messages.Messages;
import org.eclipse.tracecompass.internal.lttng2.control.ui.views.model.impl.TraceSessionGroup;
import org.eclipse.ui.IWorkbenchPage;

/**
 * <p>
 * Command handler implementation to execute commands of a command script.
 * </p>
 *
 * @author Bernd Hufmann
 */
public class ExecuteCommandScriptHandler extends BaseControlViewHandler {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    /**
     * The trace session group the command is to be executed on.
     */
    private TraceSessionGroup fSessionGroup = null;

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        fLock.lock();
        try {
            final TraceSessionGroup sessionGroup = fSessionGroup;

            // Open dialog box for the node name and address
            final ISelectCommandScriptDialog dialog = TraceControlDialogFactory.getInstance().getCommandScriptDialog();

            if (dialog.open() != Window.OK) {
                return null;
            }

            Job job = new Job(Messages.TraceControl_ExecuteScriptJob) {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    try {
                        sessionGroup.executeCommands(monitor, dialog.getCommands());
                    } catch (ExecutionException e) {
                        return new Status(IStatus.ERROR, Activator.PLUGIN_ID, Messages.TraceControl_CreateSessionFailure, e);
                    }
                    return Status.OK_STATUS;
                }
            };
            job.setUser(true);
            job.schedule();
        } finally {
            fLock.unlock();
        }
        return null;
    }

    @Override
    public boolean isEnabled() {

        // Get workbench page for the Control View
        IWorkbenchPage page = getWorkbenchPage();
        if (page == null) {
            return false;
        }

        TraceSessionGroup sessionGroup = null;

        // Check if the session group project is selected
        ISelection selection = page.getSelection(ControlView.ID);
        if (selection instanceof StructuredSelection) {
            Object element = ((StructuredSelection) selection).getFirstElement();
            sessionGroup = (element instanceof TraceSessionGroup) ? (TraceSessionGroup) element : null;
        }

        boolean isEnabled = sessionGroup != null;
        fLock.lock();
        try {
            fSessionGroup = null;
            if(isEnabled) {
                fSessionGroup = sessionGroup;
            }
        } finally {
            fLock.unlock();
        }
        return isEnabled;
    }
}
