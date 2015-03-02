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
 *  File created by keith @ Oct 25, 2003
 *  Modified 2011 - 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
 */

package net.kano.codeoutline;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Manages the text outline image, keeping it synchronized with the current file
 * and painting it to the screen when requested.
 */
public class CodeOutlineImage {

    /** The logical position of the offset in a file. */
    public static final LogicalPosition LOGPOS_START = new LogicalPosition(0, 0);
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    /** The editor being outlined. */
    protected final Editor editor;
    /** The document being outlined. */
    protected final Document document;

    /** The text outline image. */
    protected BufferedImage fgImg = null;
    protected BufferedImage bgImg = null;

    /** An empty line. */
    protected int[] emptyLine = null;

    /** The width of the image visible to the user. */
    protected int visibleImgWidth = 0;
    /** The height of the image visible to the user. */
    protected int visibleImgHeight = 0;
    /** Vertical scale factor */

    /** The listener listening to this image. */
    private final CodeOutlineListener listener;

    /** A document listener to listen for changes in the document. */
    private final DocumentListener docListener = new DocumentListener() {
        /** The logical position of the end of the changed region. */
        private LogicalPosition oldend;

        public void beforeDocumentChange(DocumentEvent event) {
            // we need to store the old end logical position before the document
            // changes, because after it changes, there's no way to convert the
            // offset to the logical position it was in before the change.

            // for example, if the user has the text:
            // int x = 2;
            // int y = 4;
            // and selects the entire first line and presses enter, the old
            // selection end offset given in the document change event is 10.
            // before the change, the logical position of offset 10 was line 0,
            // column 10. after the change, though, the logical position of
            // offset 10 is line 1, column 9.
            oldend = editor.offsetToLogicalPosition(event.getOffset() + event.getOldLength());
        }

        public void documentChanged(DocumentEvent e) {
            try {
                refreshImage();
            } catch (Exception ex) {
                listener.handleException(CodeOutlineImage.this, ex);
            }
        }
    };

    /**
     * Creates a new code outline image for the given editor and with the given
     * listener.
     *
     * @param editor the editor to image
     * @param listener a listener for code outline image events
     */
    public CodeOutlineImage(Editor editor, CodeOutlineListener listener) {
        if (listener == null) throw new NullPointerException();

        this.editor = editor;
        this.document = editor.getDocument();
        this.listener = listener;

        init();
    }

    /**
     * Initializes listeners.
     */
    private void init() {
        document.addDocumentListener(docListener);
    }

    /**
     * Removes listeners and flushes the code outline image data.
     */
    public void dispose() {
        document.removeDocumentListener(docListener);

        if (fgImg != null) fgImg.flush();
        if (bgImg != null) bgImg.flush();
    }


    /**
     * Clears the backing image and re-renders it from the editor text.
     */
    public void refreshImage() {
        if (fgImg == null || bgImg == null) return;

        resetImage(fgImg);
        resetImage(bgImg);
    }

    private void resetImage(BufferedImage img) {
        final Graphics2D g = img.createGraphics();
        g.setBackground(TRANSPARENT);
        g.clearRect(0, 0, visibleImgWidth, visibleImgHeight);

        genImage();
    }

    /**
     * Renders the text in the editor to the backing image.
     */
    private void genImage() {
        renderToImg(LOGPOS_START);
    }


    /**
     * Renders the given characters to the code outline image starting at the
     * given position.
     *
     * @param pos the position at which to start rendering
     */
    private void renderToImg(LogicalPosition pos) {
        if (fgImg == null || bgImg == null) return;

        final CharSequence chars = document.getCharsSequence();
        renderToImg(chars, 0, chars.length(), pos);
    }

    /**
     * Renders the given characters to the code outline image starting at the
     * given position.
     *
     * @param chars a character array
     * @param offset the offset into the given array to start rendering
     * @param len the number of characters to render
     * @param pos the position at which to start rendering
     */
    protected void renderToImg(CharSequence chars, int offset, int len, LogicalPosition pos) {

    }


    /**
     * Ensures that the backing image is as large or larger than the given
     * dimensions. If it is not, the image is re-created and the text outline is
     * re-rendered.
     *
     * @param gc a graphics configuration object
     * @param width the minimum width of the image
     * @param height the minimum height of the image
     */
    public synchronized void repaintCode(GraphicsConfiguration gc, int width, int height) {
        if (gc == null) return;

        visibleImgWidth = width;
        visibleImgHeight = height;

        if (fgImg == null || bgImg == null || fgImg.getWidth() < width || fgImg.getHeight() < height) {
            // clear out the old image data
            if (fgImg != null) {
                fgImg.flush();
                fgImg = null;
            }
            if (bgImg != null) {
                bgImg.flush();
                bgImg = null;
            }

            fgImg = gc.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
            bgImg = gc.createCompatibleImage(width, height, Transparency.TRANSLUCENT);
        }
        refreshImage();

    }

    public BufferedImage getFgImg() {
        return fgImg;
    }
    public BufferedImage getBgImg() {
        return bgImg;
    }
}
