/*
 * BaseDialog.java
 * Source code Copyright 2004 by Karl von Laudermann
 */
package org.volity.javolin;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.*;
import javax.swing.*;

/**
 * Base class for dialogs. Handles saving and restoring the dialog size and position in
 * the preferences storage.
 *
 * @author    karlvonl
 * @created   January 18, 2004
 */
public class BaseDialog extends JDialog
{
	protected final static int MARGIN = 12; // Space to window edge
	protected final static int SPACING = 6; // Space between related controls
	protected final static int GAP = 12; // Space between unrelated controls

	private final static String POSX_KEY = "PosX";
	private final static String POSY_KEY = "PosY";
	private final static String WIDTH_KEY = "Width";
	private final static String HEIGHT_KEY = "Height";

	private String mPrefsNodeName;

	/**
	 * Constructor.
	 *
	 * @param owner     The parent frame.
	 * @param title     The dialog title.
	 * @param modal     true for a modal dialog, false for non-modal.
	 * @param nodeName  A string to uniquely identify the dialog, to use as the
	 * node name in the preferences storage.
	 */
	public BaseDialog(Frame owner, String title, boolean modal, String nodeName)
	{
		super(owner, title, modal);
		mPrefsNodeName = nodeName;

		// Save window size and position when closing
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		addWindowListener(
			new WindowAdapter()
			{
				public void windowClosed(WindowEvent e)
				{
					saveSizeAndPosition();
				}
			});
	}

	/**
	 * Moves the dialog to the center of the screen.
	 */
	protected void centerWindow()
	{
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension frameSize = getSize();

		setLocation((screenSize.width - frameSize.width) / 2,
			(screenSize.height - frameSize.height) / 2);
	}

	/**
	 * Saves the window size and position values to the preferences storage.
	 * The size is not saved if the dialog is not resizable.
	 */
	protected void saveSizeAndPosition()
	{
		Preferences prefs =
			Preferences.userNodeForPackage(getClass()).node(mPrefsNodeName);

		// Only save the size if the dialog is resizable
		if (isResizable())
		{
			Dimension size = getSize();

			prefs.putInt(WIDTH_KEY, size.width);
			prefs.putInt(HEIGHT_KEY, size.height);
		}

		Point loc = getLocation();

		prefs.putInt(POSX_KEY, loc.x);
		prefs.putInt(POSY_KEY, loc.y);
	}

	/**
	 * Sizes and positions the window from the values stored in the preferences
	 * storage. The size is not restored if the dialog is not resizable. If the size
	 * was never stored, the size remains unchanged. If the position was never stored,
	 * the window is centered on the screen.
	 */
	protected void restoreSizeAndPosition()
	{
		Preferences prefs =
			Preferences.userNodeForPackage(getClass()).node(mPrefsNodeName);

		// Only restore the saved size if the dialog is resizable
		if (isResizable())
		{
			Dimension size = new Dimension();

			size.width = prefs.getInt(WIDTH_KEY, -1);
			size.height = prefs.getInt(HEIGHT_KEY, -1);

			if ((size.width != -1) && (size.height != -1))
			{
				setSize(size);
			}
		}

		Point loc = new Point();

		loc.x = prefs.getInt(POSX_KEY, -1);
		loc.y = prefs.getInt(POSY_KEY, -1);

		// If position was not found, center the window.
		if ((loc.x == -1) || (loc.y == -1))
		{
			centerWindow();
		}
		else
		{
			setLocation(loc);
		}
	}
}
