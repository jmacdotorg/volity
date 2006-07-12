/*
 * WindowMenu.java
 *
 * Copyright 2004 Karl von Laudermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
 * A menu used for managing the open windows of an application.
 */
public class WindowMenu extends JMenu implements ActionListener
{
    /**
     *Constructor for the WindowMenu object
     */
    public WindowMenu()
    {
        super(JavolinApp.resources.getString("Menu_Window"));
    }

    /**
     * Adds a window to the menu.
     *
     * @param window  The JFrame to be listed in the Window menu.
     */
    public void add(JFrame window)
    {
        JMenuItem item = new WindowMenuItem(window);
        item.addActionListener(this);
        add(item);
    }

    /**
     * Removes a window from the menu.
     *
     * @param window  The JFrame to be removed from the Window menu.
     */
    public void remove(JFrame window)
    {
        int itemCount = getMenuComponentCount();
        boolean found = false;

        for (int n = 0; (n < itemCount) && !found; n++)
        {
            Component c = (WindowMenuItem)getMenuComponent(n);

            if ((c instanceof WindowMenuItem) &&
                ((WindowMenuItem)c).getWindow() == window)
            {
                remove(n);
                found = true;
            }
        }
    }

    /**
     * Removes all windows from the menu.
     */
    public void clear()
    {
        removeAll();
    }

    /**
     * ActionListener interface method implementation.
     *
     * @param e  The action event.
     */
    public void actionPerformed(ActionEvent e)
    {
        Object source = e.getSource();

        if (source instanceof WindowMenuItem)
        {
            ((WindowMenuItem)source).getWindow().toFront();
        }
    }

    /**
     * A window that wants to supply a specific name for its menu entry should
     * implement this interface.
     */
    public interface GetWindowName
    {
        public String getWindowName();
    }
}

/**
 * A JMenuItem subclass for items in a WindowMenu.
 */
class WindowMenuItem extends JMenuItem
{

    private JFrame mWindow;

    /**
     * Constructor.
     *
     * @param window  The window corresponding to this menu item.
     */
    public WindowMenuItem(JFrame window)
    {
        mWindow = window;

        String app = JavolinApp.getAppName();
        String title;

        if (window instanceof WindowMenu.GetWindowName)
        {
            // The window supplies a specific name
            title = ((WindowMenu.GetWindowName)window).getWindowName();
        }
        else 
        {
            // Grab the window's title string
            title = mWindow.getTitle();

            if (title.startsWith(app))
            {
                title = title.replaceFirst(app + "\\S*\\s+", "");
            }
        }

        setText(title);
    }

    /**
     * Gets the window corresponding to this menu item.
     *
     * @return   The JFrame corresponding to this menu item.
     */
    public JFrame getWindow()
    {
        return mWindow;
    }
}
