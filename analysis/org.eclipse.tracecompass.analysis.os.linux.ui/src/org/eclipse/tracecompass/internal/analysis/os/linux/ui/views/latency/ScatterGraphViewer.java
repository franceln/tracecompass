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

import java.util.Iterator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.analysis.os.linux.core.latency.LatencyAnalysis;
import org.eclipse.tracecompass.analysis.os.linux.core.latency.LatencyAnalysisListener;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.segmentstore.core.treemap.TreeMapStore;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfWindowRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.linecharts.TmfCommonXLineChartViewer;
import org.swtchart.ILineSeries;
import org.swtchart.ILineSeries.PlotSymbolType;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.ISeriesSet;
import org.swtchart.LineStyle;

/**
 * Displays the latency analysis data in a scatter graph
 *
 * @author France Lapointe Nguyen
 * @since 1.0
 */
public class ScatterGraphViewer extends TmfCommonXLineChartViewer {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    /**
     * Listener to update the model with the latency analysis results once the
     * latency analysis is fully completed
     */
    private final class LatencyListener implements LatencyAnalysisListener {

        @Override
        public void onComplete(LatencyAnalysis activeAnalysis, ISegmentStore<ISegment> results) {
            // Only update the model if trace that was analyzed is active trace
            if (activeAnalysis.equals(getAnalysisModule())) {
                updateModel(results);
            }
        }
    }

    /**
     * Latency analysis results
     */
    private @Nullable ISegmentStore<ISegment> fTopData;

    /**
     * Latency analysis completion listener
     */
    private LatencyListener fListener;

    /**
     * Indicates whether graph has data but was not populated yet
     */
    private boolean dirty = false;

