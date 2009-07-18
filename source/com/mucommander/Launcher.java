/*
 * This file is part of muCommander, http://www.mucommander.com
 * Copyright (C) 2002-2009 Maxence Bernard
 *
 * muCommander is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * muCommander is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mucommander;

import com.mucommander.bookmark.file.BookmarkProtocolProvider;
import com.mucommander.command.Command;
import com.mucommander.command.CommandException;
import com.mucommander.command.CommandManager;
import com.mucommander.commons.CommonsLogger;
import com.mucommander.conf.impl.MuConfiguration;
import com.mucommander.extension.ExtensionManager;
import com.mucommander.file.FileFactory;
import com.mucommander.file.FileLogger;
import com.mucommander.file.icon.impl.SwingFileIconProvider;
import com.mucommander.file.impl.ftp.FTPProtocolProvider;
import com.mucommander.file.impl.smb.SMBProtocolProvider;
import com.mucommander.file.util.ResourceLoader;
import com.mucommander.runtime.OsFamilies;
import com.mucommander.shell.ShellHistoryManager;
import com.mucommander.ui.dialog.startup.CheckVersionDialog;
import com.mucommander.ui.dialog.startup.InitialSetupDialog;
import com.mucommander.ui.main.SplashScreen;
import com.mucommander.ui.main.WindowManager;
import com.mucommander.ui.main.commandbar.CommandBarIO;
import com.mucommander.ui.main.toolbar.ToolBarIO;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * muCommander launcher.
 * <p>
 * This class is used to start muCommander. It will analyse command line
 * arguments, initialise the whole software and start the main window.
 * </p>
 * @author Maxence Bernard, Nicolas Rinaudo
 */
public class Launcher {

    // - Class fields -----------------------------------------------------------
    // --------------------------------------------------------------------------
    private static SplashScreen  splashScreen;
    /** Whether or not to display the splashscreen. */
    private static boolean       useSplash;
    /** Whether or not to display verbose error messages. */
    private static boolean       verbose;
    /** true while the application is launching, false after it has finished launching */
    public static boolean isLaunching = true;
    /** Launch lock. */
    public static final Object LAUNCH_LOCK = new Object();


    // - Initialisation ---------------------------------------------------------
    // --------------------------------------------------------------------------
    /**
     * Prevents initialisation of the <code>Launcher</code>.
     */
    private Launcher() {}


    /**
     * This method can be called to wait until the application has been launched. The caller thread will be blocked
     * until the application has been launched.
     * This method will return immediately if the application has already been launched when it is called.
     */
    public static void waitUntilLaunched() {
        if(Debug.ON) Debug.trace("called, thread="+Thread.currentThread());
        synchronized(LAUNCH_LOCK) {
            while(isLaunching) {
                try {
                    if(Debug.ON) Debug.trace("waiting");
                    LAUNCH_LOCK.wait();
                }
                catch(InterruptedException e) {
                    // will loop
                }
            }
        }
    }


