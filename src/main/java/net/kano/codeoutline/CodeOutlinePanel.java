/*
 *  Copyright (c) 2003, Keith Lea
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions 
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer. 
 *  - Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution. 
 *  - Neither the name of Keith Lea nor the names of its
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 *  File created by keith @ Oct 24, 2003
 *  Modified 2011 - 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
 *  Modified 2014 - 2015, by Charlie Hayes <cosmotic@gmail.com>
 */

package net.kano.codeoutline;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

/**
 * A code outline panel for a single text editor. The code outline panel manages
 * everything about the code outline but the text outline itself. This includes
 * painting the selection and visible region, scrolling, configuration UI, and
 * highlighting the current line.
 */

public class CodeOutlinePanel extends JPanel implements Disposable {
    /**
     * A set of text attributes for highlighting the currently hovered line.
     */
    private static final TextAttributes CURRENTLINE_ATTRIBUTES
            = new TextAttributes(null, new Color(220, 255, 220), null,
            null, Font.PLAIN);


    private final CodeOutlinePlugin plugin; // The code outline plugin instance which instantiated this panel.
    private final Project project; // The project for which this code outline panel is shown.
    private final EditorEx editor; // The editor whose code is outlined in this panel.
    private final MarkupModelEx markupModel;
    private final CodeOutlinePrefs prefs; // The set of code outline preferences to obey.
    private final Set<RangeHighlighterEx> highlighters;
    private RangeHighlighter highlighter; // The range highlighter used to highlight the currently hovered line.
    private final CodeOutlineImage image; // The text outline image used in this panel.
    private JPopupMenu contextMenu = new JPopupMenu(); // The context menu that appears when right-clicking the code outline.
    private Point lastMousePoint = null; // The last position of the mouse on the code outline panel, or null if the mouse is not hovering over the panel.
    private Rectangle previousViewport = null; // The old viewport before the preview was invoked
    private Point beforePreview;
    private JCheckBoxMenuItem animatedScrollingMenuItem = new JCheckBoxMenuItem(new AnimateOptionAction());
    private JCheckBoxMenuItem highlightCurrentLineMenuItem = new JCheckBoxMenuItem(new HighlightOptionAction());
    private JCheckBoxMenuItem extendErrorHighlightsMenuItem = new JCheckBoxMenuItem(new ExtendErrorHighlightsOptionAction());
    private JCheckBoxMenuItem lightenCodeOutsideViewportMenuItem = new JCheckBoxMenuItem(new LightenCodeOutsideViewportOptionAction());


    /**
     * A listener for IDEA editor scrolling events.
     */
    private VisibleAreaListener scrollListener = new VisibleAreaListener() {
        public void visibleAreaChanged(VisibleAreaEvent e) {
            repaint();
        }
    };
    /**
     * A listener for IDEA editor text selection events.
     */
    private SelectionListener selectListener = new SelectionListener() {
        public void selectionChanged(SelectionEvent e) {
            repaint();
        }
    };
    /**
     * A listener for IDEA editor caret selection events.
     */
    private CaretListener caretListener = new CaretListener() {
        public void caretPositionChanged(CaretEvent caretEvent) {
            repaint();
        }

        public void caretAdded(CaretEvent caretEvent) {
            repaint();
        }

        public void caretRemoved(CaretEvent caretEvent) {
            repaint();
        }
    };

    /**
     * A listener for IDEA folding model events.
     */
    private FoldingListener foldingListener = new FoldingListener() {
        @Override
        public void onFoldRegionStateChange(@NotNull FoldRegion foldRegion) {

        }

        @Override
        public void onFoldProcessingEnd() {
            image.refreshImage();
            repaint();
        }
    };

