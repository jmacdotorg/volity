package org.volity.client.translate;

import java.util.List;

public class TokenTranslationHandler {
    /**
     * This class represents an object which accepts failure tokens,
     * runs them through a TranslateToken to produce a translation
     * string, and then does something with the string. 
     *
     * Typically, you will subclass TokenTranslationHandler and
     * override the output() method. If you use this class as-is, the
     * default output() method is to print the string to stdout
     * (followed by a newline).
     */
    
    protected TranslateToken translator;

    public TokenTranslationHandler(TranslateToken translator) {
        this.translator = translator;
    }

    public TranslateToken getTranslator() {
        return translator;
    }

    public void handle(String tokens[]) {
        output(translator.translate(tokens));
    }

    public void handle(String token) {
        output(translator.translate(token));
    }

    public void handle(String token1, String token2) {
        output(translator.translate(token1, token2));
    }

    public void handle(List tokens) {
        output(translator.translate(tokens));
    }

    public void handle(TokenFailure ex) {
        output(translator.translate(ex));
    }

    public void output(String msg) {
        System.out.println(msg);
    }
}