    // - Commandline handling methods -------------------------------------------
    // --------------------------------------------------------------------------
    /**
     * Prints muCommander's command line usage and exits.
     */
    private static void printUsage() {
        System.out.println("Usage: mucommander [options] [folders]");
        System.out.println("Options:");

        // Allows users to tweak how file associations are loaded / saved.
        System.out.println(" -a FILE, --assoc FILE             Load associations from FILE.");

        // Allows users to tweak how bookmarks are loaded / saved.
        System.out.println(" -b FILE, --bookmarks FILE         Load bookmarks from FILE.");

        // Allows users to tweak how configuration is loaded / saved.
        System.out.println(" -c FILE, --configuration FILE     Load configuration from FILE");

        // Allows users to tweak how command bar configuration is loaded / saved.
        System.out.println(" -C FILE, --commandbar FILE        Load command bar from FILE.");

        // Allows users to change the extensions folder.
        System.out.println(" -e FOLDER, --extensions FOLDER    Load extensions from FOLDER.");

        // Allows users to tweak how custom commands are loaded / saved.
        System.out.println(" -f FILE, --commands FILE          Load custom commands from FILE.");

        // Ignore warnings.
        System.out.println(" -i, --ignore-warnings             Do not fail on warnings (default).");

        // Allows users to tweak how keymaps are loaded.
        System.out.println(" -k FILE, --keymap FILE            Load keymap from FILE");

        // If debug is turned on, -n and -d are used to control whether debug
        // text is printed out or not.
        if(Debug.ON) {
            System.out.println(" -n, --no-debug                    Disable debug output to stdout");
            System.out.println(" -d, --debug                       Enable debug output to stdout (default)");
        }

        // Allows users to change the preferences folder.
        System.out.println(" -p FOLDER, --preferences FOLDER   Store configuration files in FOLDER");

        // muCommander will not print verbose error messages.
        System.out.println(" -S, --silent                      Do not print verbose error messages");

        // Allows users to tweak how shell history is loaded / saved.
        System.out.println(" -s FILE, --shell-history FILE     Load shell history from FILE");

        // Allows users to tweak how toolbar configuration are loaded.
        System.out.println(" -t FILE, --toolbar FILE           Load toolbar from FILE");

        // Allows users to tweak how credentials are loaded.
        System.out.println(" -u FILE, --credentials FILE       Load credentials from FILE");

        // Text commands.
        System.out.println(" -h, --help                        Print the help text and exit");
        System.out.println(" -v, --version                     Print the version and exit");

        // muCommander will print verbose boot error messages.
        System.out.println(" -V, --verbose                     Print verbose error messages (default)");

        // Pedantic mode.
        System.out.println(" -w, --fail-on-warnings            Quits when a warning is encountered during");
        System.out.println("                                   the boot process.");
        System.exit(0);
    }

    /**
     * Prints muCommander's version to stdout and exits.
     */
    private static void printVersion() {
        System.out.println(RuntimeConstants.APP_STRING);
        System.out.print("Copyright (C) ");
        System.out.print(RuntimeConstants.COPYRIGHT);
        System.out.println(" Maxence Bernard");
        System.out.println("This is free software, distributed under the terms of the GNU General Public License.");
        System.exit(0);
    }

    /**
     * Prints the specified error message to stderr.
     * @param msg       error message to print to stder.
     * @param quit      whether or not to quit after printing the error message.
     * @param exception exception that triggered the error (for verbose output).
     */
    private static void printError(String msg, Exception exception, boolean quit) {
        printError(createErrorMessage(msg, exception, quit).toString(), quit);
    }

    /**
     * Creates an error message.
     */
    private static StringBuffer createErrorMessage(String msg, Exception exception, boolean quit) {
        StringBuffer error;

        error = new StringBuffer();
        if(quit)
            error.append("Warning: ");
        error.append(msg);
        if(verbose && (exception != null)) {
            error.append(": ");
            error.append(exception.getMessage());
        }

        return error;
    }

    /**
     * Prints an error message.
     */
    private static void printError(String msg, boolean quit) {
        System.err.println(msg);
        if(quit) {
            System.err.println("See mucommander --help for more information.");
            System.exit(1);
        }
    }

    /**
     * Prints a configuration file specific error message.
     */
    private static void printFileError(String msg, Exception exception, boolean quit) {
        StringBuffer error;

        error = createErrorMessage(msg, exception, quit);
        if(!quit)
            error.append(". Using default values.");

        printError(error.toString(), quit);
    }

    /**
     * Prints the specified startup message.
     */
    private static void printStartupMessage(String message) {
        if(useSplash)
            splashScreen.setLoadingMessage(message);

        if(Debug.ON) Debug.trace(message);
    }


    // - Boot code --------------------------------------------------------------
    // --------------------------------------------------------------------------
    /**
     * Method used to migrate commands that used to be defined in the configuration but were moved to <code>commands.xml</code>.
     * @param useName     name of the <code>use custom command</code> configuration variable.
     * @param commandName name of the <code>custom command</code> configuration variable.
     */
    private static void migrateCommand(String useName, String commandName, String alias) {
        String command;

        if(MuConfiguration.getBooleanVariable(useName) && (command = MuConfiguration.getVariable(commandName)) != null) {
            try {
                CommandManager.registerCommand(new Command(alias, command, Command.SYSTEM_COMMAND));}
            catch(CommandException e) {
                // Ignore this: the command didn't work in the first place, we might as well get rid of it.
            }
            MuConfiguration.removeVariable(useName);
            MuConfiguration.removeVariable(commandName);
        }
    }


