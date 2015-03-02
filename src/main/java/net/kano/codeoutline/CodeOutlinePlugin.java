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
 *  File created by keith @ Oct 22, 2003
 *  Modified 2011 - 2014, by Ivan Prisyazhniy <john.koepi@gmail.com>
 */

package net.kano.codeoutline;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Code Outline Plugin application component. This component stores settings
 * to <code>code-outline-plugin.xml</code> in the user's IDEA settings folder.
 */
public class CodeOutlinePlugin
        implements ApplicationComponent, NamedJDOMExternalizable {
    private static final Logger logger = Logger.getInstance(CodeOutlinePlugin.class.getName());

    public static final @NonNls String TOOLWINDOW_ID = "Code Outline";

    private final CodeOutlinePrefs prefs = new CodeOutlinePrefs();

    private final Map<Project, CodeOutlineToolWindow> windows = new IdentityHashMap<Project, CodeOutlineToolWindow>();

    public @NotNull String getComponentName() { return "CodeOutlinePlugin"; }

    public synchronized void initComponent() {
        // and add a hook to create windows for new projects
        addProjectListener();
        // create tool windows for all of the open projects
        createForOpenProjects();
    }

    private void addProjectListener() {
        ProjectManager pm = ProjectManager.getInstance();
        pm.addProjectManagerListener(new ProjectManagerAdapter() {
            public void projectOpened(Project project) {
                regForProject(project);
            }

            public void projectClosed(Project project) {
                unregForProject(project);
            }
        });
    }

    private void createForOpenProjects() {
        ProjectManager pm = ProjectManager.getInstance();
        Project[] projects = pm.getOpenProjects();
        for (Project project : projects) {
            regForProject(project);
        }
    }

    /**
     * Creates a code outline tool window for the given project.
     *
     * @param project the project to register
     */
    private synchronized void regForProject(final Project project) {
        final CodeOutlineToolWindow window = new CodeOutlineToolWindow(this, project);

        ToolWindowManager twm = ToolWindowManager.getInstance(project);
        ToolWindowManagerEx twmEx = (ToolWindowManagerEx)twm;
        ToolWindow tw = twm.registerToolWindow(TOOLWINDOW_ID, false, ToolWindowAnchor.RIGHT);
                
        ContentFactory contentFactory = ServiceManager.getService(ContentFactory.class);
        Content content = contentFactory.createContent(window, "", false);

        tw.getContentManager().addContent(content);
        tw.getContentManager().setSelectedContent(content, false);

        twmEx.addToolWindowManagerListener(window.getToolWindowManagerListener());


        EditorPanelInjector injector = new EditorPanelInjector(project, this);
        project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, injector);

    }

    /**
     * Removes the code outline tool window from the given project.
     *
     * @param project the project to unregister
     */
    private synchronized void unregForProject(Project project) {
        ToolWindowManager twm = ToolWindowManager.getInstance(project);
        CodeOutlineToolWindow window = windows.get(project);

        if (window != null) {
            ToolWindowManagerEx twmEx = (ToolWindowManagerEx)twm;
            twmEx.removeToolWindowManagerListener(window.getToolWindowManagerListener());
        }

        try {
            twm.unregisterToolWindow(TOOLWINDOW_ID);
        } catch (IllegalArgumentException ignored) { }

        CodeOutlineToolWindow win = windows.remove(project);
        if (win == null) return;

        win.stop();
    }

    public void disposeComponent() { }

    public String getExternalFileName() { return "CodeOutlinePlugin"; }

    public void readExternal(Element element) throws InvalidDataException {
        prefs.setAnimated(getBooleanValue(element, "animated-scroll", true));
        prefs.setHighlightLine(getBooleanValue(element,
                "highlight-current-line", true));
    }

    public void writeExternal(Element element) {
        setBooleanValue(element, "animated-scroll", prefs.isAnimated());
        setBooleanValue(element, "highlight-current-line", prefs.isHighlightLine());
        setBooleanValue(element, "extend-error-highlights", prefs.isExtendErrorHighlights());
        setBooleanValue(element, "lighten-code-outside-viewport", prefs.isLightenCodeOutsideViewport());


    }

    /**
     * Extracts a boolean value from the text within the element inside the
     * given DOM element with the given name. Conversion is done with {@link
     * Boolean#valueOf(String)}. If no such element exists, the given default
     * value is returned.
     *
     * @param element the DOM element containing an element of the given name
     * @param name the name of the element whose text is
     * @param defaultValue a value to return if no matching DOM element exists
     * @return the boolean value extracted from the given element
     */
    private static boolean getBooleanValue(Element element, String name,
            boolean defaultValue) {
        Element subel = element.getChild(name);
        if (subel != null) {
            return Boolean.valueOf(subel.getTextTrim());
        } else {
            return defaultValue;
        }
    }

    /**
     * Adds a child element with the given to the given element, containg a
     * textual representation of the given boolean value.
     *
     * @param element the element in which the subelement should be created
     * @param name the name of the element
     * @param value the value to encode
     */
    private void setBooleanValue(Element element, String name, boolean value) {
        Element subel = new Element(name);
        subel.setText(Boolean.toString(value));
        element.addContent(subel);
    }

    /**
     * Returns the application-wide code outline preferences object.
     *
     * @return the code outline preferences object in use
     */
    public CodeOutlinePrefs getPrefs() { return prefs; }

    /** Whether we have shown the user a code outline exception. */
    private boolean showedException = false;

    /**
     * Handles the given exception by showing it to the user somehow.
     *
     * @param e the exception
     * @throws RuntimeException if the given exception is a runtime exception
     *         and no exception has been shown to the user yet
     */
    public void handleException(Exception e) throws RuntimeException {
        if (showedException) {
            logger.debug(e);
        } else {
            if (e instanceof RuntimeException) {
                RuntimeException re = (RuntimeException) e;
                showedException = true;
                throw re;
            } else {
                logger.error(e);
            }            
        }
    }
}
