/*****************************************************************************
 * Copyright (c) 2007, 2015 Intel Corporation, Ericsson, others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Intel Corporation - Initial API and implementation
 *   Ruslan A. Scherbakov, Intel - Initial API and implementation
 *   Alexander N. Alexeev, Intel - Add monitors statistics support
 *   Alvaro Sanchez-Leon - Adapted for TMF
 *   Patrick Tasse - Refactoring
 *   Geneviève Bastien - Add event links between entries
 *****************************************************************************/

package org.eclipse.tracecompass.tmf.ui.widgets.timegraph;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.tracecompass.internal.tmf.ui.Activator;
import org.eclipse.tracecompass.internal.tmf.ui.ITmfImageConstants;
import org.eclipse.tracecompass.internal.tmf.ui.Messages;
import org.eclipse.tracecompass.tmf.ui.signal.TmfTimeViewAlignmentInfo;
import org.eclipse.tracecompass.tmf.ui.views.ITmfTimeAligned;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.dialogs.TimeGraphLegend;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ILinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.ITimeDataProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeDataProviderCyclesConverter;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphColorScheme;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphScale;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphTooltipHandler;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.TimeFormat;

/**
 * Generic time graph viewer implementation
 *
 * @author Patrick Tasse, and others
 */
public class TimeGraphViewer implements ITimeDataProvider, SelectionListener {

    /** Constant indicating that all levels of the time graph should be expanded */
    public static final int ALL_LEVELS = AbstractTreeViewer.ALL_LEVELS;

    private static final int DEFAULT_NAME_WIDTH = 200;
    private static final int MIN_NAME_WIDTH = 6;
    private static final int MAX_NAME_WIDTH = 1000;
    private static final int DEFAULT_HEIGHT = 22;
    private static final String HIDE_ARROWS_KEY = "hide.arrows"; //$NON-NLS-1$
    private static final long DEFAULT_FREQUENCY = 1000000000L;
    private static final int H_SCROLLBAR_MAX = Integer.MAX_VALUE - 1;

    private long fMinTimeInterval;
    private ITimeGraphEntry fSelectedEntry;
    private long fBeginTime = SWT.DEFAULT; // The user-specified bounds start time
    private long fEndTime = SWT.DEFAULT; // The user-specified bounds end time
    private long fTime0 = SWT.DEFAULT; // The current window start time
    private long fTime1 = SWT.DEFAULT; // The current window end time
    private long fSelectionBegin = SWT.DEFAULT;
    private long fSelectionEnd = SWT.DEFAULT;
    private long fTime0Bound = SWT.DEFAULT; // The bounds start time
    private long fTime1Bound = SWT.DEFAULT; // The bounds end time
    private long fTime0ExtSynch = SWT.DEFAULT;
    private long fTime1ExtSynch = SWT.DEFAULT;
    private boolean fTimeRangeFixed;
    private int fNameWidthPref = DEFAULT_NAME_WIDTH;
    private int fMinNameWidth = MIN_NAME_WIDTH;
    private int fNameWidth;
    private Composite fDataViewer;

    private TimeGraphControl fTimeGraphCtrl;
    private TimeGraphScale fTimeScaleCtrl;
    private Slider fHorizontalScrollBar;
    private Slider fVerticalScrollBar;
    private TimeGraphColorScheme fColorScheme;
    private Object fInputElement;
    private ITimeGraphContentProvider fTimeGraphContentProvider;
    private ITimeGraphPresentationProvider fTimeGraphProvider;
    private ITimeDataProvider fTimeDataProvider = this;
    private TimeGraphTooltipHandler fToolTipHandler;

    private List<ITimeGraphSelectionListener> fSelectionListeners = new ArrayList<>();
    private List<ITimeGraphTimeListener> fTimeListeners = new ArrayList<>();
    private List<ITimeGraphRangeListener> fRangeListeners = new ArrayList<>();

    // Time format, using Epoch reference, Relative time format(default),
    // Number, or Cycles
    private TimeFormat fTimeFormat = TimeFormat.RELATIVE;
    // Clock frequency to use for Cycles time format
    private long fClockFrequency = DEFAULT_FREQUENCY;
    private int fBorderWidth = 0;
    private int fTimeScaleHeight = DEFAULT_HEIGHT;

    private Action fResetScaleAction;
    private Action fShowLegendAction;
    private Action fNextEventAction;
    private Action fPrevEventAction;
    private Action fNextItemAction;
    private Action fPreviousItemAction;
    private Action fZoomInAction;
    private Action fZoomOutAction;
    private Action fHideArrowsAction;
    private Action fFollowArrowFwdAction;
    private Action fFollowArrowBwdAction;

    private ListenerNotifier fListenerNotifier;

    private Composite fTimeAlignedComposite;

    private class ListenerNotifier extends Thread {
        private static final long DELAY = 400L;
        private static final long POLLING_INTERVAL = 10L;
        private long fLastUpdateTime = Long.MAX_VALUE;
        private boolean fSelectionChanged = false;
        private boolean fTimeRangeUpdated = false;
        private boolean fTimeSelected = false;

        @Override
        public void run() {
            while ((System.currentTimeMillis() - fLastUpdateTime) < DELAY) {
                try {
                    Thread.sleep(POLLING_INTERVAL);
                } catch (Exception e) {
                    return;
                }
            }
            Display.getDefault().asyncExec(new Runnable() {
                @Override
                public void run() {
                    if (fListenerNotifier != ListenerNotifier.this) {
                        return;
                    }
                    fListenerNotifier = null;
                    if (ListenerNotifier.this.isInterrupted() || fDataViewer.isDisposed()) {
                        return;
                    }
                    if (fSelectionChanged) {
                        fireSelectionChanged(fSelectedEntry);
                    }
                    if (fTimeRangeUpdated) {
                        fireTimeRangeUpdated(fTime0, fTime1);
                    }
                    if (fTimeSelected) {
                        fireTimeSelected(fSelectionBegin, fSelectionEnd);
                    }
                }
            });
        }

        public void selectionChanged() {
            fSelectionChanged = true;
            fLastUpdateTime = System.currentTimeMillis();
        }

        public void timeRangeUpdated() {
            fTimeRangeUpdated = true;
            fLastUpdateTime = System.currentTimeMillis();
        }

        public void timeSelected() {
            fTimeSelected = true;
            fLastUpdateTime = System.currentTimeMillis();
        }

        public boolean hasSelectionChanged() {
            return fSelectionChanged;
        }

        public boolean hasTimeRangeUpdated() {
            return fTimeRangeUpdated;
        }

        public boolean hasTimeSelected() {
            return fTimeSelected;
        }
    }

    /**
     * Standard constructor.
     * <p>
     * The default timegraph content provider accepts an ITimeGraphEntry[] as input element.
     *
     * @param parent
     *            The parent UI composite object
     * @param style
     *            The style to use
     */
    public TimeGraphViewer(Composite parent, int style) {
        createDataViewer(parent, style);
        fTimeGraphContentProvider = new TimeGraphContentProvider();
    }

