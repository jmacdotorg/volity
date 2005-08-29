package org.volity.testbench;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.*;
import javax.swing.*;
import org.mozilla.javascript.*;
import org.volity.client.GameUI;

public class TestButtonBar
    implements ActionListener, SVGTestCanvas.UIListener
{
    File uiDir;
    TestUI.MessageHandler messageHandler;
    GameUI.ErrorHandler errorHandler;
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
        GameUI.ErrorHandler errorHandler) {

        this.messageHandler = messageHandler;
        this.errorHandler = errorHandler;
        this.uiDir = uiDir;

        mDebugInfo = new DebugInfo(uiDir);
        buildUI(true);
    }

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
                    testUI.callUIMethod((Function) method, params);
                }
	    }
            catch (Exception ex) {
                errorHandler.error(ex);
                writeMessageText("game.START failed: " + ex.toString());
            }
        }
        else if (ev.getSource() == mEndGameBut) {
            //### track "ref" state
            writeMessageText("Ending game");

	    Object method = testUI.game.get("END", testUI.scope);
            List params = new ArrayList(0);
	    try {
                if (method != Scriptable.NOT_FOUND) {
                    testUI.callUIMethod((Function) method, params);
                }
	    }
            catch (Exception ex) {
                errorHandler.error(ex);
                writeMessageText("game.END failed: " + ex.toString());
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
                    try {
                        String st = interpolateFields(cmd.code);
                        testUI.loadString(st);
                    }
                    catch (Exception ex) {
                        errorHandler.error(ex);
                        writeMessageText("Debug command \"" + cmd.label + "\" failed: " + ex.toString());
                    }
                }
            }


        }
    }

    public void reload() {
        mDebugInfo = new DebugInfo(uiDir);
        buildUI(false);
    }

    private class FieldPair {
        DebugInfo.Command cmd;
        JTextField field;
        FieldPair(DebugInfo.Command cmd, JTextField field) {
            this.cmd = cmd;
            this.field = field;
        }
    }

    private String interpolateFields(String st) {
        int pos;
        StringBuffer buf = new StringBuffer(st);
        int ix = 0;
        int jx;

        while (true) {
            pos = buf.indexOf("$", ix);
            if (pos < 0) {
                break;
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
                /* We must escape quotes and backslashes, to create a valid
                 * Javascript string literal. */
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

    private void writeMessageText(String message) {
        messageHandler.print(message);
    }

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
