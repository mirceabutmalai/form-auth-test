/**
 * RADCOM.
 *
 */
package ro.radcom.frm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationBuilder;

/**
 * logger application initializer.
 */
public final class LoggerInitializer
        implements ServletContextListener {

    public static final String ATTRIBUTE_NAME = "x-log4j-initializer";
    private static final String DEFAULT_LOG_FILE_NAME = "call-ctrl-local.log";
    // aceste date sunt incarcate din resurse o singura data la startup; cum fisierul din care se
    // incarca este in interiorul war-ului se presupune ca nimeni nu-l modifica. Totusi pot fi null
    // si putem avea probleme de format (desi asa ceva nu trebuie sa ajunga in productie) daca
    // cineva modifica intr-un mod gresit acest fisier din interiorul war-ului
    private transient String defaultPropsFilePath;
    private transient Properties defaultProperties;
    private transient long lastSuccessfullLoad;
    private transient InternalCustomConfigData customConfigData;
    private transient AbstractConfiguration currentLog4jConfiguration;
    private transient LoggerContext globalLoggerContext;
    private transient Logger log;

    /**
     * constructor.
     */
    public LoggerInitializer() {
        super();
    }

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        final ServletContext ctx = sce.getServletContext();
        final ClassLoader contextCl = Thread.currentThread().getContextClassLoader();

        // reinitializare toate variabilele
        defaultPropsFilePath = null;
        defaultProperties = null;
        customConfigData = null;
        currentLog4jConfiguration = null;
        globalLoggerContext = null;
        log = null;

        final long now = System.currentTimeMillis();

        loadDefaultProperties(ctx, "log4j.config.file", contextCl);

        final InternalCustomConfigData aCustomData = loadRealProperties("war.log4j.file.path");

        final AbstractConfiguration aLog4jConfig = createLog4jConfig(aCustomData.realProperties);

        final LoggerContext aLoggerContext = (LoggerContext) LogManager.getContext(false);

        aLoggerContext.setConfiguration(aLog4jConfig);

        customConfigData = aCustomData;
        lastSuccessfullLoad = now;
        currentLog4jConfiguration = aLog4jConfig;
        globalLoggerContext = aLoggerContext;

        log = LogManager.getLogger(LoggerInitializer.class);
        log.info("LOG4J initializer has processed context initialized event");

        ctx.setAttribute(ATTRIBUTE_NAME, this);
    }

    @Override
    public void contextDestroyed(final ServletContextEvent sce) {
        final ServletContext ctx = sce.getServletContext();

        ctx.removeAttribute(ATTRIBUTE_NAME);

        if (log != null) {
            log.info("LOG4J initializer is processing context destroyed event");
            log = null;
        }

        if (globalLoggerContext != null) {
            try {
                globalLoggerContext.stop();
            } catch (Exception ex) {
                // cant do nothing
            }

            globalLoggerContext = null;
            currentLog4jConfiguration = null;
        }

        lastSuccessfullLoad = 0;
        customConfigData = null;
        defaultPropsFilePath = null;
        defaultProperties = null;
    }

    public void periodicReloadCheck() {
        if (globalLoggerContext == null) {
            if (log.isDebugEnabled()) {
                log.debug("aborting periodic reload check because globalLoggerContext is null");
            }
            return;
        }
        if (customConfigData == null) {
            if (log.isDebugEnabled()) {
                log.debug("aborting periodic reload check because customConfigData is null");
            }
            return;
        }
        if (customConfigData.customFile == null) {
            if (log.isTraceEnabled()) {
                log.trace("skipping periodic reload check because customFile is null");
            }
            return;
        }

        final long now = System.currentTimeMillis();

        final File aFile = customConfigData.customFile;
        if (!aFile.exists()) {
            if (customConfigData.fileLastModifiedOfLoadedVersion > 0) {
                // fisierul a existat si acum nu mai exista
                final Properties aNewRealProperties = defaultProperties;
                final AbstractConfiguration aLog4jConfig;

                try {
                    aLog4jConfig = createLog4jConfig(aNewRealProperties);
                    globalLoggerContext.setConfiguration(aLog4jConfig);
                } catch (Exception ex) {
                    return;
                }

                log.info("reloaded log4j default configuration because custom file dissapeared");

                customConfigData.fileLastModifiedOfLoadedVersion = 0;
                customConfigData.fileLastModifiedOfLastLoadTry = 0;
                customConfigData.realProperties = aNewRealProperties;

                lastSuccessfullLoad = now;
                currentLog4jConfiguration = aLog4jConfig;
                return;
            }

            // nu am nimic aici cand fisierul nu exista si nu a existat
            return;
        }

        // fisierul exista
        if ((!aFile.isFile()) || (!aFile.canRead())) {
            return;
        }

        final long aFileLastModified = aFile.lastModified();
        if (aFileLastModified > customConfigData.fileLastModifiedOfLastLoadTry) {
            log.info("start reloading log4j custom configuration");

            final Properties aNewRealProperties;
            try {
                aNewRealProperties = createProperties(aFile, defaultProperties);
            } catch (Exception ex) {
                log.warn("resulted properties are invalid as properties file ==> cannot reload", ex);

                customConfigData.fileLastModifiedOfLastLoadTry = aFileLastModified;
                return;
            }

            final AbstractConfiguration aLog4jConfig;
            try {
                aLog4jConfig = createLog4jConfig(aNewRealProperties);
                globalLoggerContext.setConfiguration(aLog4jConfig);
            } catch (Exception ex) {
                log.warn("log4j configuration is invalid ==> cannot reload", ex);

                customConfigData.fileLastModifiedOfLastLoadTry = aFileLastModified;
                return;
            }

            log.info("successfully reloaded log4j custom configuration");

            customConfigData.fileLastModifiedOfLoadedVersion = aFileLastModified;
            customConfigData.fileLastModifiedOfLastLoadTry = aFileLastModified;
            customConfigData.realProperties = aNewRealProperties;

            lastSuccessfullLoad = now;
            currentLog4jConfiguration = aLog4jConfig;
        }
    }

    private void loadDefaultProperties(
            final ServletContext ctx, final String initParamName, final ClassLoader contextCl) {

        final String aFilePath = ctx.getInitParameter(initParamName);
        if ((aFilePath == null) || (aFilePath.isEmpty())) {
            // in acest caz nu s-a definit nimic in web.xml deci nu functionam cu default
            defaultPropsFilePath = null;
            defaultProperties = null;
            return;
        }

        final URL urlResource = contextCl.getResource(aFilePath);
        if (urlResource == null) {
            // in acest caz nu se gaseste in war resursa specificata in web.xml ==> acest caz
            // se considera o eroare
            throw new NullPointerException("resource identified by context init parameter "
                    + initParamName + " with value " + aFilePath + " was not found by class loader");
        }

        final Properties aProperties;
        try {
            aProperties = createProperties(urlResource);
        } catch (Exception ex) {
            throw new IllegalArgumentException("resource identified by context init parameter "
                    + initParamName + " with value " + aFilePath
                    + " does not contain valid properties format data", ex);
        }

        // aici am incarcat cu succes proprietatile default pentru LOG4J 2.x
        // aici inca nu stim daca avem un format valid dpdv LOG4J
        defaultPropsFilePath = aFilePath;
        defaultProperties = aProperties;
    }

    private InternalCustomConfigData loadRealProperties(final String sysPropName) {
        final String aFilePath = System.getProperty(sysPropName);
        if ((aFilePath == null) || (aFilePath.isEmpty())) {
            // in acest caz nu s-a definit nimic in system properties pentru acest fisier
            final InternalCustomConfigData data = new InternalCustomConfigData();
            data.realProperties = defaultProperties;
            return data;
        }

        final File aFile = new File(aFilePath);
        if (!aFile.exists()) {
            // nu avem fisier pe disc dar proprietatea este definita ==> mergem mai departe
            // fara custom properties
            final InternalCustomConfigData data = new InternalCustomConfigData();
            data.customFilePath = aFilePath;
            data.customFile = aFile;
            data.realProperties = defaultProperties;
            return data;
        }
        if ((!aFile.isFile()) || (!aFile.canRead())) {
            throw new IllegalArgumentException("call control log4j configuration file " + aFilePath
                    + " given by system properties is not a file or is not readable");
        }

        final long aLastModified = aFile.lastModified();

        final Properties aProperties;
        try {
            aProperties = createProperties(aFile, defaultProperties);
        } catch (Exception ex) {
            throw new IllegalArgumentException("call control log4j configuration file " + aFilePath
                    + " does not contain valid properties format data", ex);
        }

        // configuration file is valid as properties file format (not yet log4j format)
        final InternalCustomConfigData data = new InternalCustomConfigData();
        data.customFilePath = aFilePath;
        data.customFile = aFile;
        data.fileLastModifiedOfLastLoadTry = aLastModified;
        data.fileLastModifiedOfLoadedVersion = aLastModified;
        data.realProperties = aProperties;
        return data;
    }

    private AbstractConfiguration createLog4jConfig(final Properties props) {
        if (props == null) {
            return makeDefaultLog4jConfig();
        }

        final PropertiesConfigurationBuilder aBuilder = new PropertiesConfigurationBuilder();
        aBuilder.setRootProperties(props);
        return aBuilder.build();
    }

    private AbstractConfiguration makeDefaultLog4jConfig() {
        final ConfigurationBuilder<BuiltConfiguration> aBuilder
                = ConfigurationBuilderFactory.newConfigurationBuilder();

        final AppenderComponentBuilder aAppenderBuilder
                = aBuilder.newAppender("LOCAL_LOG", "RollingFile");
        aAppenderBuilder.addAttribute("append", true);
        aAppenderBuilder.addAttribute("bufferedIO", true);
        aAppenderBuilder.addAttribute("immediateFlush", true);
        aAppenderBuilder.addAttribute(
                "fileName", "${sys:catalina.base}/logs/" + DEFAULT_LOG_FILE_NAME);
        aAppenderBuilder.addAttribute(
                "filePattern", "${sys:catalina.base}/logs/" + DEFAULT_LOG_FILE_NAME + ".%d{yyyy-MM-dd}");
        aAppenderBuilder.addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);

        final LayoutComponentBuilder aLayoutBuilder = aBuilder.newLayout("PatternLayout");
        aLayoutBuilder.addAttribute("pattern", "%date %-5level [%logger{*}] (%processId:%threadName) %message%n%throwable");

        aAppenderBuilder.add(aLayoutBuilder);

        final ComponentBuilder aPolicyComp = aBuilder.newComponent("TimeBasedTriggeringPolicy");
        aPolicyComp.addAttribute("interval", 1);
        aPolicyComp.addAttribute("modulate", true);
        aPolicyComp.addAttribute("maxRandomDelay", 0);

        aAppenderBuilder.addComponent(aPolicyComp);

        final ComponentBuilder aStrategyComp = aBuilder.newComponent("DefaultRolloverStrategy");
        aStrategyComp.addAttribute("fileIndex", "nomax");

        aAppenderBuilder.addComponent(aStrategyComp);

        aBuilder.add(aAppenderBuilder);

        final RootLoggerComponentBuilder aRootBuilder = aBuilder.newRootLogger(Level.INFO);
        aRootBuilder.add(aBuilder.newAppenderRef("LOCAL_LOG"));

        aBuilder.add(aRootBuilder);

        return aBuilder.build();
    }

    private Properties createProperties(final File file, final Properties defaults) {
        final InputStream is;
        try {
            is = new FileInputStream(file);
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException("file not found", ex);
        }

        final Properties props;
        if (defaults == null) {
            props = new Properties();
        } else {
            props = new Properties(defaults);
        }

        try {
            props.load(is);
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid properties file for " + file, ex);
        } finally {
            try {
                is.close();
            } catch (Exception ex) {
                // nu putem loga nimic pentru ca nu s-a initializat LOG4J
            }
        }

        return props;
    }

    private Properties createProperties(final URL url) {
        final InputStream is;
        try {
            is = url.openStream();
        } catch (IOException ex) {
            throw new IllegalArgumentException("open stream failure for url " + url, ex);
        }

        final Properties props = new Properties();
        try {
            props.load(is);
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "properties file cannot be loaded for url " + url, ex);
        } finally {
            try {
                is.close();
            } catch (Exception ex) {
                // nu putem loga nimic pentru ca nu s-a initializat LOG4J
            }
        }

        return props;
    }

    private static final class InternalCustomConfigData {

        private transient String customFilePath;
        private transient File customFile;
        private transient long fileLastModifiedOfLoadedVersion;
        private transient long fileLastModifiedOfLastLoadTry;
        private transient Properties realProperties;
    }
}
