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
import org.eclipse.tracecompass.analysis.os.linux.core.latency.LatencyAnalysis;
import org.eclipse.tracecompass.analysis.os.linux.core.latency.LatencyAnalysisListener;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.segmentstore.core.treemap.TreeMapStore;
import org.eclipse.tracecompass.tmf.core.signal.TmfSelectionRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.Iterables;

/**
 * Latency selection table viewer.
 *
 * Displays the intersecting latencies at selected time or selected time range
 *
 * @author France Lapointe Nguyen
 * @since 1.1
 */
public class LatencyIntersectingTableViewer extends AbstractLatencyTableViewer {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    /**
     * Listener to update the model with the latency analysis results once the
     * latency analysis is fully completed
     */
    private final class LatencyListener implements LatencyAnalysisListener {
        @Override
        public void onComplete(LatencyAnalysis activeAnalysis, ISegmentStore<ISegment> data) {
            // Check if the active trace was changed while the analysis was
            // running
            if (activeAnalysis.equals(getAnalysisModule())) {
                fTopData = data;
                updateModel(getIntersectingData(TmfTraceManager.getInstance().getCurrentTraceContext().getSelectionRange()));
            }
        }
    }

    private @Nullable ISegmentStore<ISegment> fTopData;
    private LatencyListener fListener;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param tableViewer
     *            Table viewer of the view
     */
    public LatencyIntersectingTableViewer(TableViewer tableViewer) {
        super(tableViewer);
        fListener = new LatencyListener();
    }

    /**
     * Set the data into the viewer. Will update model with intersecting
     * elements if analysis is completed or run analysis if not completed
     *
     * @param analysis
     *            Latency analysis module
     */
    public void setData(@Nullable LatencyAnalysis analysis) {
        if (analysis == null) {
            updateModel(null);
            return;
        }
        ISegmentStore<ISegment> results = analysis.getResults();
        // If results are not null, then analysis is completed and model can be
        // updated
        if (results != null) {
            // Keep the data to determine intersecting elements
            fTopData = results;
            // Update model with intersecting data
            updateModel(getIntersectingData(TmfTraceManager.getInstance().getCurrentTraceContext().getSelectionRange()));
            return;
        }
        // If results are null, then analysis was not performed so add listener
        // and run analysis
        updateModel(null);
        analysis.addListener(fListener);
        analysis.schedule();
    }

    /**
     * Get all the latencies that are intersecting with the selection range.
     * Will return null if there is no intersecting latencies
     *
     * @param analysis
     *            Latency analysis module
     */
    private @Nullable TreeMapStore<ISegment> getIntersectingData(TmfTimeRange selectionRange) {
        ISegmentStore<ISegment> topData = fTopData;
        // No intersecting data if top data is null
        if (topData == null) {
            return null;
        }
        long start = selectionRange.getStartTime().getValue();
        long end = selectionRange.getEndTime().getValue();
        Iterable<ISegment> selection;
        if (start == end) {
            selection = topData.getIntersectingElements(start);
        }
        else {
            selection = topData.getIntersectingElements(start, end);
        }
        // Return null if size of selection is null
        if (Iterables.size(selection) == 0) {
            return null;
        }
        // Add selection to store and return data
        TreeMapStore<ISegment> data = new TreeMapStore<>();
        for (ISegment segment : selection) {
            if (segment != null) {
                data.addElement(segment);
            }
        }
        return data;
    }

    // ------------------------------------------------------------------------
    // Signal handlers
    // ------------------------------------------------------------------------

    /**
     * Signal handler for handling of the selected range signal.
     *
     * @param signal
     *            The TmfSelectionRangeUpdatedSignal
     */
    @TmfSignalHandler
    public void selectionRangeUpdated(TmfSelectionRangeUpdatedSignal signal) {
        if ((signal.getSource() != this) && (TmfTraceManager.getInstance().getActiveTrace() != null)) {
            updateModel(getIntersectingData(new TmfTimeRange(signal.getBeginTime(), signal.getEndTime())));
        }
    }

    /**
     * @param signal
     *            Different trace has been selected
     */
    @TmfSignalHandler
    public void traceSelected(TmfTraceSelectedSignal signal) {
        ITmfTrace trace = signal.getTrace();
        if (trace != null) {
            setData(TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID));
        }
    }

    /**
     * @param signal
     *            New trace is opened
     */
    @TmfSignalHandler
    public void traceOpened(TmfTraceOpenedSignal signal) {
        ITmfTrace trace = signal.getTrace();
        if (trace != null) {
            setData(TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID));
        }
    }
}
