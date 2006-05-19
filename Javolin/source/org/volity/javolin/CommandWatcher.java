package org.volity.javolin;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.volity.client.data.CommandStub;

/**
 * A class which opens a TCP socket and listens for CommandStubs (in the form
 * of URLs). This operates in its own thread.
 */
public class CommandWatcher
    implements Runnable
{
    static public final int WATCHER_PORT = 9007;

    private Object mThreadLock = new Object(); // covers the following:
    private Thread mThread = null;
    private ServerSocket mSocket = null;

    /** 
     * Create a watcher. It opens a socket and begins running its own thread to
     * watch it.
     */
    protected CommandWatcher() throws IOException {
        mSocket = new ServerSocket();
        mSocket.setReuseAddress(true); /* SO_REUSEADDR */

        InetAddress addr = InetAddress.getByAddress(null, new byte[]{ 127,0,0,1 });
        assert addr.isLoopbackAddress() : "Address is not loopback";
        InetSocketAddress sockaddr = new InetSocketAddress(addr, WATCHER_PORT);
        mSocket.bind(sockaddr);

        mThread = new Thread(this);
        mThread.start();
    }

    /** 
     * Kill the watcher thread. This method blocks until the thread is gone and
     * the socket is closed.
     */
    protected void stop() {
        synchronized (mThreadLock) {
            try {
                if (mSocket != null) 
                    mSocket.close();
            }
            catch (IOException ex) {
                // ignore error.
            }

            // Wait until the thread exits.
            try {
                while (mSocket != null) {
                    mThreadLock.wait();
                }
            }
            catch (InterruptedException ex) {
                // never mind.
            }
        }
    }

    /**
     * Implements Runnable interface, for the socket thread.
     */
    public void run() {
        while (mSocket != null && !mSocket.isClosed()) {
            try {
                Socket socket = mSocket.accept();
                InetAddress addr = socket.getInetAddress();
                // Only allow connections from ourself
                if (addr.isLoopbackAddress()) {
                    new CommandHandler(socket);
                }
            }
            catch (IOException ex) {
                // Give up on socket
                break;
            }
        }

        synchronized (mThreadLock) {
            // Close the socket before the thread exits.
            try {
                if (mSocket != null)
                    mSocket.close();
            }
            catch (IOException ex) {
                // ignore error.
            }
            mSocket = null;
            mThreadLock.notifyAll();
        }
    }

    /**
     * Class which handles one incoming connection. This uses a thread to wait
     * for socket input. Under normal circumstances, the thread will exist only
     * briefly.
     *
     * Nothing in the application-shutdown process tries to kill these threads.
     */
    protected static class CommandHandler implements Runnable {
        static Charset ascii = Charset.forName("US-ASCII");

        Socket mSocket;

        private Thread mThread = null;

        protected CommandHandler(Socket sock) {
            mSocket = sock;
            mThread = new Thread(this);
            mThread.start();
        }

        /** Do the work of executing a URL that has been received. */
        protected void execute(String val) {
            assert (SwingUtilities.isEventDispatchThread()) : "not in UI thread";

            try {
                URL url = new URL(val);
                CommandStub stub = CommandStub.parse(url);
                JavolinApp.getSoleJavolinApp().doOpenFile(stub);
            }
            catch (MalformedURLException ex) {
                new ErrorWrapper(ex);
                JOptionPane.showMessageDialog(null,
                    "Received illegal URL:\n" + val,
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
            catch (CommandStub.CommandStubException ex) {
                new ErrorWrapper(ex);
                JOptionPane.showMessageDialog(null,
                    "Unable to parse command URL:\n" + val,
                    JavolinApp.getAppName() + ": Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }

        /**
         * The work thread of the socket reader. This reads data out of the
         * socket. Every linebreak-terminated string is a URL to execute. When
         * the socket is closed, the thread shuts down.
         */
        public void run() {
            InputStream stream = null;
            BufferedReader reader = null;

            try {
                mSocket.setSoTimeout(0);

                stream = mSocket.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream, ascii));

                while (true) {
                    final String val = reader.readLine();
                    if (val == null)
                        break;

                    // Invoke into the Swing thread.
                    SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                execute(val);
                            }
                        });
                }
            }
            catch (IOException ex) {
                // ignore error, but break out of the loop.
            }

            try {
                if (reader != null)
                    reader.close();
                if (stream != null)
                    stream.close();
            }
            catch (IOException ex) {
                // ignore error.
            }

            try {
                mSocket.close();
            }
            catch (IOException ex) {
                // ignore error.
            }

            mSocket = null;
        }
    }

    /**
     * Try to transmit a URL string into a waiting CommandWatcher (in another
     * process, or indeed this one if there is one operating). Returns whether
     * the command was successfully sent.
     */
    public static boolean tryCommand(String val) {
        Socket socket = null;
        OutputStream stream = null;
        BufferedWriter writer = null;
        InetAddress addr = null;

        boolean result = false;

        try {
            addr = InetAddress.getByAddress(null, new byte[]{ 127,0,0,1 });
            assert addr.isLoopbackAddress() : "Address is not loopback";
            socket = new Socket(addr, WATCHER_PORT);
            stream = socket.getOutputStream();
            writer = new BufferedWriter(new OutputStreamWriter(stream)); 

            val = val + "\n";
            writer.write(val, 0, val.length());
            writer.flush();
            result = true;
        }
        catch (IOException ex) {
            // failure.
        }

        try {
            if (writer != null)
                writer.close();
            if (stream != null)
                stream.close();
        }
        catch (IOException ex) {
            // ignore error.
        }

        try {
            if (socket != null)
                socket.close();
        }
        catch (IOException ex) {
            // ignore error.
        }

        return result;
    }
}