    /**
     * A listener for mouse events in this code outline panel.
     */
    private MouseListener mouseListener = new MouseAdapter() {
        // The scrolling position when the user began to Preview Scroll.
        // Whether the scrolling mechanism should smooth scroll when returning to the original scrolling position after Preview Scrolling.
        private boolean slideBack = false;

        public void mousePressed(MouseEvent e) {
            Point point = e.getPoint();


            if (SwingUtilities.isLeftMouseButton(e)) {
                // we want to reset the original scroll position to reset
                // Preview Scroll. this only matters when the user clicks with
                // the left mouse button while still holding down the middle
                // button - in this case, he probably wants to stop Preview
                // Scrolling.
                beforePreview = null;

                scrollTo(point);
            } else if (SwingUtilities.isRightMouseButton(e)) {
                // the right mouse button shows the context menu
                contextMenu.show(CodeOutlinePanel.this, e.getX(), e.getY());

            } else if (SwingUtilities.isMiddleMouseButton(e)) {

//                //TODO IDEA seems to be doing something fishy which is eating the mouse release.
//                // if Ctrl is being held down, we don't want to "slide" or
//                // smooth scroll; we want to skip back and forth non-smoothly
//                beforePreview = getCurrentLogicalScrollPosition();
//                slideBack = !e.isControlDown();
//
//                scrollTo(point, slideBack);



            }
        }

        public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                // when the user releases the left mouse button, the selection
                // is finalized and the cursor goes to the specified mouse
                // position

                beforePreview = null;

            } else if (SwingUtilities.isMiddleMouseButton(e)) {
                // when the user releases the middle mouse button, Preview
                // Scrolling is complete, and the document should be scrolled
                // back to where it was when the user started Preview Scrolling

                // beforePreview could have been reset while the
                // user was Preview Scrolling. if this happened, we don't have
                // to do anything

                previousViewport = null;

                if (beforePreview != null)
                    scrollToLogical(beforePreview, this.slideBack);

                beforePreview = null;
            }
        }

        public void mouseExited(MouseEvent e) {
            // when the mouse exits the code outline panel, the currently
            // hovered line must be de-highlighted

            synchronized (CodeOutlinePanel.this) {
                lastMousePoint = null;
                clearHighlightedLine();
            }
        }
    };
    /**
     * A mouse motion listener for this code outline panel.
     */
    private MouseMotionListener mouseMotionListener
            = new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
            Point point = e.getPoint();

            // the currently hovered line needs to be updated
            mouseover(point);

            if (SwingUtilities.isLeftMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
                // dragging with the middle mouse button should Preview Scroll
                // to the given location
                scrollTo(point, false);
            }
        }

        public void mouseMoved(MouseEvent e) {
            // when the mouse moves, the currently hovered line should be
            // updated
            mouseover(e.getPoint());
        }
    };
    /**
     * A mouse wheel listener for the code ouline panel.
     */
    private MouseWheelListener mouseWheelListener = new MouseWheelListener() {
        public void mouseWheelMoved(MouseWheelEvent e) {
            ScrollingModel sm = editor.getScrollingModel();

            // we disable animation for page up/down like IDEA does
            try {
                sm.disableAnimation();
                // scroll one page up/down for each mouse wheel click
                sm.scrollVertically((int) (sm.getVerticalScrollOffset()
                        + (e.getPreciseWheelRotation() * sm.getVisibleArea().height)));
            } finally {
                sm.enableAnimation();
            }
        }
    };
    /**
     * A listener for determining when the panel should be repainted to reflect
     * changes in the text outline.
     */
    private CodeOutlineListener repaintListener = new CodeOutlineListener() {
        public void shouldRepaint(CodeOutlineImage image) {
            repaint();
        }

        public void handleException(CodeOutlineImage image, Exception e) {
            plugin.handleException(e);
        }
    };

    /**
     * A property change listener for detecting changes in the currently hovered
     * line highlighting option.
     */
    private PropertyChangeListener prefListener
            = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent evt) {
            refresh();
        }
    };


    { // init
        // we are already buffering the text outline. when painting, we mostly
        // just draw rectangles around the text ouline image, which is pretty
        // fast, so we don't need to double-buffer the panel itself
        setDoubleBuffered(false);

        animatedScrollingMenuItem.addPropertyChangeListener("selected",
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        boolean animated = (Boolean) evt.getNewValue();
                        prefs.setAnimated(animated);
                    }
                });

        highlightCurrentLineMenuItem.addPropertyChangeListener("selected",
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        boolean highlighted = (Boolean) evt.getNewValue();
                        prefs.setHighlightLine(highlighted);
                    }
                });

        extendErrorHighlightsMenuItem.addPropertyChangeListener("selected",
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        boolean highlighted = (Boolean) evt.getNewValue();
                        prefs.setExtendErrorHighlights(highlighted);
                    }
                });

        lightenCodeOutsideViewportMenuItem.addPropertyChangeListener("selected",
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent evt) {
                        boolean highlighted = (Boolean) evt.getNewValue();
                        prefs.setLightenCodeOutsideViewport(highlighted);
                    }
                });



        contextMenu.add(animatedScrollingMenuItem);
        contextMenu.add(highlightCurrentLineMenuItem);
        contextMenu.add(extendErrorHighlightsMenuItem);
        contextMenu.add(lightenCodeOutsideViewportMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(new RefreshAction());
        // the context menu's checkboxes are only updated from the code outline
        // preferences object when they are needed (before the menu is shown)
        contextMenu.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuCanceled(PopupMenuEvent e) {
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                animatedScrollingMenuItem.setSelected(prefs.isAnimated());
                highlightCurrentLineMenuItem.setSelected(prefs.isHighlightLine());
                extendErrorHighlightsMenuItem.setSelected(prefs.isExtendErrorHighlights());
                lightenCodeOutsideViewportMenuItem.setSelected(prefs.isLightenCodeOutsideViewport());
            }
        });

        addMouseListener(mouseListener);
        addMouseMotionListener(mouseMotionListener);
        addMouseWheelListener(mouseWheelListener);
    }

    /**
     * Creates a new code outline panel for the given plugin, project, and
     * editor.
     *
     * @param plugin  a code outline plugin instance
     * @param editor  the editor whose contents this panel outlines
     */
    public CodeOutlinePanel(CodeOutlinePlugin plugin, EditorEx editor) {
        this.plugin = plugin;
        this.project = editor.getProject();
        this.editor = editor;
        this.image = new CodeOutlineImageEx(editor, repaintListener);
        this.prefs = plugin.getPrefs();
        this.markupModel = (MarkupModelEx) DocumentMarkupModel.forDocument(editor.getDocument(), editor.getProject(), true);

        highlighters = //new HashSet<RangeHighlighterEx>();
                new TreeSet<RangeHighlighterEx>(new Comparator<RangeHighlighterEx>() {

                    public int compare(RangeHighlighterEx o1, RangeHighlighterEx o2) {
                        return o1.getAffectedAreaStartOffset() - o2.getAffectedAreaStartOffset();
                    }
                });


        markupModel.addMarkupModelListener(this, new MarkupModelListener() {
            public void afterAdded(@NotNull RangeHighlighterEx rangeHighlighterEx) {
                highlighters.add(rangeHighlighterEx);
                repaint();
            }

            public void beforeRemoved(@NotNull RangeHighlighterEx rangeHighlighterEx) {
                highlighters.remove(rangeHighlighterEx);
                repaint();
            }

            @Override
            public void attributesChanged(@NotNull RangeHighlighterEx rangeHighlighterEx, boolean b) {
                repaint();
            }

        });
        UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
                for (RangeHighlighter rangeHighlighter : markupModel.getAllHighlighters()) {
                    highlighters.add((RangeHighlighterEx) rangeHighlighter);
                }
            }
        });

        init();
    }

    /**
     * Creates a new code outline panel for the given plugin, project, and
     * editor.
     *
     * @param plugin  a code outline plugin instance
     * @param editor  the editor whose contents this panel outlines
     */
    public CodeOutlinePanel(CodeOutlinePlugin plugin, Editor editor) {
        this(plugin, (EditorEx) editor);
        //TODO figure out when this might be called, i think this is for old versions which wont work with the new code highliting features
    }

    /**
     * Initializes listeners.
     */
    private void init() {
        prefs.addPropertyChangeListener(prefListener);
        editor.getScrollingModel().addVisibleAreaListener(scrollListener);
        editor.getSelectionModel().addSelectionListener(selectListener);
        editor.getCaretModel().addCaretListener(caretListener);
        editor.getFoldingModel().addListener(foldingListener, this);
    }

    /**
     * Removes listeners and tells the text outline image to dispose of itself
     * as well.
     */
    public void dispose() {
        image.dispose();

        prefs.removePropertyChangeListener(prefListener);
        editor.getScrollingModel().removeVisibleAreaListener(scrollListener);
        editor.getSelectionModel().removeSelectionListener(selectListener);
        editor.getCaretModel().removeCaretListener(caretListener);
    }

    /**
     * Preview Scrolls to the given position. If the user has selected not to
     * animate code outline scrolling operations, the value of
     * <code>animate</code> is ignored.
     *
     * @param point   a point in the code outline panel corresponding to the
     *                position to scroll to
     * @param animate whether the scrolling should be animated
     */
    private void scrollTo(Point point, boolean animate) {
        int x = Math.max(0, point.y /2);
        int y = Math.max(0, point.x /2);
        x = Util.getLinePlusFolds(editor, x);
        LogicalPosition pos = new LogicalPosition(x , y );
        scrollTo(pos, animate);
    }

    private void scrollTo(Point point) {
        scrollTo(point, prefs.isAnimated());
    }

    /**
     * Returns whether a scroll operation should "cut" instead of animating,
     * based on the given value and the user's preferences.
     *
     * @param animate whether animation is suggested
     * @return whether a scroll operation should cut instead of animating
     */
    private boolean shouldCut(boolean animate) {
        return !prefs.isAnimated() || !animate;
    }

    /**
     * Scrolls to the given coordinates. If the user has chosen not to animate
     * scrolling operations, the value of <code>animate</code> is ignored.
     *
     * @param p       point to make top loeft
     * @param animate whether this operation should be animated
     */
    private void scrollToLogical(Point p, boolean animate) {
        ScrollingModel sm = editor.getScrollingModel();
        boolean cut = shouldCut(animate);
        try {
            if (cut) sm.disableAnimation();
            sm.scrollHorizontally(p.x);
            sm.scrollVertically(p.y);
        } finally {
            if (cut) sm.enableAnimation();
        }
    }

    private Point getCurrentLogicalScrollPosition(){
        ScrollingModel sm = editor.getScrollingModel();
        return new Point(sm.getHorizontalScrollOffset(),sm.getVerticalScrollOffset());

    }

    /**
     * Scrolls so the given position is in the center of the window. If the user
     * has chosen not to animate scrolling operations, the value of
     * <code>animate</code> is ignored.
     *
     * @param pos     the position to scroll to
     * @param animate whether this operation should be animated
     */
    private void scrollTo(LogicalPosition pos, boolean animate) {
        ScrollingModel sm = editor.getScrollingModel();
        boolean cut = shouldCut(animate);
        try {
            if (cut) sm.disableAnimation();
            // Handy centring by x-axis
            // This is work around for use of MAKE_VISIBLE
//            Point targetPoint = editor.logicalPositionToXY(pos);
//            int viewX = sm.getVisibleArea().x;
//            int viewWidth = sm.getVisibleArea().width;
//            int deltaX = targetPoint.x - (viewX + viewWidth / 2);
//            if (deltaX > 0) {
//                LogicalPosition pos2 = editor.xyToLogicalPosition(new Point(viewX + viewWidth + deltaX, 0));
//                pos = new LogicalPosition(pos.line, pos2.column - 5);
//            } else {
//                int newX = Math.max(0, viewX + deltaX);
//                LogicalPosition pos2 = editor.xyToLogicalPosition(new Point(newX, 0));
//                pos = new LogicalPosition(pos.line, pos2.column);
//            }
            // Don't know why MAKE_CENTER does wrong.

            sm.scrollTo(pos, ScrollType.CENTER);
        } finally {
            if (cut) sm.enableAnimation();
        }
    }


    /**
     * Highlights the line associated with the given code outline panel
     * position, if the user has this option enabled.
     *
     * @param point a point in the code outline panel
     */
    private synchronized void mouseover(Point point) {
        lastMousePoint = point;
        if (!prefs.isHighlightLine()) return;

        highlightCurrentLine();
    }

    private int getLineFromMousePointY(int mousePointY) {
        return mousePointY / 2; // Two pixels of preview per line, plus one blank line of pixels between each line of text
    }

    /**
     * Highlights the line specified by <code>lastMousePoint</code>. This method
     * does nothing if <code>lastMousePoint</code> is <code>null</code>.
     */
    private void highlightCurrentLine() {
        if (lastMousePoint == null) return;

        clearHighlightedLine();
        MarkupModel mm = editor.getMarkupModel();
        int line = getLineFromMousePointY(lastMousePoint.y);

        line = Util.getLinePlusFolds(editor, line);
        if (line >= 0 && line < editor.getDocument().getLineCount()) {
            highlighter = mm.addLineHighlighter(line, 100, CURRENTLINE_ATTRIBUTES);
        }
    }

    /**
     * Highlights the line which should be highlighted according to the last
     * mouse position; clears the highlighted line if the user has this option
     * turned off.
     */
    private void updateHighlightedLine() {
        if (prefs.isHighlightLine() && lastMousePoint != null) {
            highlightCurrentLine();
        } else {
            clearHighlightedLine();
        }
    }

    /**
     * Erases the highlighting for the currently highlighted line.
     */
    private void clearHighlightedLine() {
        if (highlighter != null) {
            editor.getMarkupModel().removeHighlighter(highlighter);
            highlighter = null;
        }
    }

    /**
     * Draws a selection block between the two given positions.
     *
     * @param g the graphics device t paint t
     * @param f the starting position
     * @param t the ending position
     */
    private void drawSelection(Graphics2D g, LogicalPosition f, LogicalPosition t, int yOffset) {

        int fLine = Util.getLineMinusFolds(editor, f.line);
        int tLine = Util.getLineMinusFolds(editor, t.line);


        int span = Math.abs(fLine - tLine);
        if (span == 0)
            g.fillRect(f.column, (fLine + 1) * 2 - 1 + yOffset, t.column - f.column, 2);
        else {
            g.fillRect(f.column, (fLine + 1) * 2 - 1 + yOffset, getWidth() - f.column, 2);
            g.fillRect(0, (fLine + 1) * 2 + yOffset, getWidth(), (span - 1) * 2 + 1);
            g.fillRect(0, tLine * 2 + 1 + yOffset, t.column, 2);
        }
    }


    /**
     * Repaints the entire code outline panel, reloading the editor text completely by recaching the file.
     */
    public void refresh() {
        image.refreshImage();
        repaint();
    }

    protected void paintComponent(Graphics g1) {
        Dimension editorComponent = editor.getScrollPane().getViewport().getComponents()[0].getSize();

        Color eBG = editor.getColorsScheme().getDefaultBackground();
        Color caretColor = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);

        Graphics2D g = (Graphics2D) g1;
        List<Caret> carets = editor.getCaretModel().getAllCarets();
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        double editorHeight = editorComponent.getHeight();
        double pScrolled = visibleArea.getY() / (editorHeight - visibleArea.getHeight());

        // make sure the text outline image is big enough
        int linesWithoutFolds = editor.getDocument().getLineCount();
        for (int i = 0; i < editor.getFoldingModel().getAllFoldRegions().length; i++) {
            FoldRegion f = editor.getFoldingModel().getAllFoldRegions()[i];
            int y = editor.getDocument().getLineNumber(f.getStartOffset());
            int z = editor.getDocument().getLineNumber(f.getEndOffset());
            if (!f.isExpanded())
                linesWithoutFolds -= z - y;
        }
        int height = (linesWithoutFolds + 6) * 2; // IDEA seems to add 6 lines to the end of the doc, maybe theres a better way to calculate this

        image.repaintCode(getGraphicsConfiguration(), getWidth(), height);

        BufferedImage fg = image.getFgImg();
        BufferedImage bg = image.getBgImg();

        int yOffset = (int) Math.min(-(fg.getHeight() - getHeight()) * pScrolled, 0);

        // fill the whole area with white
        g.setBackground(eBG);
        g.clearRect(0, 0, getWidth(), getHeight());

        // Draw text backgrounds
        g.drawImage(bg, 0, yOffset, null);

        // draw current line
        g.setColor(editor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR));
        for (Caret c : carets) {
            g.fillRect(0, Util.getLineMinusFolds(editor, c.getLogicalPosition().line) * 2 + 1 + yOffset, getWidth(), 2);
        }

        // draw errors/warnings
        if (prefs.isExtendErrorHighlights()) {
            // sort by severity and highlight things that are more severe
            for (RangeHighlighterEx h : highlighters) {
                if (h.isThinErrorStripeMark() || !h.isValid())
                    continue;
                HighlightInfo tooltip = (HighlightInfo) h.getErrorStripeTooltip();
                if (tooltip == null)
                    continue;
                if (tooltip.getDescription() != null) {


                    int y1 = editor.getDocument().getLineNumber(h.getStartOffset());
                    int y2 = editor.getDocument().getLineNumber(h.getEndOffset());
                    int y1f = Util.getLineMinusFolds(editor, y1);
                    int y2f = Util.getLineMinusFolds(editor, y2);

                    int dy = y2f - y1f + 1;
                    Color errorStripeMarkColor = h.getErrorStripeMarkColor();
                    g.setColor(errorStripeMarkColor != null ? errorStripeMarkColor : JBColor.yellow);
                    g.fillRect(0, y1f * 2 + yOffset, getWidth(), dy * 2 + 1);
                }
            }
        }

        // draw the right margin
        final EditorSettings editorSettings = editor.getSettings();
        if (editorSettings.isRightMarginShown()) {
            int margin = editorSettings.getRightMargin(project);
            g.setColor(editor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR));
            g.drawLine(margin, 0, margin, getHeight());
        }

        // draw the selection
        final SelectionModel sm = editor.getSelectionModel();
        if (sm.hasSelection()) {
            g.setColor(editor.getColorsScheme().getColor(EditorColors.SELECTION_BACKGROUND_COLOR));

            int[] starts = sm.getBlockSelectionStarts();
            int[] ends = sm.getBlockSelectionEnds();

            for (int i = 0; i < starts.length; i++) {
                LogicalPosition s = editor.offsetToLogicalPosition(starts[i]);
                LogicalPosition e = editor.offsetToLogicalPosition(ends[i]);

                drawSelection(g, s, e, yOffset);
            }

        }

        // draw the text itself
        g.drawImage(fg, 0, yOffset, null);

        // draw caret
        g.setColor(caretColor);
        for (Caret c : carets) {
            LogicalPosition logicalPosition = c.getLogicalPosition();
            g.fillRect(logicalPosition.column, Util.getLineMinusFolds(editor, logicalPosition.line) * 2 + yOffset, 2, 4);
        }


        Rectangle vp = getProportionalRectangle(editorComponent,
                visibleArea, new Dimension(this.getWidth(), height), pScrolled); // TODO cache dimension
        // mask all but viewport
        if (prefs.isLightenCodeOutsideViewport()) {
            Area mask = new Area(new Rectangle(this.getSize()));
            mask.subtract(new Area(new Rectangle(vp.x, vp.y, vp.width + 1, vp.height + 1)));

            g.setColor(new Color(eBG.getRed(), eBG.getGreen(), eBG.getBlue(), 180));
            g.fill(mask);
        }

        // draw viewport
        g.setColor(new Color(caretColor.getRed(), caretColor.getGreen(), caretColor.getBlue(), 50));
        g.draw(vp);

        if (beforePreview == null){
            // The user isn't previewing so lets store this viewport for later when the user may preview another area
            previousViewport = vp;
        }else{
            if (previousViewport != null) {
                // The user is previewing a different part of the code, let's draw the old region in gray
                g.setColor(new Color(caretColor.getRed(), caretColor.getGreen(), caretColor.getBlue(), 20));
                g.fill(previousViewport);
            }
        }




    }

    private Dimension shrinkHeight(Dimension size, int i) {
        return new Dimension(size.width, Math.min(size.height, i));
    }

    private Rectangle getProportionalRectangle(Dimension outer, Rectangle inner, Dimension targetOuter, double pScrolled) {
        double x;
        double y;
        double height;
        double width;
        double pH = targetOuter.getHeight() / (outer.getHeight());
        double pW = targetOuter.getWidth() / outer.getWidth();


        height = inner.getHeight() * pH;
        width = inner.getWidth() / inner.getHeight() * height * 1.22; // TODO 1.22 is a hack
        x = inner.getX() * pW;
        y = (Math.min(getHeight(), targetOuter.getHeight()) - height) * pScrolled;

        return new Rectangle((int) x, (int) y, (int) width, (int) height);
    }

    private class RefreshAction extends AbstractAction {
        public RefreshAction() {
            super("Refresh");
            putValue(AbstractAction.MNEMONIC_KEY, new Integer(KeyEvent.VK_R));
        }

        public void actionPerformed(ActionEvent e) {
            refresh();
        }
    }

    private class AnimateOptionAction extends AbstractAction {
        public AnimateOptionAction() {
            super("Animated Scroll");
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_A));
        }

        public void actionPerformed(ActionEvent e) {
            prefs.setAnimated(animatedScrollingMenuItem.isSelected());
        }
    }

    private class HighlightOptionAction extends AbstractAction {
        public HighlightOptionAction() {
            super("Highlight Current Line");
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_H));
        }

        public void actionPerformed(ActionEvent e) {
            prefs.setHighlightLine(highlightCurrentLineMenuItem.isSelected());
        }
    }

    private class ExtendErrorHighlightsOptionAction extends AbstractAction {
        public ExtendErrorHighlightsOptionAction() {
            super("Extend error highlights accross entire line");
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_E));
        }

        public void actionPerformed(ActionEvent e) {
            prefs.setExtendErrorHighlights(extendErrorHighlightsMenuItem.isSelected());
        }
    }
    private class LightenCodeOutsideViewportOptionAction extends AbstractAction {
        public LightenCodeOutsideViewportOptionAction() {
            super("Lighten code outside viewport");
            putValue(MNEMONIC_KEY, new Integer(KeyEvent.VK_L));
        }

        public void actionPerformed(ActionEvent e) {
            prefs.setLightenCodeOutsideViewport(lightenCodeOutsideViewportMenuItem.isSelected());
        }
    }

}
