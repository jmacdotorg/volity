package org.volity.testbench;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.prefs.*;
import javax.swing.*;

public class TestMessagePane
    implements SVGTestCanvas.UIListener
{
    private final static String NODENAME = "TestMessagePane";
    private final static String CMD_HISTORY = "CmdHistory";
    private final static int MAX_HISTORY_SAVED = 20;

    private JTextArea mInputText;
    private TestButtonBar mButtonBar;
    private TestUI ui;
    private List history;
    private int historyPos;

    private TestUI.MessageHandler messageHandler;

    public TestMessagePane(TestUI.MessageHandler inMessageHandler, TestButtonBar buttonBar) {
        messageHandler = inMessageHandler;
        mButtonBar = buttonBar;

        history = new ArrayList();
        historyPos = 0;

        buildUI();

        // Send message when user presses Enter while editing input text
        mInputText.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    String st = mInputText.getText();
                    mInputText.setText("");

                    /* Don't save the empty string in command history. Don't
                     * save a string if it's identical to the last string.
                     */
                    int cursize = history.size();
                    if ((cursize == 0 || !st.equals(history.get(cursize-1)))
                        && !st.equals("")) {
                        history.add(st);
                    }
                    historyPos = history.size();

                    if (st.startsWith("?")) {
                        st = st.substring(1);
                        messageHandler.print("Printing debug expression: " + st);
                        st = "literalmessage(\"\" + (" + st + "));";   
                    }
                    else {
                        messageHandler.print("Performing debug command: " + st);
                    }

                    st = mButtonBar.interpolateFields(st);
                    if (ui != null)
                        ui.loadString(st, "Debug command");
                }
            });
        
        mInputText.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
            new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) {
                    if (historyPos > 0) {
                        historyPos--;
                        String st = (String)history.get(historyPos);
                        mInputText.setText(st);
                    }
                }
            });

        mInputText.getInputMap().put(
            KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
            new AbstractAction()
            {
                public void actionPerformed(ActionEvent e) {
                    if (historyPos < history.size()-1) {
                        historyPos++;
                        String st = (String)history.get(historyPos);
                        mInputText.setText(st);
                    }
                    else if (historyPos == history.size()-1) {
                        historyPos++;
                        mInputText.setText("");
                    }
                }
            });

        restoreHistory();
    }

    public JComponent getComponent() {
        return mInputText;
    }

    /**
     * UIListener interface method implementation.
     */
    public void newUI(TestUI ui) {
        this.ui = ui;
    }

    public void saveHistory() {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        StringBuffer buf = new StringBuffer();

        int ix = history.size() - MAX_HISTORY_SAVED;
        if (ix < 0)
            ix = 0;

        for (; ix<history.size(); ix++) {
            String st = (String)history.get(ix);
            buf.append(st);
            buf.append("\n");
        }

        prefs.put(CMD_HISTORY, buf.toString());
    }

    public void restoreHistory() {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        String buf = prefs.get(CMD_HISTORY, "");
        String arr[] = buf.split("\n");

        for (int ix = 0; ix < arr.length; ix++) {
            history.add(arr[ix]);
        }
        historyPos = history.size();
    }

    /**
     * Creates the UI controls.
     */
    private void buildUI()
    {
        mInputText = new JTextArea();
        mInputText.setLineWrap(true);
        mInputText.setWrapStyleWord(true);
        mInputText.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
    }
}
