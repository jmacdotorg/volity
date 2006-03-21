package org.volity.client.comm;

import java.util.Iterator;
import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smackx.packet.DataForm;
import org.jivesoftware.smackx.FormField;

/** 
 * The packet extension for <volity> message attachments. This wraps a
 * DataForm, and that's all it does.
 */
public class FormPacketExtension extends DefaultPacketExtension
{
    public static final String NAME = "volity";
    public static final String NAMESPACE = "http://volity.org/protocol/form";

    DataForm mForm;

    /**
     * Construct a FormPacketExtension, given a DataForm.
     *
     * If you are preparing a FormPacketExtension for sending, the DataForm
     * must be of type "result".
     */
    public FormPacketExtension(DataForm form) {
        super(NAME, NAMESPACE);

        mForm = form;
    }

    /** Get the DataForm. */
    public DataForm getForm() {
        return mForm;
    }

    /** Get the type (the FORM_TYPE, that is) of the DataForm. */
    public String getFormType() {
        FormField field = getField("FORM_TYPE");
        if (field != null && field.getType() != null 
            && field.getType().equals(FormField.TYPE_HIDDEN)) {
            String formtype = (String) field.getValues().next();
            return formtype;
        }
        return null;
    }

    /** Return the fields of the DataForm. */
    public Iterator getFields() {
        return mForm.getFields();
    }

    /**
     * Returns the field of the form whose variable matches the specified
     * variable. The fields of type FIXED will never be returned since they do
     * not specify a variable.
     *
     * (This is a clone of the getField method in Smack's Form class.)
     * 
     * @param variable the variable to look for in the form fields. 
     * @return the field of the form whose variable matches the specified
     * variable.
     */
    public FormField getField(String variable) {
        if (variable == null || variable.equals("")) {
            throw new IllegalArgumentException("Variable must not be null or blank.");
        }

        // Look for the field whose variable matches the requested variable
        FormField field;
        for (Iterator it=getFields();it.hasNext();) {
            field = (FormField)it.next();
            if (variable.equals(field.getVariable())) {
                return field;
            }
        }
        return null;
    }

    /** Generate XML for this extension. */
    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<").append(getElementName());
        buf.append(" xmlns=\"").append(getNamespace()).append("\"");
        buf.append(">");
        buf.append(mForm.toXML());
        buf.append("</").append(getElementName());
        buf.append(">");
        return buf.toString();
    }

}