    /**
     * Current analysis module
     */
    private @Nullable LatencyAnalysis fAnalysisModule;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param parent
     *            parent composite
     * @param title
     *            name of the graph
     * @param xLabel
     *            name of the x axis
     * @param yLabel
     *            name of the y axis
     */
    public ScatterGraphViewer(Composite parent, String title, String xLabel, String yLabel) {
        super(parent, title, xLabel, yLabel);
        fListener = new LatencyListener();
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if (trace != null) {
            fAnalysisModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID);
        }
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Update the data in the graph
     *
     * @param dataInput
     *            new model
     */
    public void updateModel(@Nullable ISegmentStore<ISegment> dataInput) {
        fTopData = dataInput;
        // Set the current latency analysis module
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if (trace == null) {
            fAnalysisModule = null;
        }
        else {
            fAnalysisModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID);
        }
        if (fTopData == null) {
            clearContent();
        }
        dirty = true;
        // Update new window range
        TmfTimeRange currentRange = TmfTraceManager.getInstance().getCurrentTraceContext().getWindowRange();
        long currentStart = currentRange.getStartTime().normalize(0, ITmfTimestamp.NANOSECOND_SCALE).getValue();
        long currentEnd = currentRange.getEndTime().normalize(0, ITmfTimestamp.NANOSECOND_SCALE).getValue();
        setWindowRange(currentStart, currentEnd);
    }

    @Override
    protected void updateData(long start, long end, int nb, @Nullable IProgressMonitor monitor) {
        // Third parameter is not used by implementation
        if (dirty) {
            // Determine data that needs to be visible
            ISegmentStore<ISegment> data = getElementsStartingInRange(start, end);
            if (data == null) {
                return;
            }
            double[] xSeries = new double[(int) data.getNbElements()];
            double[] ySeries = new double[(int) data.getNbElements()];
            // For each visible latency, add start time to x value and duration
            // for y value
            Iterator<ISegment> modelIter = data.iterator();
            for (int i = 0; i < ySeries.length; i++) {
                if (modelIter.hasNext()) {
                    ISegment segment = modelIter.next();
                    xSeries[i] = segment.getStart() - start;
                    ySeries[i] = segment.getLength();
                }
            }
            setXAxis(xSeries);
            if (getSwtChart().isDisposed()) {
                return;
            }
            getSwtChart().updateLayout();
            setSeries("Latencies", ySeries); //$NON-NLS-1$
            updateDisplay();
            dirty = false;
        }
    }

    /**
     * Get elements that have a starting time higher or equal to the range start
     * and an end time lower or equal to the range end.
     *
     * @param rangeStart
     *            Start of the selection range
     * @param end
     *            End of the selection range
     * @return TreeMapStore<ISegment> ISegment starting in the selection range
     */
    private @Nullable ISegmentStore<ISegment> getElementsStartingInRange(final long rangeStart, final long rangeEnd) {
        TreeMapStore<ISegment> model = new TreeMapStore<>();
        if (fTopData != null) {
            for (ISegment segment : fTopData) {
                if (segment.getStart() > rangeEnd) {
                    break;
                }
                if (segment.getStart() >= rangeStart) {
                    model.addElement(segment);
                }
            }
        }
        if (model.getNbElements() == 0) {
            return null;
        }
        return model;
    }

    @Override
    protected void setWindowRange(final long windowStartTime, final long windowEndTime) {
        super.setWindowRange(windowStartTime, windowEndTime);
        dirty = true;
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                updateData(windowStartTime, windowEndTime, 0, new NullProgressMonitor());
            }
        });
    }

    @Override
    protected ILineSeries addSeries(@Nullable String seriesName) {
        ISeriesSet seriesSet = getSwtChart().getSeriesSet();
        ILineSeries series = (ILineSeries) seriesSet.createSeries(SeriesType.LINE, seriesName);
        series.setVisible(true);
        series.enableArea(false);
        series.setLineStyle(LineStyle.NONE);
        series.setSymbolType(PlotSymbolType.DIAMOND);
        return series;
    }

    /**
     * Set the data into the viewer. Will update model is analysis is completed
     * or run analysis if not completed
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
            updateModel(results);
            return;
        }
        updateModel(null);
        analysis.addListener(fListener);
        analysis.schedule();
    }

    // ------------------------------------------------------------------------
    // Signal handlers
    // ------------------------------------------------------------------------

    /**
     * @param signal
     *            Signal received when a different trace is selected
     */
    @Override
    @TmfSignalHandler
    public void traceSelected(@Nullable TmfTraceSelectedSignal signal) {
        if (signal == null) {
            return;
        }
        ITmfTrace trace = signal.getTrace();
        if (trace != null) {
            setData(TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID));
        }
    }

    /**
     * @param signal
     *            Signal received when trace is opened
     */
    @Override
    @TmfSignalHandler
    public void traceOpened(@Nullable TmfTraceOpenedSignal signal) {
        super.traceOpened(signal);
        if (signal == null) {
            return;
        }
        ITmfTrace trace = signal.getTrace();
        if (trace != null) {
            setData(TmfTraceUtils.getAnalysisModuleOfClass(trace, LatencyAnalysis.class, LatencyAnalysis.ID));
        }
    }

    /**
     * @param signal
     *            Signal received when last opened trace is closed
     */
    @Override
    @TmfSignalHandler
    public void traceClosed(@Nullable TmfTraceClosedSignal signal) {
        if (signal != null) {
            // Check if there is no more opened trace
            if (TmfTraceManager.getInstance().getActiveTrace() == null) {
                clearContent();
            }
        }
    }

    /**
     * @param signal
     *            Signal received when window range is updated
     */
    @Override
    @TmfSignalHandler
    public void windowRangeUpdated(@Nullable TmfWindowRangeUpdatedSignal signal) {
        if (signal == null) {
            return;
        }
        // Validate the time range
        TmfTimeRange range = signal.getCurrentRange();
        if (range == null) {
            return;
        }
        if (signal.getSource() != this) {
            // Update the time range
            setWindowRange(range.getStartTime().normalize(0, ITmfTimestamp.NANOSECOND_SCALE).getValue(), range.getEndTime().normalize(0, ITmfTimestamp.NANOSECOND_SCALE).getValue());
        }
    }

    /**
     * Get the current analysis module
     *
     * @return current analysis
     */
    public @Nullable LatencyAnalysis getAnalysisModule() {
        return fAnalysisModule;
    }
}