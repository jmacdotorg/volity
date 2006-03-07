package org.volity.javolin;

/**
 * A window which supports the Close menu item (via calling win.dispose())
 * should implement this interface.
 */
public interface CloseableWindow {

    /**
     * A window which supports the Close menu item (via calling a custom
     * win.closeWindow() method) should implement this subinterface.
     */
    public interface Custom extends CloseableWindow {
        public void closeWindow();
    }

}