    /**
     * Sets the timegraph content provider used by this timegraph viewer.
     *
     * @param timeGraphContentProvider
     *            the timegraph content provider
     */
    public void setTimeGraphContentProvider(ITimeGraphContentProvider timeGraphContentProvider) {
        fTimeGraphContentProvider = timeGraphContentProvider;
    }

    /**
     * Gets the timegraph content provider used by this timegraph viewer.
     *
     * @return the timegraph content provider
     */
    public ITimeGraphContentProvider getTimeGraphContentProvider() {
        return fTimeGraphContentProvider;
    }

    /**
     * Sets the timegraph presentation provider used by this timegraph viewer.
     *
     * @param timeGraphProvider
     *            the timegraph provider
     */
    public void setTimeGraphProvider(ITimeGraphPresentationProvider timeGraphProvider) {
        fTimeGraphProvider = timeGraphProvider;
        fTimeGraphCtrl.setTimeGraphProvider(timeGraphProvider);
        fToolTipHandler = new TimeGraphTooltipHandler(fTimeGraphProvider, fTimeDataProvider);
        fToolTipHandler.activateHoverHelp(fTimeGraphCtrl);
    }

    /**
     * Sets or clears the input for this time graph viewer.
     *
     * @param inputElement
     *            The input of this time graph viewer, or <code>null</code> if
     *            none
     */
    public void setInput(Object inputElement) {
        fInputElement = inputElement;
        ITimeGraphEntry[] input = fTimeGraphContentProvider.getElements(inputElement);
        fListenerNotifier = null;
        if (fTimeGraphCtrl != null) {
            setTimeRange(input);
            setTopIndex(0);
            fSelectionBegin = SWT.DEFAULT;
            fSelectionEnd = SWT.DEFAULT;
            fSelectedEntry = null;
            refreshAllData(input);
        }
    }

    /**
     * Gets the input for this time graph viewer.
     *
     * @return The input of this time graph viewer, or <code>null</code> if none
     */
    public Object getInput() {
        return fInputElement;
    }

    /**
     * Sets (or clears if null) the list of links to display on this combo
     *
     * @param links
     *            the links to display in this time graph combo
     */
    public void setLinks(List<ILinkEvent> links) {
        if (fTimeGraphCtrl != null) {
            fTimeGraphCtrl.refreshArrows(links);
        }
    }

    /**
     * Refresh the view
     */
    public void refresh() {
        ITimeGraphEntry[] input = fTimeGraphContentProvider.getElements(fInputElement);
        setTimeRange(input);
        refreshAllData(input);
    }

    /**
     * Callback for when the control is moved
     *
     * @param e
     *            The caller event
     */
    public void controlMoved(ControlEvent e) {
    }

    /**
     * Callback for when the control is resized
     *
     * @param e
     *            The caller event
     */
    public void controlResized(ControlEvent e) {
        resizeControls();
    }

    /**
     * @return The string representing the view type
     */
    protected String getViewTypeStr() {
        return "viewoption.threads"; //$NON-NLS-1$
    }

    int getMarginWidth() {
        return 0;
    }

    int getMarginHeight() {
        return 0;
    }

    void loadOptions() {
        fMinTimeInterval = 1;
        fSelectionBegin = SWT.DEFAULT;
        fSelectionEnd = SWT.DEFAULT;
        fNameWidth = Utils.loadIntOption(getPreferenceString("namewidth"), //$NON-NLS-1$
                fNameWidthPref, fMinNameWidth, MAX_NAME_WIDTH);
    }

    void saveOptions() {
        Utils.saveIntOption(getPreferenceString("namewidth"), fNameWidth); //$NON-NLS-1$
    }

