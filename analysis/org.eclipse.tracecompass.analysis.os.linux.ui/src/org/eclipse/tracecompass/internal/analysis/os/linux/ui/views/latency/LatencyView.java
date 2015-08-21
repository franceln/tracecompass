/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   France Lapointe Nguyen - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.latency;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;

/**
 * View for the latency analysis
 *
 * @author France Lapointe Nguyen
 */
public class LatencyView extends TmfView {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    /** The view's ID */
    public static final String ID = "org.eclipse.tracecompass.analysis.os.linux.views.latency"; //$NON-NLS-1$

    private @Nullable LatencyTableViewer fTableViewer;
    private @Nullable LatencyIntersectingTableViewer fIntersecTableViewer;
    private @Nullable ScatterGraphViewer fScatterGraph;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     */
    public LatencyView() {
        super(ID);
    }

    // ------------------------------------------------------------------------
    // ViewPart
    // ------------------------------------------------------------------------

    @Override
    public void createPartControl(@Nullable Composite parent) {
        SashForm sf = new SashForm(parent, SWT.NONE);
        TableViewer tableViewer = new TableViewer(sf, SWT.FULL_SELECTION | SWT.VIRTUAL);
        fTableViewer = new LatencyTableViewer(tableViewer);
        TableViewer intersecTableViewer = new TableViewer(sf, SWT.FULL_SELECTION | SWT.VIRTUAL);
        fIntersecTableViewer = new LatencyIntersectingTableViewer(intersecTableViewer);
        fScatterGraph = new ScatterGraphViewer(sf, NonNullUtils.nullToEmptyString(Messages.LatencyView_title), NonNullUtils.nullToEmptyString(Messages.LatencyView_xAxis),
                NonNullUtils.nullToEmptyString(Messages.LatencyView_yAxis));
        setInitialData();
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    @Override
    public void setFocus() {
        if (fTableViewer != null) {
            fTableViewer.getTableViewer().getControl().setFocus();
        }
        if (fIntersecTableViewer != null) {
            fIntersecTableViewer.getTableViewer().getControl().setFocus();
        }
        if (fScatterGraph != null) {
            fScatterGraph.getControl().setFocus();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (fTableViewer != null) {
            fTableViewer.dispose();
        }
        if (fIntersecTableViewer != null) {
            fIntersecTableViewer.dispose();
        }
        if (fScatterGraph != null) {
            fScatterGraph.dispose();
        }
    }

    /**
     * Set initial data into the viewer
     */
    private void setInitialData() {
        if (fTableViewer != null) {
            fTableViewer.setData(fTableViewer.getAnalysisModule());
        }
        if (fIntersecTableViewer != null) {
            fIntersecTableViewer.setData(fIntersecTableViewer.getAnalysisModule());
        }
        if (fScatterGraph != null) {
            fScatterGraph.setData(fScatterGraph.getAnalysisModule());
        }
    }
}