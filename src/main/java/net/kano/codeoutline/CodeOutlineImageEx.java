/*
 *  Copyright (c) 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
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

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SoftWrap;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.*;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

public class CodeOutlineImageEx extends CodeOutlineImage implements Disposable {

    SeverityRegistrar severityRegistrar;
    MarkupModelEx markupModel;
    SoftWrapModelImpl softWrapModel;
    EditorImpl ex;



    /**
     * Creates a new code outline image for the given editor and with the given
     * listener.
     *
     * @param editor the editor to image
     * @param listener a listener for code outline image events
     */
    public CodeOutlineImageEx(EditorEx editor, CodeOutlineListener listener) {
        super(editor, listener);

        ex = (EditorImpl) this.editor;
        softWrapModel = new SoftWrapModelImpl(ex);
        severityRegistrar = SeverityRegistrar.getSeverityRegistrar(editor.getProject());

    }


    protected void renderToImg(CharSequence charsToRender, int offset, int len, LogicalPosition pos) {

        final EditorImpl ex = (EditorImpl)editor;
        DocumentImpl myDocument = (DocumentImpl) ex.getDocument();
        FoldingModelImpl foldingModel =ex.getFoldingModel();
        Graphics2D fG = fgImg.createGraphics();
        Graphics2D bG = bgImg.createGraphics();
        fG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        fG.setFont(new Font(ex.getColorsScheme().getEditorFontName(), Font.BOLD, 2));

        int clipEndOffset = offset + len;
        TextAttributes attributes;
        Color fgColor;
        Color spanBgColor;
        Color windowBgColor;
        Color esColor;
        Color fxColor;
        Color defaultBg = ex.getColorsScheme().getDefaultBackground();
        int start = offset;

        Point position = new Point(0, 2);
        int l;

        LineIterator lIterator = myDocument.createLineIterator();
        lIterator.start(start);
        if (lIterator.atEnd()) {
            return;
        }

        IterationState iterationState = new IterationState(ex, start, clipEndOffset, false);
        try {
            final char[] chars = myDocument.getChars();//TODO .getRawChars(); is no longer defined in this version of IDEA

            while (!iterationState.atEnd() && !lIterator.atEnd()) {
                int hEnd = iterationState.getEndOffset();
                int lEnd = lIterator.getEnd();
                if (hEnd >= lEnd) {
                    FoldRegion collapsedFolderAt = foldingModel.getCollapsedRegionAtOffset(start);
                    if (collapsedFolderAt == null) {
                        start = lEnd;
                        position.y += 2;
                        position.x = 0;
                    }else{
                    }
                    lIterator.advance();
                }
                else {
                    FoldRegion collapsedFolderAt = iterationState.getCurrentFold();
                    l = Math.min(hEnd, lEnd - lIterator.getSeparatorLength()) - start;
                    String token = String.valueOf(chars, start, l).replace("\t","    "); // TODO use tab pref
                    l = token.length();

                    if (collapsedFolderAt != null) {
                        SoftWrap softWrap = softWrapModel.getSoftWrap(collapsedFolderAt.getStartOffset());
                        if (softWrap != null) {
                            // TODO
                        }

                        fG.drawString(collapsedFolderAt.getPlaceholderText(), position.x, position.y);
                    }
                    else {

                        attributes = iterationState.getMergedAttributes();

                        fgColor = attributes.getForegroundColor();
                        windowBgColor = attributes.getBackgroundColor();
                        esColor = attributes.getErrorStripeColor();
                        fxColor = attributes.getEffectColor();

                        spanBgColor=null;
                        if (windowBgColor!=null && !windowBgColor.equals(defaultBg))
                            spanBgColor = windowBgColor;
                        if (fxColor != null)
                            spanBgColor = fxColor;
                        if (esColor != null)
                            spanBgColor = esColor;




                        if (spanBgColor!= null){
                            bG.setColor(spanBgColor);
                            bG.drawLine(position.x, position.y,position.x+l,position.y);
                        }

                        if (token.trim().length() > 0 && !token.equals(".")){
                            fG.setColor(fgColor);
                            fG.drawString(token, position.x, position.y);
                        }
                        position.x+=l;

                    }



                    iterationState.advance();

                    start = iterationState.getStartOffset();
                }
            }


        }
        finally {
            // TODO dispose is no longer defined in this version of idea  iterationState.dispose();
        }

        //TODO Sweep is nolonger defined in this version of IDEA
//        ((MarkupModelEx)ex.getMarkupModel()).sweep(0, Integer.MAX_VALUE, new SweepProcessor<RangeHighlighterEx>() {
//            @Override
//            public boolean process(int i, RangeHighlighterEx rangeHighlighterEx, boolean b, Collection<RangeHighlighterEx> rangeHighlighterExes) {
//                return false;
//            }
//        });

    }


}
