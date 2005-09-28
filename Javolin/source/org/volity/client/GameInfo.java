package org.volity.client;

import java.net.*;
import java.util.Iterator;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.packet.DiscoverInfo;

/**
 * Represents all the info which is retrieved from a game parlor's disco info.
 * The fields returned this class may be null, but will not be empty
 * strings.
 */
public class GameInfo
{
    private String mParlorJID;
    private String mGameName;
    private String mVolityVersion;
    private URI mRulesetURI;
    private String mRulesetVersion;
    private URL mGameWebsiteURL;
    private String mParlorContactEmail;
    private String mParlorContactJID;

    /**
     * Constructs an empty GameInfo -- all fields are null, except for the
     * parlor JID itself.
     *
     * @param jid the JID of the parlor that was queried for this info
     */
    public GameInfo(String jid) {
        mParlorJID = jid;
    }

    /**
     * Constructs a GameInfo that parses a disco-info structure.
     *
     * @param jid the JID of the parlor that was queried for this info
     * @param info the disco reply
     */
    public GameInfo(String jid, DiscoverInfo info) {
        mParlorJID = jid;

        Iterator iter;

        for (iter = info.getIdentities(); iter.hasNext(); ) {
            DiscoverInfo.Identity ident = (DiscoverInfo.Identity)iter.next();
            if (ident.getCategory().equals("volity") 
                //###workaround### && ident.getType().equals("parlor")
                ) {
                mGameName = ident.getName();
            }
        }

        Form form = Form.getFormFrom(info);
        if (form != null) {
            FormField field; 

            field = form.getField("volity-version");
            if (field != null)
                mVolityVersion = (String) field.getValues().next();

            field = form.getField("ruleset");
            if (field != null) {
                try {
                    mRulesetURI = new URI((String) field.getValues().next());
                }
                catch (URISyntaxException ex) {
                    mRulesetURI = null;
                }
            }

            field = form.getField("ruleset-version");
            if (field != null)
                mRulesetVersion = (String) field.getValues().next();

            field = form.getField("website");
            if (field != null) {
                try {
                    mGameWebsiteURL = new URL((String) field.getValues().next());
                }
                catch (MalformedURLException ex) {
                    mGameWebsiteURL = null;
                }
            }

            field = form.getField("contact-email");
            if (field != null)
                mParlorContactEmail = (String) field.getValues().next();

            field = form.getField("contact-jid");
            if (field != null)
                mParlorContactJID = (String) field.getValues().next();

        }

        /**
         * If any of the fields are empty strings, change those to null. The
         * distinction is not interesting to callers -- the field exists or it
         * doesn't.
         */
        if (mGameName != null && mGameName.length() == 0)
            mGameName = null;
        if (mVolityVersion != null && mVolityVersion.length() == 0)
            mVolityVersion = null;
        if (mRulesetURI != null && mRulesetURI.toString().length() == 0)
            mRulesetURI = null;
        if (mRulesetVersion != null && mRulesetVersion.length() == 0)
            mRulesetVersion = null;
        if (mGameWebsiteURL != null && mGameWebsiteURL.toString().length() == 0)
            mGameWebsiteURL = null;
        if (mParlorContactEmail != null && mParlorContactEmail.length() == 0)
            mParlorContactEmail = null;
        if (mParlorContactJID != null && mParlorContactJID.length() == 0)
            mParlorContactJID = null;
    }

    public String getParlorJID() {
        return mParlorJID;
    }

    /** The (non-internationalized) game name. */
    public String getGameName() {
        return mGameName;
    }

    public String getVolityVersion() {
        return mVolityVersion;
    }

    public URI getRulesetURI() {
        return mRulesetURI;
    }

    public String getRulesetVersion() {
        return mRulesetVersion;
    }

    public URL getGameWebsiteURL() {
        return mGameWebsiteURL;
    }

    public String getParlorContactEmail() {
        return mParlorContactEmail;
    }

    public String getParlorContactJID() {
        return mParlorContactJID;
    }
}
