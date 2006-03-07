package org.volity.client.translate;

import java.util.ArrayList;
import java.util.List;

/**
 * An exception thrown when a remote procedure call comes back with a
 * failure token.
 *
 * You can pass a TokenFailure to the TranslateToken module, to get
 * its natural-language translation.
 *
 * @author Andrew Plotkin (erkyrath@eblong.com)
 */
public class TokenFailure extends Exception {

    public TokenFailure(String singletoken) {
        this.tokenlist = new ArrayList();
        tokenlist.add(singletoken);
    }

    public TokenFailure(List tokenlist) {
        this.tokenlist = tokenlist;
    }

    protected List tokenlist;
    public List getTokens() { return tokenlist; }
}