    /**
     * Create a data viewer.
     *
     * @param parent
     *            Parent composite
     * @param style
     *            Style to use
     * @return The new data viewer
     */
    protected Control createDataViewer(Composite parent, int style) {
        loadOptions();
        fColorScheme = new TimeGraphColorScheme();
        fDataViewer = new Composite(parent, style) {
            @Override
            public void redraw() {
                fTimeScaleCtrl.redraw();
                fTimeGraphCtrl.redraw();
                super.redraw();
            }
        };
        GridLayout gl = new GridLayout(2, false);
        gl.marginHeight = fBorderWidth;
        gl.marginWidth = 0;
        gl.verticalSpacing = 0;
        gl.horizontalSpacing = 0;
        fDataViewer.setLayout(gl);

        fTimeAlignedComposite = new Composite(fDataViewer, style) {
            @Override
            public void redraw() {
                fDataViewer.redraw();
                super.redraw();
            }
        };
        GridLayout gl2 = new GridLayout(1, false);
        gl2.marginHeight = fBorderWidth;
        gl2.marginWidth = 0;
        gl2.verticalSpacing = 0;
        gl2.horizontalSpacing = 0;
        fTimeAlignedComposite.setLayout(gl2);
        fTimeAlignedComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        fTimeScaleCtrl = new TimeGraphScale(fTimeAlignedComposite, fColorScheme);
        fTimeScaleCtrl.setTimeProvider(fTimeDataProvider);
        fTimeScaleCtrl.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));
        fTimeScaleCtrl.setHeight(fTimeScaleHeight);
        fTimeScaleCtrl.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseScrolled(MouseEvent e) {
                fTimeGraphCtrl.zoom(e.count > 0);
            }
        });

        fTimeGraphCtrl = createTimeGraphControl(fTimeAlignedComposite, fColorScheme);

        fTimeGraphCtrl.setTimeProvider(this);
        fTimeGraphCtrl.setTimeGraphScale(fTimeScaleCtrl);
        fTimeGraphCtrl.addSelectionListener(this);
        fTimeGraphCtrl.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        fTimeGraphCtrl.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseScrolled(MouseEvent e) {
                adjustVerticalScrollBar();
            }
        });
        fTimeGraphCtrl.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.character == '+') {
                    zoomIn();
                } else if (e.character == '-') {
                    zoomOut();
                }
                adjustVerticalScrollBar();
            }
        });

        fVerticalScrollBar = new Slider(fDataViewer, SWT.VERTICAL | SWT.NO_FOCUS);
        fVerticalScrollBar.setLayoutData(new GridData(SWT.DEFAULT, SWT.FILL, false, true, 1, 1));
        fVerticalScrollBar.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setTopIndex(fVerticalScrollBar.getSelection());
            }
        });

        fHorizontalScrollBar = new Slider(fDataViewer, SWT.HORIZONTAL | SWT.NO_FOCUS);
        fHorizontalScrollBar.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));
        fHorizontalScrollBar.addListener(SWT.MouseWheel, new Listener() {
            @Override
            public void handleEvent(Event event) {
                if ((event.stateMask & SWT.MODIFIER_MASK) == SWT.CTRL) {
                    getTimeGraphControl().zoom(event.count > 0);
                } else {
                    getTimeGraphControl().horizontalScroll(event.count > 0);
                }
                // don't handle the immediately following SWT.Selection event
                event.doit = false;
            }
        });
        fHorizontalScrollBar.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event event) {
                int start = fHorizontalScrollBar.getSelection();
                long time0 = getTime0();
                long time1 = getTime1();
                long timeMin = getMinTime();
                long timeMax = getMaxTime();
                long delta = timeMax - timeMin;

                long range = time1 - time0;
                time0 = timeMin + Math.round(delta * ((double) start / H_SCROLLBAR_MAX));
                time1 = time0 + range;

                setStartFinishTimeNotify(time0, time1);
            }
        });

        Composite filler = new Composite(fDataViewer, SWT.NONE);
        GridData gd = new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false);
        gd.heightHint = fHorizontalScrollBar.getSize().y;
        filler.setLayoutData(gd);
        filler.setLayout(new FillLayout());

        fTimeGraphCtrl.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent event) {
                resizeControls();
            }
        });
        resizeControls();
        fDataViewer.update();
        adjustHorizontalScrollBar();
        adjustVerticalScrollBar();
        return fDataViewer;
    }

    /**
     * Dispose the view.
     */
    public void dispose() {
        saveOptions();
        fTimeGraphCtrl.dispose();
        fDataViewer.dispose();
        fColorScheme.dispose();
    }

    /**
     * Create a new time graph control.
     *
     * @param parent
     *            The parent composite
     * @param colors
     *            The color scheme
     * @return The new TimeGraphControl
     */
    protected TimeGraphControl createTimeGraphControl(Composite parent,
            TimeGraphColorScheme colors) {
        return new TimeGraphControl(parent, colors);
    }

    /**
     * Resize the controls
     */
    public void resizeControls() {
        Rectangle r = fDataViewer.getClientArea();
        if (r.isEmpty()) {
            return;
        }

        int width = r.width;
        if (fNameWidth > width - fMinNameWidth) {
            fNameWidth = width - fMinNameWidth;
        }
        if (fNameWidth < fMinNameWidth) {
            fNameWidth = fMinNameWidth;
        }
        adjustHorizontalScrollBar();
        adjustVerticalScrollBar();
    }

    /**
     * Recalculate the time bounds based on the time graph entries,
     * if the user-specified bound is set to SWT.DEFAULT.
     *
     * @param entries
     *            The root time graph entries in the model
     */
    public void setTimeRange(ITimeGraphEntry entries[]) {
        fTime0Bound = (fBeginTime != SWT.DEFAULT ? fBeginTime : fEndTime);
        fTime1Bound = (fEndTime != SWT.DEFAULT ? fEndTime : fBeginTime);
        if (fBeginTime != SWT.DEFAULT && fEndTime != SWT.DEFAULT) {
            return;
        }
        if (entries == null || entries.length == 0) {
            return;
        }
        if (fTime0Bound == SWT.DEFAULT) {
            fTime0Bound = Long.MAX_VALUE;
        }
        if (fTime1Bound == SWT.DEFAULT) {
            fTime1Bound = Long.MIN_VALUE;
        }
        for (ITimeGraphEntry entry : entries) {
            setTimeRange(entry);
        }
    }

    private void setTimeRange(ITimeGraphEntry entry) {
        if (fBeginTime == SWT.DEFAULT && entry.hasTimeEvents() && entry.getStartTime() != SWT.DEFAULT) {
            fTime0Bound = Math.min(entry.getStartTime(), fTime0Bound);
        }
        if (fEndTime == SWT.DEFAULT && entry.hasTimeEvents() && entry.getEndTime() != SWT.DEFAULT) {
            fTime1Bound = Math.max(entry.getEndTime(), fTime1Bound);
        }
        if (entry.hasChildren()) {
            for (ITimeGraphEntry child : entry.getChildren()) {
                setTimeRange(child);
            }
        }
    }

    /**
     * Set the time bounds to the provided values.
     *
     * @param beginTime
     *            The bounds begin time, or SWT.DEFAULT to use the input bounds
     * @param endTime
     *            The bounds end time, or SWT.DEFAULT to use the input bounds
     */
    public void setTimeBounds(long beginTime, long endTime) {
        fBeginTime = beginTime;
        fEndTime = endTime;
        fTime0Bound = (fBeginTime != SWT.DEFAULT ? fBeginTime : fEndTime);
        fTime1Bound = (fEndTime != SWT.DEFAULT ? fEndTime : fBeginTime);
        if (fTime0Bound > fTime1Bound) {
            // only possible if both are not default
            fBeginTime = endTime;
            fEndTime = beginTime;
            fTime0Bound = fBeginTime;
            fTime1Bound = fEndTime;
        }
        adjustHorizontalScrollBar();
    }

    /**
     * Recalculate the current time window when bounds have changed.
     */
    public void setTimeBounds() {
        if (!fTimeRangeFixed) {
            fTime0 = fTime0Bound;
            fTime1 = fTime1Bound;
        }
        fTime0 = Math.max(fTime0Bound, Math.min(fTime0, fTime1Bound));
        fTime1 = Math.max(fTime0Bound, Math.min(fTime1, fTime1Bound));
        if (fTime1 - fTime0 < fMinTimeInterval) {
            fTime1 = Math.min(fTime1Bound, fTime0 + fMinTimeInterval);
        }
    }

    /**
     * @param traces
     */
    private void refreshAllData(ITimeGraphEntry[] traces) {
        setTimeBounds();
        if (fSelectionBegin < fBeginTime) {
            fSelectionBegin = fBeginTime;
        } else if (fSelectionBegin > fEndTime) {
            fSelectionBegin = fEndTime;
        }
        if (fSelectionEnd < fBeginTime) {
            fSelectionEnd = fBeginTime;
        } else if (fSelectionEnd > fEndTime) {
            fSelectionEnd = fEndTime;
        }
        fTimeGraphCtrl.refreshData(traces);
        fTimeScaleCtrl.redraw();
        adjustVerticalScrollBar();
    }

    /**
     * Callback for when this view is focused
     */
    public void setFocus() {
        if (null != fTimeGraphCtrl) {
            fTimeGraphCtrl.setFocus();
        }
    }

    /**
     * Get the current focus status of this view.
     *
     * @return If the view is currently focused, or not
     */
    public boolean isInFocus() {
        return fTimeGraphCtrl.isInFocus();
    }

    /**
     * Get the view's current selection
     *
     * @return The entry that is selected
     */
    public ITimeGraphEntry getSelection() {
        return fTimeGraphCtrl.getSelectedTrace();
    }

    /**
     * Get the index of the current selection
     *
     * @return The index
     */
    public int getSelectionIndex() {
        return fTimeGraphCtrl.getSelectedIndex();
    }

    @Override
    public long getTime0() {
        return fTime0;
    }

    @Override
    public long getTime1() {
        return fTime1;
    }

    @Override
    public long getMinTimeInterval() {
        return fMinTimeInterval;
    }

    @Override
    public int getNameSpace() {
        return fNameWidth;
    }

    @Override
    public void setNameSpace(int width) {
        fNameWidth = width;
        int w = fTimeGraphCtrl.getClientArea().width;
        if (fNameWidth > w - MIN_NAME_WIDTH) {
            fNameWidth = w - MIN_NAME_WIDTH;
        }
        if (fNameWidth < MIN_NAME_WIDTH) {
            fNameWidth = MIN_NAME_WIDTH;
        }
        fTimeGraphCtrl.redraw();
        fTimeScaleCtrl.redraw();
    }

    @Override
    public int getTimeSpace() {
        int w = fTimeGraphCtrl.getClientArea().width;
        return w - fNameWidth;
    }

    @Override
    public long getBeginTime() {
        return fBeginTime;
    }

    @Override
    public long getEndTime() {
        return fEndTime;
    }

    @Override
    public long getMaxTime() {
        return fTime1Bound;
    }

    @Override
    public long getMinTime() {
        return fTime0Bound;
    }

    @Override
    public long getSelectionBegin() {
        return fSelectionBegin;
    }

    @Override
    public long getSelectionEnd() {
        return fSelectionEnd;
    }

    @Override
    public void setStartFinishTimeNotify(long time0, long time1) {
        setStartFinishTimeInt(time0, time1);
        notifyRangeListeners();
    }

    @Override
    public void notifyStartFinishTime() {
        notifyRangeListeners();
    }

    @Override
    public void setStartFinishTime(long time0, long time1) {
        /* if there is a pending time range, ignore this one */
        if (fListenerNotifier != null && fListenerNotifier.hasTimeRangeUpdated()) {
            return;
        }
        setStartFinishTimeInt(time0, time1);
    }

    private void setStartFinishTimeInt(long time0, long time1) {
        fTime0 = time0;
        if (fTime0 < fTime0Bound) {
            fTime0 = fTime0Bound;
        }
        if (fTime0 > fTime1Bound) {
            fTime0 = fTime1Bound;
        }
        fTime1 = time1;
        if (fTime1 < fTime0Bound) {
            fTime1 = fTime0Bound;
        }
        if (fTime1 > fTime1Bound) {
            fTime1 = fTime1Bound;
        }
        if (fTime1 - fTime0 < fMinTimeInterval) {
            fTime1 = Math.min(fTime1Bound, fTime0 + fMinTimeInterval);
        }
        fTimeRangeFixed = true;
        adjustHorizontalScrollBar();
        fTimeGraphCtrl.redraw();
        fTimeScaleCtrl.redraw();
    }

    @Override
    public void resetStartFinishTime() {
        setStartFinishTimeNotify(fTime0Bound, fTime1Bound);
        fTimeRangeFixed = false;
    }

    @Override
    public void setSelectedTimeNotify(long time, boolean ensureVisible) {
        setSelectedTimeInt(time, ensureVisible, true);
    }

    @Override
    public void setSelectedTime(long time, boolean ensureVisible) {
        /* if there is a pending time selection, ignore this one */
        if (fListenerNotifier != null && fListenerNotifier.hasTimeSelected()) {
            return;
        }
        setSelectedTimeInt(time, ensureVisible, false);
    }

    @Override
    public void setSelectionRangeNotify(long beginTime, long endTime) {
        long time0 = fTime0;
        long time1 = fTime1;
        boolean changed = (beginTime != fSelectionBegin || endTime != fSelectionEnd);
        fSelectionBegin = Math.max(fTime0Bound, Math.min(fTime1Bound, beginTime));
        fSelectionEnd = Math.max(fTime0Bound, Math.min(fTime1Bound, endTime));
        ensureVisible(fSelectionEnd);
        fTimeGraphCtrl.redraw();
        fTimeScaleCtrl.redraw();
        if ((time0 != fTime0) || (time1 != fTime1)) {
            notifyRangeListeners();
        }
        if (changed) {
            notifyTimeListeners();
        }
    }

    @Override
    public void setSelectionRange(long beginTime, long endTime) {
        /* if there is a pending time selection, ignore this one */
        if (fListenerNotifier != null && fListenerNotifier.hasTimeSelected()) {
            return;
        }
        fSelectionBegin = Math.max(fTime0Bound, Math.min(fTime1Bound, beginTime));
        fSelectionEnd = Math.max(fTime0Bound, Math.min(fTime1Bound, endTime));
        fTimeGraphCtrl.redraw();
        fTimeScaleCtrl.redraw();
    }

    private void setSelectedTimeInt(long time, boolean ensureVisible, boolean doNotify) {
        long time0 = fTime0;
        long time1 = fTime1;
        if (ensureVisible) {
            ensureVisible(time);
        }
        fTimeGraphCtrl.redraw();
        fTimeScaleCtrl.redraw();

        boolean notifySelectedTime = (time != fSelectionBegin || time != fSelectionEnd);
        fSelectionBegin = time;
        fSelectionEnd = time;

        if (doNotify && ((time0 != fTime0) || (time1 != fTime1))) {
            notifyRangeListeners();
        }

        if (doNotify && notifySelectedTime) {
            notifyTimeListeners();
        }
    }

    private void ensureVisible(long time) {
        long timeMid = (fTime1 - fTime0) / 2;
        if (time < fTime0) {
            long dt = fTime0 - time + timeMid;
            fTime0 -= dt;
            fTime1 -= dt;
        } else if (time > fTime1) {
            long dt = time - fTime1 + timeMid;
            fTime0 += dt;
            fTime1 += dt;
        }
        if (fTime0 < fTime0Bound) {
            fTime1 = Math.min(fTime1Bound, fTime1 + (fTime0Bound - fTime0));
            fTime0 = fTime0Bound;
        } else if (fTime1 > fTime1Bound) {
            fTime0 = Math.max(fTime0Bound, fTime0 - (fTime1 - fTime1Bound));
            fTime1 = fTime1Bound;
        }
        if (fTime1 - fTime0 < fMinTimeInterval) {
            fTime1 = Math.min(fTime1Bound, fTime0 + fMinTimeInterval);
        }
        adjustHorizontalScrollBar();
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent e) {
        if (fSelectedEntry != getSelection()) {
            fSelectedEntry = getSelection();
            notifySelectionListeners();
        }
    }

    @Override
    public void widgetSelected(SelectionEvent e) {
        if (fSelectedEntry != getSelection()) {
            fSelectedEntry = getSelection();
            notifySelectionListeners();
        }
    }

    /**
     * Callback for when the next event is selected
     *
     * @param extend
     *            true to extend selection range, false for single selection
     * @since 1.0
     */
    public void selectNextEvent(boolean extend) {
        fTimeGraphCtrl.selectNextEvent(extend);
        adjustVerticalScrollBar();
    }

    /**
     * Callback for when the previous event is selected
     *
     * @param extend
     *            true to extend selection range, false for single selection
     * @since 1.0
     */
    public void selectPrevEvent(boolean extend) {
        fTimeGraphCtrl.selectPrevEvent(extend);
        adjustVerticalScrollBar();
    }

    /**
     * Callback for when the next item is selected
     */
    public void selectNextItem() {
        fTimeGraphCtrl.selectNextTrace();
        adjustVerticalScrollBar();
    }

    /**
     * Callback for when the previous item is selected
     */
    public void selectPrevItem() {
        fTimeGraphCtrl.selectPrevTrace();
        adjustVerticalScrollBar();
    }

    /**
     * Callback for the show legend action
     */
    public void showLegend() {
        if (fDataViewer == null || fDataViewer.isDisposed()) {
            return;
        }

        TimeGraphLegend.open(fDataViewer.getShell(), fTimeGraphProvider);
    }

    /**
     * Callback for the Zoom In action
     */
    public void zoomIn() {
        fTimeGraphCtrl.zoomIn();
    }

    /**
     * Callback for the Zoom Out action
     */
    public void zoomOut() {
        fTimeGraphCtrl.zoomOut();
    }

    private String getPreferenceString(String string) {
        return getViewTypeStr() + "." + string; //$NON-NLS-1$
    }

    /**
     * Add a selection listener
     *
     * @param listener
     *            The listener to add
     */
    public void addSelectionListener(ITimeGraphSelectionListener listener) {
        fSelectionListeners.add(listener);
    }

    /**
     * Remove a selection listener
     *
     * @param listener
     *            The listener to remove
     */
    public void removeSelectionListener(ITimeGraphSelectionListener listener) {
        fSelectionListeners.remove(listener);
    }

    private void notifySelectionListeners() {
        if (fListenerNotifier == null) {
            fListenerNotifier = new ListenerNotifier();
            fListenerNotifier.start();
        }
        fListenerNotifier.selectionChanged();
    }

    private void fireSelectionChanged(ITimeGraphEntry selection) {
        TimeGraphSelectionEvent event = new TimeGraphSelectionEvent(this, selection);

        for (ITimeGraphSelectionListener listener : fSelectionListeners) {
            listener.selectionChanged(event);
        }
    }

    /**
     * Add a time listener
     *
     * @param listener
     *            The listener to add
     */
    public void addTimeListener(ITimeGraphTimeListener listener) {
        fTimeListeners.add(listener);
    }

    /**
     * Remove a time listener
     *
     * @param listener
     *            The listener to remove
     */
    public void removeTimeListener(ITimeGraphTimeListener listener) {
        fTimeListeners.remove(listener);
    }

    private void notifyTimeListeners() {
        if (fListenerNotifier == null) {
            fListenerNotifier = new ListenerNotifier();
            fListenerNotifier.start();
        }
        fListenerNotifier.timeSelected();
    }

    private void fireTimeSelected(long startTime, long endTime) {
        TimeGraphTimeEvent event = new TimeGraphTimeEvent(this, startTime, endTime);

        for (ITimeGraphTimeListener listener : fTimeListeners) {
            listener.timeSelected(event);
        }
    }

    /**
     * Add a range listener
     *
     * @param listener
     *            The listener to add
     */
    public void addRangeListener(ITimeGraphRangeListener listener) {
        fRangeListeners.add(listener);
    }

    /**
     * Remove a range listener
     *
     * @param listener
     *            The listener to remove
     */
    public void removeRangeListener(ITimeGraphRangeListener listener) {
        fRangeListeners.remove(listener);
    }

    private void notifyRangeListeners() {
        if (fListenerNotifier == null) {
            fListenerNotifier = new ListenerNotifier();
            fListenerNotifier.start();
        }
        fListenerNotifier.timeRangeUpdated();
    }

    private void fireTimeRangeUpdated(long startTime, long endTime) {
        // Check if the time has actually changed from last notification
        if (startTime != fTime0ExtSynch || endTime != fTime1ExtSynch) {
            // Notify Time Scale Selection Listeners
            TimeGraphRangeUpdateEvent event = new TimeGraphRangeUpdateEvent(this, startTime, endTime);

            for (ITimeGraphRangeListener listener : fRangeListeners) {
                listener.timeRangeUpdated(event);
            }

            // update external synch timers
            updateExtSynchTimers();
        }
    }

    /**
     * Callback to set a selected event in the view
     *
     * @param event
     *            The event that was selected
     * @param source
     *            The source of this selection event
     */
    public void setSelectedEvent(ITimeEvent event, Object source) {
        if (event == null || source == this) {
            return;
        }
        fSelectedEntry = event.getEntry();
        fTimeGraphCtrl.selectItem(fSelectedEntry, false);

        setSelectedTimeInt(event.getTime(), true, true);
        adjustVerticalScrollBar();
    }

    /**
     * Set the seeked time of a trace
     *
     * @param trace
     *            The trace that was seeked
     * @param time
     *            The target time
     * @param source
     *            The source of this seek event
     */
    public void setSelectedTraceTime(ITimeGraphEntry trace, long time, Object source) {
        if (trace == null || source == this) {
            return;
        }
        fSelectedEntry = trace;
        fTimeGraphCtrl.selectItem(trace, false);

        setSelectedTimeInt(time, true, true);
    }

    /**
     * Callback for a trace selection
     *
     * @param trace
     *            The trace that was selected
     */
    public void setSelection(ITimeGraphEntry trace) {
        /* if there is a pending selection, ignore this one */
        if (fListenerNotifier != null && fListenerNotifier.hasSelectionChanged()) {
            return;
        }
        fSelectedEntry = trace;
        fTimeGraphCtrl.selectItem(trace, false);
        adjustVerticalScrollBar();
    }

    /**
     * Callback for a time window selection
     *
     * @param time0
     *            Start time of the range
     * @param time1
     *            End time of the range
     * @param source
     *            Source of the event
     */
    public void setSelectVisTimeWindow(long time0, long time1, Object source) {
        if (source == this) {
            return;
        }

        setStartFinishTimeInt(time0, time1);

        // update notification time values since we are now in synch with the
        // external application
        updateExtSynchTimers();
    }

    /**
     * update the cache timers used to identify the need to send a time window
     * update to external registered listeners
     */
    private void updateExtSynchTimers() {
        // last time notification cache
        fTime0ExtSynch = fTime0;
        fTime1ExtSynch = fTime1;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return fTimeFormat;
    }

    /**
     * @param tf
     *            the {@link TimeFormat} used to display timestamps
     */
    public void setTimeFormat(TimeFormat tf) {
        this.fTimeFormat = tf;
        if (tf == TimeFormat.CYCLES) {
            fTimeDataProvider = new TimeDataProviderCyclesConverter(this, fClockFrequency);
        } else {
            fTimeDataProvider = this;
        }
        fTimeScaleCtrl.setTimeProvider(fTimeDataProvider);
        if (fToolTipHandler != null) {
            fToolTipHandler.setTimeProvider(fTimeDataProvider);
        }
    }

    /**
     * Sets the clock frequency. Used when the time format is set to CYCLES.
     *
     * @param clockFrequency
     *            the clock frequency in Hz
     */
    public void setClockFrequency(long clockFrequency) {
        fClockFrequency = clockFrequency;
        if (fTimeFormat == TimeFormat.CYCLES) {
            fTimeDataProvider = new TimeDataProviderCyclesConverter(this, fClockFrequency);
            fTimeScaleCtrl.setTimeProvider(fTimeDataProvider);
            if (fToolTipHandler != null) {
                fToolTipHandler.setTimeProvider(fTimeDataProvider);
            }
        }
    }

    /**
     * Retrieve the border width
     *
     * @return The width
     */
    public int getBorderWidth() {
        return fBorderWidth;
    }

    /**
     * Set the border width
     *
     * @param borderWidth
     *            The width
     */
    public void setBorderWidth(int borderWidth) {
        if (borderWidth > -1) {
            this.fBorderWidth = borderWidth;
            GridLayout gl = (GridLayout) fDataViewer.getLayout();
            gl.marginHeight = borderWidth;
        }
    }

    /**
     * Retrieve the height of the header
     *
     * @return The height
     */
    public int getHeaderHeight() {
        return fTimeScaleHeight;
    }

    /**
     * Set the height of the header
     *
     * @param headerHeight
     *            The height to set
     */
    public void setHeaderHeight(int headerHeight) {
        if (headerHeight > -1) {
            this.fTimeScaleHeight = headerHeight;
            fTimeScaleCtrl.setHeight(headerHeight);
        }
    }

    /**
     * Retrieve the height of an item row
     *
     * @return The height
     */
    public int getItemHeight() {
        if (fTimeGraphCtrl != null) {
            return fTimeGraphCtrl.getItemHeight();
        }
        return 0;
    }

    /**
     * Set the height of an item row
     *
     * @param rowHeight
     *            The height to set
     */
    public void setItemHeight(int rowHeight) {
        if (fTimeGraphCtrl != null) {
            fTimeGraphCtrl.setItemHeight(rowHeight);
        }
    }

    /**
     * Set the minimum item width
     *
     * @param width
     *            The min width
     */
    public void setMinimumItemWidth(int width) {
        if (fTimeGraphCtrl != null) {
            fTimeGraphCtrl.setMinimumItemWidth(width);
        }
    }

    /**
     * Set the width for the name column
     *
     * @param width
     *            The width
     */
    public void setNameWidthPref(int width) {
        fNameWidthPref = width;
        if (width == 0) {
            fMinNameWidth = 0;
            fNameWidth = 0;
        }
    }

    /**
     * Retrieve the configure width for the name column
     *
     * @param width
     *            Unused?
     * @return The width
     */
    public int getNameWidthPref(int width) {
        return fNameWidthPref;
    }

    /**
     * Returns the primary control associated with this viewer.
     *
     * @return the SWT control which displays this viewer's content
     */
    public Control getControl() {
        return fDataViewer;
    }

    /**
     * Returns the time graph control associated with this viewer.
     *
     * @return the time graph control
     */
    public TimeGraphControl getTimeGraphControl() {
        return fTimeGraphCtrl;
    }

    /**
     * Returns the time graph scale associated with this viewer.
     *
     * @return the time graph scale
     */
    public TimeGraphScale getTimeGraphScale() {
        return fTimeScaleCtrl;
    }

    /**
     * Returns the composite containing all the controls that are time aligned,
     * i.e. TimeGraphScale, TimeGraphControl.
     *
     * @return the time based composite
     * @since 1.0
     */
    public Composite getTimeAlignedComposite() {
        return fTimeAlignedComposite;
    }

    /**
     * Return the x coordinate corresponding to a time
     *
     * @param time
     *            the time
     * @return the x coordinate corresponding to the time
     */
    public int getXForTime(long time) {
        return fTimeGraphCtrl.getXForTime(time);
    }

    /**
     * Return the time corresponding to an x coordinate
     *
     * @param x
     *            the x coordinate
     * @return the time corresponding to the x coordinate
     */
    public long getTimeAtX(int x) {
        return fTimeGraphCtrl.getTimeAtX(x);
    }

    /**
     * Get the selection provider
     *
     * @return the selection provider
     */
    public ISelectionProvider getSelectionProvider() {
        return fTimeGraphCtrl;
    }

    /**
     * Wait for the cursor
     *
     * @param waitInd
     *            Wait indefinitely?
     */
    public void waitCursor(boolean waitInd) {
        fTimeGraphCtrl.waitCursor(waitInd);
    }

    /**
     * Get the horizontal scroll bar object
     *
     * @return The scroll bar
     */
    public Slider getHorizontalBar() {
        return fHorizontalScrollBar;
    }

    /**
     * Get the vertical scroll bar object
     *
     * @return The scroll bar
     */
    public Slider getVerticalBar() {
        return fVerticalScrollBar;
    }

    /**
     * Set the given index as the top one
     *
     * @param index
     *            The index that will go to the top
     */
    public void setTopIndex(int index) {
        fTimeGraphCtrl.setTopIndex(index);
        adjustVerticalScrollBar();
    }

    /**
     * Retrieve the current top index
     *
     * @return The top index
     */
    public int getTopIndex() {
        return fTimeGraphCtrl.getTopIndex();
    }

    /**
     * Sets the auto-expand level to be used when the input of the viewer is set
     * using {@link #setInput(Object)}. The value 0 means that there is no
     * auto-expand; 1 means that top-level elements are expanded, but not their
     * children; 2 means that top-level elements are expanded, and their
     * children, but not grand-children; and so on.
     * <p>
     * The value {@link #ALL_LEVELS} means that all subtrees should be expanded.
     * </p>
     * @param level
     *            non-negative level, or <code>ALL_LEVELS</code> to expand all
     *            levels of the tree
     */
    public void setAutoExpandLevel(int level) {
        fTimeGraphCtrl.setAutoExpandLevel(level);
    }

    /**
     * Returns the auto-expand level.
     *
     * @return non-negative level, or <code>ALL_LEVELS</code> if all levels of
     *         the tree are expanded automatically
     * @see #setAutoExpandLevel
     */
    public int getAutoExpandLevel() {
        return fTimeGraphCtrl.getAutoExpandLevel();
    }

    /**
     * Set the expanded state of an entry
     *
     * @param entry
     *            The entry to expand/collapse
     * @param expanded
     *            True for expanded, false for collapsed
     */
    public void setExpandedState(ITimeGraphEntry entry, boolean expanded) {
        fTimeGraphCtrl.setExpandedState(entry, expanded);
        adjustVerticalScrollBar();
    }

    /**
     * Collapses all nodes of the viewer's tree, starting with the root.
     */
    public void collapseAll() {
        fTimeGraphCtrl.collapseAll();
        adjustVerticalScrollBar();
    }

    /**
     * Expands all nodes of the viewer's tree, starting with the root.
     */
    public void expandAll() {
        fTimeGraphCtrl.expandAll();
        adjustVerticalScrollBar();
    }

    /**
     * Get the number of sub-elements when expanded
     *
     * @return The element count
     */
    public int getExpandedElementCount() {
        return fTimeGraphCtrl.getExpandedElementCount();
    }

    /**
     * Get the sub-elements
     *
     * @return The array of entries that are below this one
     */
    public ITimeGraphEntry[] getExpandedElements() {
        return fTimeGraphCtrl.getExpandedElements();
    }

    /**
     * Add a tree listener
     *
     * @param listener
     *            The listener to add
     */
    public void addTreeListener(ITimeGraphTreeListener listener) {
        fTimeGraphCtrl.addTreeListener(listener);
    }

    /**
     * Remove a tree listener
     *
     * @param listener
     *            The listener to remove
     */
    public void removeTreeListener(ITimeGraphTreeListener listener) {
        fTimeGraphCtrl.removeTreeListener(listener);
    }

    /**
     * Get the reset scale action.
     *
     * @return The Action object
     */
    public Action getResetScaleAction() {
        if (fResetScaleAction == null) {
            // resetScale
            fResetScaleAction = new Action() {
                @Override
                public void run() {
                    resetStartFinishTime();
                }
            };
            fResetScaleAction.setText(Messages.TmfTimeGraphViewer_ResetScaleActionNameText);
            fResetScaleAction.setToolTipText(Messages.TmfTimeGraphViewer_ResetScaleActionToolTipText);
            fResetScaleAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_HOME_MENU));
        }
        return fResetScaleAction;
    }

    /**
     * Get the show legend action.
     *
     * @return The Action object
     */
    public Action getShowLegendAction() {
        if (fShowLegendAction == null) {
            // showLegend
            fShowLegendAction = new Action() {
                @Override
                public void run() {
                    showLegend();
                }
            };
            fShowLegendAction.setText(Messages.TmfTimeGraphViewer_LegendActionNameText);
            fShowLegendAction.setToolTipText(Messages.TmfTimeGraphViewer_LegendActionToolTipText);
            fShowLegendAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_SHOW_LEGEND));
        }

        return fShowLegendAction;
    }

    /**
     * Get the the next event action.
     *
     * @return The action object
     */
    public Action getNextEventAction() {
        if (fNextEventAction == null) {
            fNextEventAction = new Action() {
                @Override
                public void runWithEvent(Event event) {
                    boolean extend = (event.stateMask & SWT.SHIFT) != 0;
                    selectNextEvent(extend);
                }
            };

            fNextEventAction.setText(Messages.TmfTimeGraphViewer_NextEventActionNameText);
            fNextEventAction.setToolTipText(Messages.TmfTimeGraphViewer_NextEventActionToolTipText);
            fNextEventAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_NEXT_EVENT));
        }

        return fNextEventAction;
    }

    /**
     * Get the previous event action.
     *
     * @return The Action object
     */
    public Action getPreviousEventAction() {
        if (fPrevEventAction == null) {
            fPrevEventAction = new Action() {
                @Override
                public void runWithEvent(Event event) {
                    boolean extend = (event.stateMask & SWT.SHIFT) != 0;
                    selectPrevEvent(extend);
                }
            };

            fPrevEventAction.setText(Messages.TmfTimeGraphViewer_PreviousEventActionNameText);
            fPrevEventAction.setToolTipText(Messages.TmfTimeGraphViewer_PreviousEventActionToolTipText);
            fPrevEventAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_PREV_EVENT));
        }

        return fPrevEventAction;
    }

    /**
     * Get the next item action.
     *
     * @return The Action object
     */
    public Action getNextItemAction() {
        if (fNextItemAction == null) {

            fNextItemAction = new Action() {
                @Override
                public void run() {
                    selectNextItem();
                }
            };
            fNextItemAction.setText(Messages.TmfTimeGraphViewer_NextItemActionNameText);
            fNextItemAction.setToolTipText(Messages.TmfTimeGraphViewer_NextItemActionToolTipText);
            fNextItemAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_NEXT_ITEM));
        }
        return fNextItemAction;
    }

    /**
     * Get the previous item action.
     *
     * @return The Action object
     */
    public Action getPreviousItemAction() {
        if (fPreviousItemAction == null) {

            fPreviousItemAction = new Action() {
                @Override
                public void run() {
                    selectPrevItem();
                }
            };
            fPreviousItemAction.setText(Messages.TmfTimeGraphViewer_PreviousItemActionNameText);
            fPreviousItemAction.setToolTipText(Messages.TmfTimeGraphViewer_PreviousItemActionToolTipText);
            fPreviousItemAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_PREV_ITEM));
        }
        return fPreviousItemAction;
    }

    /**
     * Get the zoom in action
     *
     * @return The Action object
     */
    public Action getZoomInAction() {
        if (fZoomInAction == null) {
            fZoomInAction = new Action() {
                @Override
                public void run() {
                    zoomIn();
                }
            };
            fZoomInAction.setText(Messages.TmfTimeGraphViewer_ZoomInActionNameText);
            fZoomInAction.setToolTipText(Messages.TmfTimeGraphViewer_ZoomInActionToolTipText);
            fZoomInAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_ZOOM_IN_MENU));
        }
        return fZoomInAction;
    }

    /**
     * Get the zoom out action
     *
     * @return The Action object
     */
    public Action getZoomOutAction() {
        if (fZoomOutAction == null) {
            fZoomOutAction = new Action() {
                @Override
                public void run() {
                    zoomOut();
                }
            };
            fZoomOutAction.setText(Messages.TmfTimeGraphViewer_ZoomOutActionNameText);
            fZoomOutAction.setToolTipText(Messages.TmfTimeGraphViewer_ZoomOutActionToolTipText);
            fZoomOutAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_ZOOM_OUT_MENU));
        }
        return fZoomOutAction;
    }

    /**
     * Get the hide arrows action
     *
     * @param dialogSettings
     *            The dialog settings section where the state should be stored,
     *            or null
     *
     * @return The Action object
     */
    public Action getHideArrowsAction(final IDialogSettings dialogSettings) {
        if (fHideArrowsAction == null) {
            fHideArrowsAction = new Action(Messages.TmfTimeGraphViewer_HideArrowsActionNameText, IAction.AS_CHECK_BOX) {
                @Override
                public void run() {
                    boolean hideArrows = fHideArrowsAction.isChecked();
                    fTimeGraphCtrl.hideArrows(hideArrows);
                    refresh();
                    if (dialogSettings != null) {
                        dialogSettings.put(HIDE_ARROWS_KEY, hideArrows);
                    }
                    if (fFollowArrowFwdAction != null) {
                        fFollowArrowFwdAction.setEnabled(!hideArrows);
                    }
                    if (fFollowArrowBwdAction != null) {
                        fFollowArrowBwdAction.setEnabled(!hideArrows);
                    }
                }
            };
            fHideArrowsAction.setToolTipText(Messages.TmfTimeGraphViewer_HideArrowsActionToolTipText);
            fHideArrowsAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_HIDE_ARROWS));
            if (dialogSettings != null) {
                boolean hideArrows = dialogSettings.getBoolean(HIDE_ARROWS_KEY);
                fTimeGraphCtrl.hideArrows(hideArrows);
                fHideArrowsAction.setChecked(hideArrows);
                if (fFollowArrowFwdAction != null) {
                    fFollowArrowFwdAction.setEnabled(!hideArrows);
                }
                if (fFollowArrowBwdAction != null) {
                    fFollowArrowBwdAction.setEnabled(!hideArrows);
                }
            }
        }
        return fHideArrowsAction;
    }

    /**
     * Get the follow arrow forward action.
     *
     * @return The Action object
     */
    public Action getFollowArrowFwdAction() {
        if (fFollowArrowFwdAction == null) {
            fFollowArrowFwdAction = new Action() {
                @Override
                public void runWithEvent(Event event) {
                    boolean extend = (event.stateMask & SWT.SHIFT) != 0;
                    fTimeGraphCtrl.followArrowFwd(extend);
                    adjustVerticalScrollBar();
                }
            };
            fFollowArrowFwdAction.setText(Messages.TmfTimeGraphViewer_FollowArrowForwardActionNameText);
            fFollowArrowFwdAction.setToolTipText(Messages.TmfTimeGraphViewer_FollowArrowForwardActionToolTipText);
            fFollowArrowFwdAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_FOLLOW_ARROW_FORWARD));
            if (fHideArrowsAction != null) {
                fFollowArrowFwdAction.setEnabled(!fHideArrowsAction.isChecked());
            }
        }
        return fFollowArrowFwdAction;
    }

    /**
     * Get the follow arrow backward action.
     *
     * @return The Action object
     */
    public Action getFollowArrowBwdAction() {
        if (fFollowArrowBwdAction == null) {
            fFollowArrowBwdAction = new Action() {
                @Override
                public void runWithEvent(Event event) {
                    boolean extend = (event.stateMask & SWT.SHIFT) != 0;
                    fTimeGraphCtrl.followArrowBwd(extend);
                    adjustVerticalScrollBar();
                }
            };
            fFollowArrowBwdAction.setText(Messages.TmfTimeGraphViewer_FollowArrowBackwardActionNameText);
            fFollowArrowBwdAction.setToolTipText(Messages.TmfTimeGraphViewer_FollowArrowBackwardActionToolTipText);
            fFollowArrowBwdAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_FOLLOW_ARROW_BACKWARD));
            if (fHideArrowsAction != null) {
                fFollowArrowBwdAction.setEnabled(!fHideArrowsAction.isChecked());
            }
        }
        return fFollowArrowBwdAction;
    }

    private void adjustHorizontalScrollBar() {
        long time0 = getTime0();
        long time1 = getTime1();
        long timeMin = getMinTime();
        long timeMax = getMaxTime();
        long delta = timeMax - timeMin;
        int timePos = 0;
        int thumb = H_SCROLLBAR_MAX;
        if (delta != 0) {
            // Thumb size (page size)
            thumb = Math.max(1, (int) (H_SCROLLBAR_MAX * ((double) (time1 - time0) / delta)));
            // At the beginning of visible window
            timePos = (int) (H_SCROLLBAR_MAX * ((double) (time0 - timeMin) / delta));
        }
        fHorizontalScrollBar.setValues(timePos, 0, H_SCROLLBAR_MAX, thumb, Math.max(1, thumb / 2), Math.max(2, thumb));
    }

    private void adjustVerticalScrollBar() {
        int topIndex = fTimeGraphCtrl.getTopIndex();
        int countPerPage = fTimeGraphCtrl.countPerPage();
        int expandedElementCount = fTimeGraphCtrl.getExpandedElementCount();
        if (topIndex + countPerPage > expandedElementCount) {
            fTimeGraphCtrl.setTopIndex(Math.max(0, expandedElementCount - countPerPage));
        }

        int selection = fTimeGraphCtrl.getTopIndex();
        int min = 0;
        int max = Math.max(1, expandedElementCount - 1);
        int thumb = Math.min(max, Math.max(1, countPerPage - 1));
        int increment = 1;
        int pageIncrement = Math.max(1, countPerPage);
        fVerticalScrollBar.setValues(selection, min, max, thumb, increment, pageIncrement);
    }

    /**
     * @param listener
     *            a {@link MenuDetectListener}
     * @see org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl#addTimeGraphEntryMenuListener(org.eclipse.swt.events.MenuDetectListener)
     */
    public void addTimeGraphEntryMenuListener(MenuDetectListener listener) {
        fTimeGraphCtrl.addTimeGraphEntryMenuListener(listener);
    }

    /**
     * @param listener
     *            a {@link MenuDetectListener}
     * @see org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl#removeTimeGraphEntryMenuListener(org.eclipse.swt.events.MenuDetectListener)
     */
    public void removeTimeGraphEntryMenuListener(MenuDetectListener listener) {
        fTimeGraphCtrl.removeTimeGraphEntryMenuListener(listener);
    }

    /**
     * @param listener
     *            a {@link MenuDetectListener}
     * @see org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl#addTimeEventMenuListener(org.eclipse.swt.events.MenuDetectListener)
     */
    public void addTimeEventMenuListener(MenuDetectListener listener) {
        fTimeGraphCtrl.addTimeEventMenuListener(listener);
    }

    /**
     * @param listener
     *            a {@link MenuDetectListener}
     * @see org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl#removeTimeEventMenuListener(org.eclipse.swt.events.MenuDetectListener)
     */
    public void removeTimeEventMenuListener(MenuDetectListener listener) {
        fTimeGraphCtrl.removeTimeEventMenuListener(listener);
    }

    /**
     * @param filter
     *            The filter object to be attached to the view
     */
    public void addFilter(ViewerFilter filter) {
        fTimeGraphCtrl.addFilter(filter);
        refresh();
    }

    /**
     * @param filter
     *            The filter object to be attached to the view
     */
    public void removeFilter(ViewerFilter filter) {
        fTimeGraphCtrl.removeFilter(filter);
        refresh();
    }

    /**
     * Return the time alignment information
     *
     * @return the time alignment information
     *
     * @see ITmfTimeAligned
     *
     * @since 1.0
     */
    public TmfTimeViewAlignmentInfo getTimeViewAlignmentInfo() {
        return fTimeGraphCtrl.getTimeViewAlignmentInfo();
    }

    /**
     * Return the available width for the time-axis.
     *
     * @see ITmfTimeAligned
     *
     * @param requestedOffset
     *            the requested offset
     * @return the available width for the time-axis
     *
     * @since 1.0
     */
    public int getAvailableWidth(int requestedOffset) {
        int totalWidth = fTimeAlignedComposite.getSize().x;
        return Math.min(totalWidth, Math.max(0, totalWidth - requestedOffset));
    }

    /**
     * Perform the alignment operation.
     *
     * @param offset
     *            the alignment offset
     * @param width
     *            the alignment width
     *
     * @see ITmfTimeAligned
     *
     * @since 1.0
     */
    public void performAlign(int offset, int width) {
        fTimeGraphCtrl.performAlign(offset);
        int alignmentWidth = width;
        int size = fTimeAlignedComposite.getSize().x;
        GridLayout layout = (GridLayout) fTimeAlignedComposite.getLayout();
        int marginSize = size - alignmentWidth - offset;
        layout.marginRight = Math.max(0, marginSize);
        fTimeAlignedComposite.layout();
    }

}
