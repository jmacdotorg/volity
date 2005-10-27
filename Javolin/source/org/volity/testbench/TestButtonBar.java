package org.volity.testbench;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import org.mozilla.javascript.*;

/**
 * UI component for Testbench's toolbar. This contains "Start Game" and "End
 * Game" buttons, and a seating popup. It can also contain a bunch of
 * game-specific controls, which are defined by reading a "testbench.xml" file
 * from the UI directory.
 */
public class TestButtonBar
    implements ActionListener, SVGTestCanvas.UIListener
{
    File uiDir;
    TestUI.MessageHandler messageHandler;
    TestUI.ErrorHandler errorHandler;
    TestUI testUI;
    DebugInfo mDebugInfo;

    JPanel toolbar;
    JButton mStartGameBut;
    JButton mEndGameBut;
    JComboBox mSeatPopup;
    List mComponents = new ArrayList();
    Map mFields = new Hashtable();

    public TestButtonBar(File uiDir, 
        TestUI.MessageHandler messageHandler,
        TestUI.ErrorHandler errorHandler) {

        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.uiDir = uiDir;

        mDebugInfo = new DebugInfo(uiDir);
        buildUI(true);
    }

    /**
     * Get the JComponent which represents the toolbar. (It happens to be a
     * JPanel.)
     */
    public JComponent getToolbar() {
        return toolbar;
    }

    /**
     * UIListener interface method implementation.
     */
    public void newUI(TestUI ui) {
        testUI = ui;
    }

    /**
     * ActionListener interface method implementation.
     *
     * @param ev  The ActionEvent received.
     */
    public void actionPerformed(ActionEvent ev) {
        if (ev.getSource() == mStartGameBut) {
            //### track "ref" state

            String seatid = (String)mSeatPopup.getSelectedItem();
            if (seatid.equals("") || seatid.equals(UNSEATED_ITEM))
                seatid = null;
            testUI.setCurrentSeat(seatid);

            String tmpmsg = "player not seated";
            if (seatid != null)
                tmpmsg = "player in seat \"" + seatid + "\"";
            writeMessageText("Starting game (" + tmpmsg + ")");

	    Object method = testUI.game.get("START", testUI.scope);
            List params = new ArrayList(0);
	    try {
                if (method != Scriptable.NOT_FOUND) {
                    testUI.callUIMethod((Function) method, params, null);
                }
	    }
            catch (Exception ex) {
                errorHandler.error(ex, "game.START failed");
            }
        }
        else if (ev.getSource() == mEndGameBut) {
            //### track "ref" state
            writeMessageText("Ending game");

	    Object method = testUI.game.get("END", testUI.scope);
            List params = new ArrayList(0);
	    try {
                if (method != Scriptable.NOT_FOUND) {
                    testUI.callUIMethod((Function) method, params, null);
                }
	    }
            catch (Exception ex) {
                errorHandler.error(ex, "game.END failed");
            }
        }
        else {
            int ix;

            for (ix = 0; ix < mComponents.size(); ix++) {
                if (ev.getSource() == mComponents.get(ix))
                    break;
            }

            List ls = mDebugInfo.getCommandList();
            if (ix < mComponents.size() && ix < ls.size()) {
                DebugInfo.Command cmd = (DebugInfo.Command)(ls.get(ix));
                if (cmd.code != null) {
                    writeMessageText("Performing debug command \"" + cmd.label + "\".");
                    String st = interpolateFields(cmd.code);
                    if (testUI != null)
                        testUI.loadString(st, "Debug command \"" + cmd.label + "\"");
                }
            }


        }
    }

    /**
     * Reload the testbench.xml file, and update the controls in the buttonbar
     * to match.
     */
    public void reload() {
        mDebugInfo = new DebugInfo(uiDir);
        buildUI(false);
    }

    /**
     * Ad-hoc class to hold a couple of values. For buttonbar internal use.
     */
    private class FieldPair {
        DebugInfo.Command cmd;
        JTextField field;
        FieldPair(DebugInfo.Command cmd, JTextField field) {
            this.cmd = cmd;
            this.field = field;
        }
    }

    /**
     * Take a string, and substitute in field values for "$name" variables.
     * Use "$$" for a literal "$".
     *
     * String fields turn into double-quoted string literals; int fields turn
     * into int literals. Dollar signs inside string literals are *not*
     * protected. Therefore, "hello $X" is a bad idea; it will turn into
     * "hello "xfield"".
     *
     * @param st the string to convert.
     */
    public String interpolateFields(String st) {
        StringBuffer buf = new StringBuffer(st);
        int ix = 0;
        int jx;

        while (true) {
            ix = buf.indexOf("$", ix);
            if (ix < 0) {
                break;
            }

            if (ix+1 < buf.length()
                && buf.charAt(ix+1) == '$') {
                /* Accept "$$" as "$". */
                buf.replace(ix, ix+2, "$");
                ix += 1;
                continue;
            }

            // This would be faster with a regex
            for (jx=ix+1; jx<buf.length(); jx++) {
                char ch = buf.charAt(jx);
                if ((ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || (ch == '_'))
                    continue;
                break;
            }

            String name = buf.substring(ix+1, jx);
            if (!mFields.containsKey(name)) {
                ix = jx;
                continue;
            }
            FieldPair pair = (FieldPair)mFields.get(name);

            String val = pair.field.getText();
            switch (pair.cmd.datatype) {
            case DebugInfo.DATTYP_STRING:
                /* We must escape quotes and backslashes, and then put quotes
                 * aorund the whole thing, to create a valid Javascript string
                 * literal. */
                Pattern pat = Pattern.compile("([\\\"\\\\])");
                Matcher matcher = pat.matcher(val);
                String escapedval = matcher.replaceAll("\\\\$1");
                val = "\"" + escapedval + "\"";
                break;
            case DebugInfo.DATTYP_INT:
                JFormattedTextField ffield = (JFormattedTextField)pair.field;
                int intval = ((Number)ffield.getValue()).intValue();
                val = String.valueOf(intval);
                break;
            default:
                val = "";
                break;
            }

            buf.replace(ix, jx, val);
            ix += val.length();
        }

        return buf.toString();
    }

    /**
     * Wrapper function to print a message string. 
     *
     * @param message the message.
     */
    private void writeMessageText(String message) {
        messageHandler.print(message);
    }

    /**
     * Turn all the controls in the buttonbar on or off. 
     *
     * The controls are disabled when the bar is first created or reloaded;
     * this is because the SVG file has just been loaded too, and is not yet
     * visible. When the SVG is first drawn, setAllEnabled(true) should be
     * called.
     *
     * @param val on or off?
     */
    public void setAllEnabled(boolean val) {
        mStartGameBut.setEnabled(val);
        mEndGameBut.setEnabled(val);
        mSeatPopup.setEnabled(val);

        for (int ix=0; ix<mComponents.size(); ix++) {
            JComponent cmp = (JComponent)mComponents.get(ix);
            cmp.setEnabled(val);
        }
    }

    private final static String START_GAME_LABEL = "Start Game";
    private final static String END_GAME_LABEL = "End Game";
    private final static String UNSEATED_ITEM = "(none)";

    /**
     * Whack together the UI. This BuildUI routine is not quite in line with
     * the Javolin standard -- sorry. It is called both at construction time,
     * and when the UI is reloaded. In the latter case, the toolbar already
     * exists, but its contents must be cleaned out and rebuilt (because
     * testbench.xml might have changed).
     *
     * @param firsttime building a new toolbar, or reloading it?
     */
    private void buildUI(boolean firsttime) {
        if (firsttime) {
            toolbar = new JPanel(new RestrictedWidthFlowLayout(FlowLayout.LEFT));
        }
        else {
            toolbar.removeAll();
        }

        mComponents.clear();
        mFields.clear();

        mStartGameBut = new JButton(START_GAME_LABEL);
        mStartGameBut.addActionListener(this);
        toolbar.add(mStartGameBut);
        
        mEndGameBut = new JButton(END_GAME_LABEL);
        mEndGameBut.addActionListener(this);
        toolbar.add(mEndGameBut);
 
        List ls = mDebugInfo.getSeatList();
        mSeatPopup = new JComboBox();
        mSeatPopup.setEditable(true);
        mSeatPopup.addItem(UNSEATED_ITEM);
        for (int ix = 0; ix < ls.size(); ix++) {
            String seatid = (String)(ls.get(ix));
            mSeatPopup.addItem(seatid);
        }
        toolbar.add(mSeatPopup);

        ls = mDebugInfo.getCommandList();
        for (int ix = 0; ix < ls.size(); ix++) {
            DebugInfo.Command cmd = (DebugInfo.Command)(ls.get(ix));
            switch (cmd.type) {
            case DebugInfo.CMD_BUTTON:
                JButton but = new JButton(cmd.label);
                but.addActionListener(this);
                toolbar.add(but);
                mComponents.add(but);
                break;
            case DebugInfo.CMD_FIELD:
                JPanel pair = new JPanel();
                pair.setLayout(new BoxLayout(pair, BoxLayout.X_AXIS));
                JLabel label = new JLabel(cmd.label + ":");
                pair.add(label);
                JTextField field;
                if (cmd.datatype == DebugInfo.DATTYP_INT) {
                    field = new JFormattedTextField(new Integer(0));
                    field.setColumns(4);
                }
                else {
                    field = new JTextField(4);
                }
                pair.add(field);

                toolbar.add(pair);
                mComponents.add(field);
                mFields.put(cmd.label, new FieldPair(cmd, field));
                break;
            }
        }

        setAllEnabled(false);
        if (!firsttime) {
            toolbar.revalidate();
        }
    }
}
