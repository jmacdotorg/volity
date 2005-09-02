package org.volity.testbench;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class TestMessagePane
    implements SVGTestCanvas.UIListener
{
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

                    history.add(st);
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