    /**
     * Main method used to startup muCommander.
     * @param args command line arguments.
     * @throws IOException if an unrecoverable error occurred during startup 
     */
    public static void main(String args[]) throws IOException {
        int i; // Index in the command line arguments.

        // Initialises fields.
        // Whether or not to ignore warnings when booting.
        boolean fatalWarnings = false;
        verbose               = true;

        // - Command line parsing -------------------------------------
        // ------------------------------------------------------------
        for(i = 0; i < args.length; i++) {
            // Print version.
            if(args[i].equals("-v") || args[i].equals("--version"))
                printVersion();

            // Print help.
            else if(args[i].equals("-h") || args[i].equals("--help"))
                printUsage();

            // Associations handling.
            else if(args[i].equals("-a") || args[i].equals("--assoc")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {com.mucommander.command.CommandManager.setAssociationFile(args[++i]);}
                catch(Exception e) {printError("Could not set association files", e, fatalWarnings);}
            }

            // Custom commands handling.
            else if(args[i].equals("-f") || args[i].equals("--commands")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {com.mucommander.command.CommandManager.setCommandFile(args[++i]);}
                catch(Exception e) {printError("Could not set commands file", e, fatalWarnings);}
            }

            // Bookmarks handling.
            else if(args[i].equals("-b") || args[i].equals("--bookmarks")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {com.mucommander.bookmark.BookmarkManager.setBookmarksFile(args[++i]);}
                catch(Exception e) {printError("Could not set bookmarks file", e, fatalWarnings);}
            }

            // Configuration handling.
            else if(args[i].equals("-c") || args[i].equals("--configuration")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {MuConfiguration.setConfigurationFile(args[++i]);}
                catch(Exception e) {printError("Could not set configuration file", e, fatalWarnings);}
            }

            // Shell history.
            else if(args[i].equals("-s") || args[i].equals("--shell-history")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {ShellHistoryManager.setHistoryFile(args[++i]);}
                catch(Exception e) {printError("Could not set shell history file", e, fatalWarnings);}
            }

            // Keymap file.
            else if(args[i].equals("-k") || args[i].equals("--keymap")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {com.mucommander.ui.action.ActionKeymapIO.setActionsFile(args[++i]);}
                catch(Exception e) {printError("Could not set keymap file", e, fatalWarnings);}
            }

            // Toolbar file.
            else if(args[i].equals("-t") || args[i].equals("--toolbar")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {
                    ToolBarIO.setDescriptionFile(args[++i]);}
                catch(Exception e) {printError("Could not set keymap file", e, fatalWarnings);}
            }

            // Commandbar file.
            else if(args[i].equals("-C") || args[i].equals("--commandbar")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {
                    CommandBarIO.setDescriptionFile(args[++i]);}
                catch(Exception e) {printError("Could not set commandbar description file", e, fatalWarnings);}
            }

            // Credentials file.
            else if(args[i].equals("-U") || args[i].equals("--credentials")) {
                if(i >= args.length - 1)
                    printError("Missing FILE parameter to " + args[i], null, true);
                try {com.mucommander.auth.CredentialsManager.setCredentialsFile(args[++i]);}
                catch(Exception e) {printError("Could not set credentials file", e, fatalWarnings);}
            }

            // Debug options.
            else if(Debug.ON && (args[i].equals("-n") || args[i].equals("--no-debug")))
                Debug.setEnabled(false);
            else if(Debug.ON && (args[i].equals("-d") || args[i].equals("--debug")))
                Debug.setEnabled(true);

            // Preference folder.
            else if((args[i].equals("-p") || args[i].equals("--preferences"))) {
                if(i >= args.length - 1)
                    printError("Missing FOLDER parameter to " + args[i], null, true);
                try {PlatformManager.setPreferencesFolder(args[++i]);}
                catch(Exception e) {printError("Could not set preferences folder", e, fatalWarnings);}
            }

            // Extensions folder.
            else if((args[i].equals("-e") || args[i].equals("--extensions"))) {
                if(i >= args.length - 1)
                    printError("Missing FOLDER parameter to " + args[i], null, true);
                try {ExtensionManager.setExtensionsFolder(args[++i]);}
                catch(Exception e) {printError("Could not set extensions folder", e, fatalWarnings);}
            }

            // Ignore warnings.
            else if(args[i].equals("-i") || args[i].equals("--ignore-warnings"))
                fatalWarnings = false;

            // Fail on warnings.
            else if(args[i].equals("-w") || args[i].equals("--fail-on-warnings"))
                fatalWarnings = true;

            // Silent mode.
            else if(args[i].equals("-S") || args[i].equals("--silent"))
                verbose = false;

            // Verbose mode.
            else if(args[i].equals("-V") || args[i].equals("--verbose"))
                verbose = true;

            // Illegal argument.
            else
                break;
        }

        // - Configuration init ---------------------------------------
        // ------------------------------------------------------------


        // Attempts to guess whether this is the first time muCommander is booted or not.
        boolean isFirstBoot;
        try {isFirstBoot = !MuConfiguration.getConfigurationFile().exists();}
        catch(IOException e) {isFirstBoot = true;}

        // Configuration needs to be loaded before any sort of GUI creation is performed : under Mac OS X, if we're
        // to use the metal look, we need to know about it right about now.
        try {MuConfiguration.read();}
        catch(Exception e) {printFileError("Could not load configuration", e, fatalWarnings);}


        // - Logging configuration ------------------------------------
        // ------------------------------------------------------------
        configureLogging();


        // - MAC OS X specific init -----------------------------------
        // ------------------------------------------------------------
        // If muCommander is running under Mac OS X (how lucky!), add some glue for the main menu bar and other OS X
        // specifics.
        if(OsFamilies.MAC_OS_X.isCurrent()) {
            // Use reflection to create an OSXIntegration instance so that ClassLoader
            // doesn't throw an NoClassDefFoundException under platforms other than Mac OS X
            try {
                Class osxIntegrationClass = Class.forName("com.mucommander.ui.macosx.OSXIntegration");
                Constructor constructor   = osxIntegrationClass.getConstructor(new Class[]{});
                constructor.newInstance(new Object[]{});
            }
            catch(Exception e) {if(Debug.ON) Debug.trace("Exception thrown while initializing Mac OS X integration");}
        }


        // - muCommander boot -----------------------------------------
        // ------------------------------------------------------------
        // Adds all extensions to the classpath.
        try {ExtensionManager.addExtensionsToClasspath();}
        catch(Exception e) {if(Debug.ON) Debug.trace("Failed to add extensions to the classpath");}
       
        // This the property is supposed to have the java.net package use the proxy defined in the system settings
        // to establish HTTP connections. This property is supported only under Java 1.5 and up.
        // Note that Mac OS X already uses the system HTTP proxy, with or without this property being set.
        System.setProperty("java.net.useSystemProxies", "true");

        // Shows the splash screen, if enabled in the preferences
        useSplash = MuConfiguration.getVariable(MuConfiguration.SHOW_SPLASH_SCREEN, MuConfiguration.DEFAULT_SHOW_SPLASH_SCREEN);
        if(useSplash) {
            splashScreen = new SplashScreen(RuntimeConstants.VERSION, "Loading preferences...");}

        boolean showSetup;
        showSetup = MuConfiguration.getVariable(MuConfiguration.THEME_TYPE) == null;

        // Traps VM shutdown
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());

