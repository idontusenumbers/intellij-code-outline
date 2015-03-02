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
 *  File created by keith @ Oct 27, 2003
 *  Modified 2011 - 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
 */

package net.kano.codeoutline;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Holds application-wide preferences for the code outline plugin.
 */
public class CodeOutlinePrefs {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private boolean animated = true;

    private boolean highlightLine = true;

    private boolean lightenCodeOutsideViewport = true;

    private boolean extendErrorHighlights = true;

    public boolean isAnimated() { return animated; }

    public void setAnimated(boolean animated) {
        boolean old = this.animated;

        this.animated = animated;

        pcs.firePropertyChange("animated", old, animated);
    }

    public void setHighlightLine(boolean highlightLine) {
        boolean old = this.highlightLine;

        this.highlightLine = highlightLine;

        pcs.firePropertyChange("highlightLine", old, highlightLine);
    }

    public boolean isHighlightLine() { return highlightLine; }

    public boolean isLightenCodeOutsideViewport() {
        return lightenCodeOutsideViewport;
    }

    public void setLightenCodeOutsideViewport(boolean lightenCodeOutsideViewport) {
        boolean old = this.lightenCodeOutsideViewport;

        this.lightenCodeOutsideViewport = lightenCodeOutsideViewport;
        pcs.firePropertyChange("lightenCodeOutsideViewport", old, lightenCodeOutsideViewport);

    }

    public boolean isExtendErrorHighlights() {
        return extendErrorHighlights;
    }

    public void setExtendErrorHighlights(boolean extendErrorHighlights) {
        boolean old = this.extendErrorHighlights;
        this.extendErrorHighlights = extendErrorHighlights;
        pcs.firePropertyChange("extendErrorHighlights", old, extendErrorHighlights);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
