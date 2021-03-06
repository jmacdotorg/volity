package org.volity.testbench;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.prefs.*;
import java.util.zip.ZipException;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import org.apache.batik.bridge.*;
import org.apache.batik.util.gui.JErrorPane;
import org.volity.client.GameUI;
import org.volity.client.translate.TranslateToken;
import org.volity.javolin.ErrorWrapper;
import org.volity.javolin.LogTextPanel;
import org.volity.javolin.SizeAndPositionSaver;
import org.volity.javolin.game.UIFileCache;

/**
 * The main application class of Testbench.
 */
public class TestbenchApp extends JFrame
                                  implements ActionListener
{
    private static UIFileCache sUIFileCache = new UIFileCache();

    private final static String APPNAME = "Testbench";
    private final static String NODENAME = "MainAppWin";

    private final static String SVGUIDIR_KEY = "SVG_UI_Dir";
            
    private final static String MENUCMD_QUIT = "Exit";
    private final static String MENUCMD_LASTEXCEPTION = "Show Last Exception";
    private final static String MENUCMD_MEMUSAGE = "Show Memory Usage";
    private final static String MENUCMD_RELOAD = "Reload";
    private final static String MENUCMD_METADATA = "UI Metadata...";

    private final static String LOG_SPLIT_POS = "LogSplitPos";
    private final static String CHAT_SPLIT_POS = "ChatSplitPos";

    private File mUIDir;
    private File mUIFile;
    private DebugInfo mDebugInfo;
    private TranslateToken mTranslator;
    private TestUI.ErrorHandler errorHandler;
    private TestButtonBar mButtonBar;

    private SVGTestCanvas mViewport;
    private TestMessagePane mInputPane;
    private JSplitPane mLogSplitter;
    private JSplitPane mChatSplitter;
    private LogTextPanel mMessageText;
    private JMenuItem mReloadMenuItem;
    private JMenuItem mMetadataMenuItem;
    private JMenuItem mLastExceptionMenuItem;
    private JMenuItem mMemUsageMenuItem;
    private JMenuItem mQuitMenuItem;

    private SizeAndPositionSaver mSizePosSaver;
    private SimpleDateFormat mTimeStampFormat;

    private Throwable lastException = null;

    /**
     * The main program for the TestbenchApp class.
     *
     * @param args  The command line arguments.
     */
    public static void main(String[] args)
    {
        String filename = null;
        File ui = null;

        for (int ix=0; ix<args.length; ix++) {
            if (args[ix].equals("--lang") && (ix < args.length-1)) {
                ix++;
                Locale.setDefault(new Locale(args[ix]));
                continue;
            }
            if (args[ix].startsWith("--")) {
                System.err.println(getAppName() + ": Unknown option: " + args[ix]);
                System.exit(1);
            }
            filename = args[ix];
        }

        Locale localeApp = Locale.getDefault();
        TranslateToken.setLocale(localeApp);

        if (filename != null) {
            ui = new File(filename);
        }
        else {
            JFileChooser filer = new JFileChooser();

            // Set directory in file chooser to UI directory from prefs 
            Preferences prefs = 
                Preferences.userNodeForPackage(TestbenchApp.class).node(NODENAME);
            
            String dir = prefs.get(SVGUIDIR_KEY, "");
            if (!dir.equals("")) {
                filer.setCurrentDirectory(new File(dir));
            }

            filer.setDialogTitle("Select SVG UI");
            filer.setApproveButtonText("Select");
            filer.addChoosableFileFilter(new FileFilter() {
                    public boolean accept(File file) {
                        if (file.isDirectory())
                            return true;
                        String name = file.getName().toLowerCase();
                        if (name.endsWith(".zip"))
                            return true;
                        if (name.endsWith(".svg"))
                            return true;
                        return false;
                    }
                    public String getDescription() {
                        return "UI files (*.zip, *.svg)";
                    }
                });
            int val = filer.showOpenDialog(null);

            if (val != JFileChooser.APPROVE_OPTION) {
                System.exit(1);
            }
            ui = filer.getSelectedFile();

            // Save UI directory to prefs
            try {
                prefs.put(SVGUIDIR_KEY, filer.getCurrentDirectory().getCanonicalPath());
            } catch (IOException ex) {
                // getCanonicalPath() failed; can't do anything about it
            }
        }

        if (!ui.exists()) {
            System.err.println(getAppName() + ": " + ui.getPath() + " does not exist");
            System.exit(1);
        }

        /*
         * Make sure we can reach the handlers for our special Volity URLs.
         * Testbench only uses the protocol handlers, not the content handlers.
         */
        String val = System.getProperty("java.protocol.handler.pkgs");
        if (val == null)
            val = "org.volity.client.protocols";
        else
            val = val + "|org.volity.client.protocols";
        System.setProperty("java.protocol.handler.pkgs", val);

        // Set the look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
        }

        // Set appropriate properties when running on Mac
        if (isRunningOnMac()) {
            // Make .app and .pkg files non-traversable as directories in AWT
            // file choosers
            System.setProperty("com.apple.macos.use-file-dialog-packages", "true");
        }

        try {
            TestbenchApp mainApp = new TestbenchApp(ui);
            mainApp.start();
        }
        catch (Exception ex) {
            System.err.println(getAppName() + ": " + ex.toString());
            System.exit(1);
        }
    }

    /**
     * Constructor.
     */
    public TestbenchApp(File ui)
        throws MalformedURLException, IOException, ZipException
    {
        setTitle(APPNAME);

        File uiDir, uiFile;

        if (ui.isDirectory()) {
            uiDir = ui;
            uiFile = null;
        }
        else {
            boolean iszip = ui.toString().toLowerCase().endsWith(".zip");
            if (iszip) {
                uiDir = sUIFileCache.getUIDir(ui.toURI().toURL());
                uiFile = null;
            }
            else {
                uiFile = ui;
                uiDir = uiFile.getParentFile();
            }
        }

        if (uiFile == null) {
            uiDir = UIFileCache.locateTopDirectory(uiDir);
            uiFile = UIFileCache.locateMainFile(uiDir);
        }

        URL uiMainUrl = uiFile.toURI().toURL();

        mUIDir = uiDir;
        mUIFile = uiFile;
        mTranslator = new TranslateToken(UIFileCache.findFileCaseless(uiDir, "locale"));

        GameUI.MessageHandler messageHandler = new GameUI.MessageHandler() {
                public void print(String msg) {
                    writeMessageText(msg);
                }
            };
        errorHandler = new TestUI.ErrorHandler() {
                public void error(Throwable ex) {
                    error(ex, null);
                }
                public void error(Throwable ex, String prefix) {
                    noticeException(ex);
                    String msg = ex.toString();
                    msg = ErrorWrapper.fixLineNumbers(msg);
                    if (prefix != null)
                        msg = prefix + ": " + msg;
                    writeMessageText(msg);
                }
            };
            
        mDebugInfo = new DebugInfo(uiDir);

        mViewport = new SVGTestCanvas(uiMainUrl, mDebugInfo, mTranslator,
            messageHandler, errorHandler);

        mButtonBar = new TestButtonBar(mDebugInfo, messageHandler, errorHandler);
        mViewport.addUIListener(mButtonBar);
        
        mViewport.addUpdateManagerListener(
            new UpdateManagerAdapter() {
                public void managerStarted(UpdateManagerEvent ev) {
                    mButtonBar.setAllEnabled(true);
                }
            });

        mInputPane = new TestMessagePane(messageHandler, mButtonBar);
        mViewport.addUIListener(mInputPane);

        buildUI();

        setSize(500, 600);
        mSizePosSaver = new SizeAndPositionSaver(this, NODENAME);
        restoreWindowState();

        mTimeStampFormat = new SimpleDateFormat("HH:mm:ss");

        // Handle closing the window to quit the app
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(
            new WindowAdapter()
            {
                public void windowClosing(WindowEvent we)
                {
                    saveWindowState();
                    doQuit();
                }

                public void windowOpened(WindowEvent we)
                {
                    // Give focus to input text area when the window is created
                    mInputPane.getComponent().requestFocusInWindow();
                }
            });

        // Save window size and position whenever it is moved or resized
        addComponentListener(
            new ComponentAdapter()
            {
                public void componentMoved(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }

                public void componentResized(ComponentEvent e)
                {
                    mSizePosSaver.saveSizeAndPosition();
                }
            });

        setVisible(true);
    }

    /**
     * Performs tasks that should occur immediately after launch, but which
     * don't seem appropriate to put in the constructor.
     */
    private void start()
    {
    }

    /**
     * ActionListener interface method implementation.
     *
     * @param ev  The ActionEvent received.
     */
    public void actionPerformed(ActionEvent ev)
    {
        Object source = ev.getSource();

        if (source == null) {
            // We don't want surprises; some of the menu items may be null.
            return; 
        }

        if (source == mQuitMenuItem) {
            doQuit();
        } 
        else if (source == mReloadMenuItem) {
            writeMessageText("Reloading UI files...");
            mTranslator.clearCache();
            mDebugInfo = new DebugInfo(mUIDir);
            mButtonBar.reload(mDebugInfo);
            mViewport.reloadUI(mDebugInfo);
            MetadataDialog.reloadSoleMetadataDialog();
        }
        else if (source == mMetadataMenuItem) {
            MetadataDialog box = MetadataDialog.getSoleMetadataDialog(mUIFile,
                errorHandler);
            box.setVisible(true);        
        }
        else if (source == mLastExceptionMenuItem) {
            if (lastException instanceof Exception) {
                JErrorPane pane = new JErrorPane(
                    (Exception)lastException, 
                    JOptionPane.ERROR_MESSAGE);
                JDialog dialog = pane.createDialog(this, 
                    APPNAME+" Exception");
                dialog.setModal(false);
                dialog.setVisible(true);
            }
            else {
                writeMessageText("Last exception was not an Exception: " + lastException.toString());
            }
        }
        else if (source == mMemUsageMenuItem) {
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long total = runtime.totalMemory();
            long free = runtime.freeMemory();
            long max = runtime.maxMemory();
            System.out.println("Memory used: " + String.valueOf(total-free)
                + " of " + String.valueOf(total)
                + " (limit " + String.valueOf(max) + ")");
        }
    }

    /**
     * Handler for the Quit menu item.
     */
    private void doQuit()
    {
        System.exit(0);
    }

    /**
     * Appends the given message text to the message text area.
     *
     * @param message   The text of the message.
     */
    private void writeMessageText(String message)
    {
        // Append time stamp
        Date now = new Date();
        mMessageText.append("[" + mTimeStampFormat.format(now) + "]  ", 
            Color.GRAY);

        mMessageText.append(message + "\n", Color.BLACK);
    }

    private void noticeException(Throwable ex) 
    {
        lastException = ex;
        if (mLastExceptionMenuItem != null)
            mLastExceptionMenuItem.setEnabled(true);
    }

    /**
     * Gets the name of the application, suitable for display to the user in
     * dialog box titles and other appropriate places.
     *
     * @return   The name of the application.
     */
    public static String getAppName()
    {
        return APPNAME;
    }

    /**
     * Tells whether Testbench is running on a Mac platform.
     * (Copied from JavolinApp)
     *
     * @return true if Testbench is currently running on a Mac, false if
     * running on another platform.
     */
    private static boolean isRunningOnMac()
    {
        return (System.getProperty("mrj.version") != null); // Apple recommended test for Mac
    }

    /**
     *  Creates and sets up the menus for the application.
     */
    private void setupAppMenus()
    {
        // Platform independent accelerator key
        int keyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        // File menu
        JMenu fileMenu = new JMenu("File");
        setPlatformMnemonic(fileMenu, KeyEvent.VK_F);

        mReloadMenuItem = new JMenuItem(MENUCMD_RELOAD);
        mReloadMenuItem.addActionListener(this);
        mReloadMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, keyMask));
        setPlatformMnemonic(mReloadMenuItem, KeyEvent.VK_R);
        fileMenu.add(mReloadMenuItem);

        mMetadataMenuItem = new JMenuItem(MENUCMD_METADATA);
        mMetadataMenuItem.addActionListener(this);
        mMetadataMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, keyMask));
        setPlatformMnemonic(mMetadataMenuItem, KeyEvent.VK_M);
        fileMenu.add(mMetadataMenuItem);

        mLastExceptionMenuItem = new JMenuItem(MENUCMD_LASTEXCEPTION);
        mLastExceptionMenuItem.addActionListener(this);
        setPlatformMnemonic(mLastExceptionMenuItem, KeyEvent.VK_E);
        mLastExceptionMenuItem.setEnabled(lastException != null);
        fileMenu.add(mLastExceptionMenuItem);

        if (true) {
            mMemUsageMenuItem = new JMenuItem(MENUCMD_MEMUSAGE);
            mMemUsageMenuItem.addActionListener(this);
            mMemUsageMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, keyMask));
            setPlatformMnemonic(mMemUsageMenuItem, KeyEvent.VK_M);
            fileMenu.add(mMemUsageMenuItem);
        }

        mQuitMenuItem = new JMenuItem(MENUCMD_QUIT);
        mQuitMenuItem.addActionListener(this);
        setPlatformMnemonic(mQuitMenuItem, KeyEvent.VK_X);
        fileMenu.add(mQuitMenuItem);

        // Create menu bar
        JMenuBar theMenuBar = new JMenuBar();
        theMenuBar.add(fileMenu);
        setJMenuBar(theMenuBar);
    }

    /**
     * Saves window state to the preferences storage, including window size and
     * position, and splitter bar positions.
     */
    private void saveWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.saveSizeAndPosition();
        mInputPane.saveHistory();

        prefs.putInt(LOG_SPLIT_POS, mLogSplitter.getDividerLocation());
        prefs.putInt(CHAT_SPLIT_POS, mChatSplitter.getDividerLocation());
    }

    /**
     * Restores window state from the preferences storage, including window
     * size and position, and splitter bar positions.
     */
    private void restoreWindowState()
    {
        Preferences prefs = Preferences.userNodeForPackage(getClass()).node(NODENAME);

        mSizePosSaver.restoreSizeAndPosition();

        mChatSplitter.setDividerLocation(prefs.getInt(CHAT_SPLIT_POS, 50));
        mLogSplitter.setDividerLocation(prefs.getInt(LOG_SPLIT_POS,
            getHeight() - 200));
    }

    /**
     * Helper method for setUpAppMenus. Assigns a keyboard mnemonic to a menu
     * or menu item, but only if not running on the Mac platform.
     *
     * @param item  The menu or menu item to assign the mnemonic to
     * @param key   The keyboard mnemonic.
     */
    private void setPlatformMnemonic(JMenuItem item, int key)
    {
        if (!isRunningOnMac())
        {
            item.setMnemonic(key);
        }
    }

    /**
     * Populates the frame with UI controls.
     */
    private void buildUI()
    {
        Container cPane = getContentPane();
        cPane.setLayout(new BorderLayout());

        // Create toolbar
        JComponent toolbar = mButtonBar.getToolbar();
        cPane.add(toolbar, BorderLayout.NORTH);

        // Split pane for viewport and message text area
        mLogSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mLogSplitter.setResizeWeight(1);
        mLogSplitter.setBorder(BorderFactory.createEmptyBorder());

        // Split pane for message text area and input text area
        mChatSplitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mChatSplitter.setResizeWeight(1);
        mChatSplitter.setBorder(BorderFactory.createEmptyBorder());

        mMessageText = new LogTextPanel();
        mChatSplitter.setTopComponent(mMessageText);

        mChatSplitter.setBottomComponent(new JScrollPane(mInputPane.getComponent()));

        mLogSplitter.setTopComponent(mViewport);
        mLogSplitter.setBottomComponent(mChatSplitter);

        cPane.add(mLogSplitter, BorderLayout.CENTER);

        setupAppMenus();
    }
}