        // Configure filesystems
        configureFilesystems();

        // Initialises the desktop.
        try {com.mucommander.desktop.DesktopManager.init(isFirstBoot);}
        catch(Exception e) {printError("Could not initialise desktop", e, true);}

        // Loads dictionary
        printStartupMessage("Loading dictionary...");
        try {com.mucommander.text.Translator.loadDictionaryFile();}
        catch(Exception e) {printError("Could not load dictionary", e, true);}

        // Loads custom commands
        printStartupMessage("Loading file associations...");
        try {com.mucommander.command.CommandManager.loadCommands();}
        catch(Exception e) {
            if(Debug.ON)
                printFileError("Could not load custom commands", e, fatalWarnings);
        }

        // Migrates the custom editor and custom viewer if necessary.
        migrateCommand("viewer.use_custom", "viewer.custom_command", CommandManager.VIEWER_ALIAS);
        migrateCommand("editor.use_custom", "editor.custom_command", CommandManager.EDITOR_ALIAS);
        try {CommandManager.writeCommands();}
        catch(Exception e) {
            System.out.println("###############################");
            e.printStackTrace();
            // There's really nothing we can do about this...
        }

        try {com.mucommander.command.CommandManager.loadAssociations();}
        catch(Exception e) {
            if(Debug.ON)
                printFileError("Could not load custom associations", e, fatalWarnings);
        }

