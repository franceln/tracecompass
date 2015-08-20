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
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.analysis.os.linux.core.latency.LatencyAnalysis;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.segmentstore.core.SegmentComparators;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestampFormat;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.table.TmfSimpleTableViewer;

/**
 * Abstract table for latency table viewer
 *
 * @author France Lapointe Nguyen
 */
public abstract class AbstractLatencyTableViewer extends TmfSimpleTableViewer {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    /**
     * Abstract class for the column label provider for the latency analysis
     * table viewer
     */
    private abstract class LatencyTableColumnLabelProvider extends ColumnLabelProvider {

        @Override
        public String getText(@Nullable Object input) {
            if (!(input instanceof ISegment)) {
                /* Doubles as a null check */
                return ""; //$NON-NLS-1$
            }
            return getTextForITimeRange((ISegment) input);
        }

        public abstract String getTextForITimeRange(ISegment input);
    }

    /**
     * Current latency analysis module
     */
    private @Nullable LatencyAnalysis fAnalysisModule = null;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Create a Latency Abstract Table Viewer
     *
     * @param table
     *            table viewer
     */
    public AbstractLatencyTableViewer(TableViewer table) {
        super(table);
        // Sort order of the content provider is by start time by default
        getTableViewer().setContentProvider(new LatencyContentProvider());
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if (trace != null) {
            fAnalysisModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID);
        }
        createColumns();
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Create columns for start time, end time and duration
     */
    private void createColumns() {
        createColumn(Messages.LatencyTableViewer_startTime, new LatencyTableColumnLabelProvider() {
            @Override
            public String getTextForITimeRange(ISegment input) {
                return NonNullUtils.nullToEmptyString(TmfTimestampFormat.getDefaulTimeFormat().format(input.getStart()));
            }
        }, SegmentComparators.INTERVAL_START_COMPARATOR);

        createColumn(Messages.LatencyTableViewer_endTime, new LatencyTableColumnLabelProvider() {
            @Override
            public String getTextForITimeRange(ISegment input) {
                return NonNullUtils.nullToEmptyString(TmfTimestampFormat.getDefaulTimeFormat().format(input.getEnd()));
            }
        }, SegmentComparators.INTERVAL_END_COMPARATOR);

        createColumn(Messages.LatencyTableViewer_duration, new LatencyTableColumnLabelProvider() {
            @Override
            public String getTextForITimeRange(ISegment input) {
                return NonNullUtils.nullToEmptyString(Long.toString(input.getLength()));
            }
        }, SegmentComparators.INTERVAL_LENGTH_COMPARATOR);
    }

    /**
     * Update the data in the table viewer
     *
     * @param dataInput
     *            New data input
     */
    public void updateModel(final @Nullable ISegmentStore<ISegment> dataInput) {
        final TableViewer tableViewer = getTableViewer();
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                if (!tableViewer.getTable().isDisposed()) {
                    ISegmentStore<ISegment> data = dataInput;
                    // Set the current latency analysis module
                    ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
                    if (trace == null) {
                        fAnalysisModule = null;
                    }
                    else {
                        fAnalysisModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID);
                    }
                    // Go to the top of the table
                    tableViewer.getTable().setTopIndex(0);
                    // Reset selected row
                    tableViewer.setSelection(StructuredSelection.EMPTY);
                    if (data == null) {
                        tableViewer.setInput(null);
                        tableViewer.setItemCount(0);
                        return;
                    }
                    tableViewer.setInput(data);
                    if (data.getNbElements() <= Integer.MAX_VALUE) {
                        tableViewer.setItemCount((int) data.getNbElements());
                    } else {
                        tableViewer.setItemCount(Integer.MAX_VALUE);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------------
    // Getters/Setters
    // ------------------------------------------------------------------------

    /**
     * @return Current latency analysis module
     */
    public @Nullable LatencyAnalysis getAnalysisModule() {
        return fAnalysisModule;
    }

    // ------------------------------------------------------------------------
    // Signal handlers
    // ------------------------------------------------------------------------

    /**
     * @param signal
     *            Last opened trace was closed
     */
    @TmfSignalHandler
    public void traceClosed(TmfTraceClosedSignal signal) {
        // Check if there is no more opened trace
        if (TmfTraceManager.getInstance().getActiveTrace() == null) {
            if (!getTableViewer().getTable().isDisposed()) {
                getTableViewer().setInput(null);
                refresh();
            }
        }
    }
}