        // Loads bookmarks
        printStartupMessage("Loading bookmarks...");
        try {com.mucommander.bookmark.BookmarkManager.loadBookmarks();}
        catch(Exception e) {printFileError("Could not load bookmarks", e, fatalWarnings);}

        // Loads credentials
        printStartupMessage("Loading credentials...");
        try {com.mucommander.auth.CredentialsManager.loadCredentials();}
        catch(Exception e) {printFileError("Could not load credentials", e, fatalWarnings);}
        
        // Loads shell history
        printStartupMessage("Loading shell history...");
        try {ShellHistoryManager.loadHistory();}
        catch(Exception e) {printFileError("Could not load shell history", e, fatalWarnings);}

        // Inits CustomDateFormat to make sure that its ConfigurationListener is added
        // before FileTable, so CustomDateFormat gets notified of date format changes first
        com.mucommander.text.CustomDateFormat.init();

        // Initialize file icons
        printStartupMessage("Loading icons...");
        // Initialize the SwingFileIconProvider from the main thread, see method Javadoc for an explanation on why we do this now
        SwingFileIconProvider.forceInit();
        // The math.max(1.0f, ...) part is to workaround a bug which cause(d) this value to be set to 0.0 in the configuration file.
        com.mucommander.ui.icon.FileIcons.setScaleFactor(Math.max(1.0f, MuConfiguration.getVariable(MuConfiguration.TABLE_ICON_SCALE,
                                                                                          MuConfiguration.DEFAULT_TABLE_ICON_SCALE)));
        com.mucommander.ui.icon.FileIcons.setSystemIconsPolicy(MuConfiguration.getVariable(MuConfiguration.USE_SYSTEM_FILE_ICONS, MuConfiguration.DEFAULT_USE_SYSTEM_FILE_ICONS));

        // Loads the ActionKeymap file
        printStartupMessage("Loading actions...");
        try {com.mucommander.ui.action.ActionKeymapIO.loadActionKeymap();}
        catch(Exception e) {printFileError("Could not load actions", e, fatalWarnings);}

        // Loads the ToolBar's description file
        printStartupMessage("Loading toolbar description...");
        try {ToolBarIO.loadDescriptionFile();}
        catch(Exception e) {printFileError("Could not load toolbar description", e, fatalWarnings);}

        // Loads the CommandBar's description file
        printStartupMessage("Loading command bar description...");
        try {CommandBarIO.loadCommandBar();}
        catch(Exception e) {printFileError("Could not load commandbar description", e, fatalWarnings);}

        // Loads the themes.
        printStartupMessage("Loading theme...");
        com.mucommander.ui.theme.ThemeManager.loadCurrentTheme();

        // Starts Bonjour services discovery (only if enabled in prefs)
        printStartupMessage("Starting Bonjour services discovery...");
        com.mucommander.bonjour.BonjourDirectory.setActive(MuConfiguration.getVariable(MuConfiguration.ENABLE_BONJOUR_DISCOVERY, MuConfiguration.DEFAULT_ENABLE_BONJOUR_DISCOVERY));

        // Creates the initial main frame using any initial path specified by the command line.
        printStartupMessage("Initializing window...");
        for(; i < args.length; i += 2) {
            if(i < args.length - 1)
                WindowManager.createNewMainFrame(args[i], args[i + 1]);
            else
                WindowManager.createNewMainFrame(args[i], null);
        }

        // If no initial path was specified, start a default main window.
        if(WindowManager.getCurrentMainFrame() == null)
    	    WindowManager.createNewMainFrame();

        // Done launching, wake up threads waiting for the application being launched.
        // Important: this must be done before disposing the splash screen, as this would otherwise create a deadlock
        // if the AWT event thread were waiting in #waitUntilLaunched .
        synchronized(LAUNCH_LOCK) {
            isLaunching = false;
            LAUNCH_LOCK.notifyAll();
        }

        // Enable system nofifications, only after MainFrame is created as SystemTrayNotifier needs to retrieve
        // a MainFrame instance
        if(MuConfiguration.getVariable(MuConfiguration.ENABLE_SYSTEM_NOTIFICATIONS, MuConfiguration.DEFAULT_ENABLE_SYSTEM_NOTIFICATIONS)) {
            printStartupMessage("Enabling system notifications...");
            if(com.mucommander.ui.notifier.AbstractNotifier.isAvailable())
                com.mucommander.ui.notifier.AbstractNotifier.getNotifier().setEnabled(true);
        }

        // Dispose splash screen.
        if(useSplash)
            splashScreen.dispose();
        
        // Check for newer version unless it was disabled
        if(MuConfiguration.getVariable(MuConfiguration.CHECK_FOR_UPDATE, MuConfiguration.DEFAULT_CHECK_FOR_UPDATE))
            new CheckVersionDialog(WindowManager.getCurrentMainFrame(), false);

        // If no theme is configured in the preferences, ask for an initial theme.
        if(showSetup)
            new InitialSetupDialog(WindowManager.getCurrentMainFrame()).showDialog();
    }

    private static void configureFilesystems() {
        // Configure the SMB subsystem (backed by jCIFS) to maintain compatibility with SMB servers that don't support
        // NTLM v2 authentication such as Samba 3.0.x, which still is widely used and comes pre-installed on
        // Mac OS X Leopard.
        // Since jCIFS 1.3.0, the default is to use NTLM v2 authentication and extended security.
        SMBProtocolProvider.setLmCompatibility(MuConfiguration.getVariable(MuConfiguration.SMB_LM_COMPATIBILITY, MuConfiguration.DEFAULT_SMB_LM_COMPATIBILITY));
        SMBProtocolProvider.setExtendedSecurity(MuConfiguration.getVariable(MuConfiguration.SMB_USE_EXTENDED_SECURITY, MuConfiguration.DEFAULT_SMB_USE_EXTENDED_SECURITY));

        // Use the FTP configuration option that controls whether to force the display of hidden files, or leave it for
        // the servers to decide whether to show them.
        FTPProtocolProvider.setForceHiddenFilesListing(MuConfiguration.getVariable(MuConfiguration.LIST_HIDDEN_FILES, MuConfiguration.DEFAULT_LIST_HIDDEN_FILES));        

        // Register the application-specific 'bookmark' protocol. 
        FileFactory.registerProtocol(BookmarkProtocolProvider.BOOKMARK, new com.mucommander.bookmark.file.BookmarkProtocolProvider());
    }

    private static void configureLogging() throws IOException {
        // Set the path to the java.util.logging configuration file, before loggers get a chance to be instantiated.
        LogManager.getLogManager().readConfiguration(ResourceLoader.getResourceAsStream("com/mucommander/logging.properties"));

        // Read the log level defined in the configuration
        setLogLevel(Level.parse(MuConfiguration.getVariable(MuConfiguration.LOG_LEVEL, MuConfiguration.DEFAULT_LOG_LEVEL)));

//        Logger fileLogger = FileLogger.getLogger();
//        fileLogger.finest("fileLogger finest");
//        fileLogger.finer("fileLogger finer");
//        fileLogger.fine("fileLogger fine");
//        fileLogger.config("fileLogger config");
//        fileLogger.info("fileLogger info");
//        fileLogger.warning("fileLogger warning");
//        fileLogger.severe("fileLogger severe");
//
//        rootLogger.finest("rootLogger finest");
//        rootLogger.finer("rootLogger finer");
//        rootLogger.fine("rootLogger fine");
//        rootLogger.config("rootLogger config");
//        rootLogger.info("rootLogger info");
//        rootLogger.warning("rootLogger warning");
//        rootLogger.severe("rootLogger severe");
    }

    /**
     * Sets the level of all muCommander loggers, namely {@link AppLogger}, {@link FileLogger} and
     * {@link CommonsLogger}.
     *
     * @param level the new log level
     */
    public static void setLogLevel(Level level) {
        // Set the level of muCommander loggers.
        // Note: non-muCommander loggers default to the level defined in the bundled logging.properties.
        CommonsLogger.getLogger().setLevel(level);
        FileLogger.getLogger().setLevel(level);
        AppLogger.getLogger().setLevel(level);

        // Set the level of log handlers to ALL, to let the logging level be defined solely by loggers.
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler handlers[] = rootLogger.getHandlers();
        for(int i=0; i<handlers.length; i++)
            handlers[i].setLevel(Level.ALL);

    }
}
