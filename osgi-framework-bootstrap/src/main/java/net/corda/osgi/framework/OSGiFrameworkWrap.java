package net.corda.osgi.framework;

import net.corda.osgi.api.Application;
import net.corda.osgi.api.Shutdown;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableMap;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * {@link OSGiFrameworkWrap} provides an API to bootstrap an OSGI framework and OSGi bundles in the classpath.
 * <p/>
 * This classpath can be either an executable jar or a runtime classpath generated by the IDE.
 * <p/>
 * The OSGi bundles are embedded in the directory {@code bundles}, which is a child of the root classpath.
 * <p/>
 * The file {@code system_bundles} in the root of the classpath lists the paths to access the bundles to activate.
 * <p/>
 * The file {@code system_packages_extra} in the root of the classpath lists packages exposed from this classpath to the
 * bundles active in the OSGi framework.
 * <p/>
 * The classpath or executable jar has the following structure:
 * <pre>{@code
 *
 *      <root_of_classpath>
 *      +--- bundles
 *      |    +--- <bundle_1.jar>
 *      |    +--- <...>
 *      |    +--- <bundle_n.jar>
 *      +--- system_bundles
 *      \___ system_packages_extra
 * }</pre>
 */
class OSGiFrameworkWrap implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(OSGiFrameworkWrap.class);

    /**
     * Map the bundle state number to a description of the state.
     * Used to log.
     */
    private static final Map<Integer, String> bundleStateMap;
    static {
        Map<Integer, String> map = new LinkedHashMap<>();
        // 0x00000020 = 0010.0000 binary
        map.put(Bundle.ACTIVE, "active");
        // 0x00000002 = 0000.0010 binary
        map.put(Bundle.INSTALLED, "installed");
        // 0x00000004 = 0000.0100 binary
        map.put(Bundle.RESOLVED, "resolved");
        // 0x00000008 = 0000.1000 binary
        map.put(Bundle.STARTING, "starting");
        // 0x00000010 = 0001.0000 binary
        map.put(Bundle.STOPPING, "stopping");
        // 0x00000001 = 0000.0001 binary
        map.put(Bundle.UNINSTALLED, "uninstalled");
        bundleStateMap = unmodifiableMap(map);
    }

    /**
     * Extension used to identify {@code jar} files to {@link #install}.
     * @see #install
     */
    private static final String JAR_EXTENSION = ".jar";

    private static final String FRAMEWORK_PROPERTIES_RESOURCE = "framework.properties";

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return unmodifiableMap(result);
    }

    /**
     * Return a new configured {@link Framework} loaded from the classpath and having {@code frameworkFactoryFQN} as
     * Full Qualified Name of the {@link FrameworkFactory}.
     * Configure the {@link Framework} to set the bundles' cache to {@code frameworkStorageDir} path.
     *
     * The {@link FrameworkFactory} must be in the classpath.
     *
     * @param frameworkStorageDir Path to the directory the {@link Framework} uses as bundles' cache.
     * @param systemPackagesExtra Packages specified in this property are added to
     * the {@code org.osgi.framework.system.packages} property.
     * This allows the configurator to only define the additional packages and leave the standard execution
     * environment packages to be defined by the framework.
     * @see <a href="http://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html#framework.lifecycle.launchingproperties">OSGi Core Release 7 - 4.2.2 Launching Properties</a>
     * See {@link #getFrameworkPropertyFrom(String)} to load properties from resources.
     *
     * @return A new configured {@link Framework} loaded from the classpath and having {@code frameworkFactoryFQN} as
     *         Full Qualified Name of the {@link FrameworkFactory}.
     *
     * @throws ClassNotFoundException If the {@link FrameworkFactory} specified in {@code frameworkFactoryFQN}
     *                                isn't in the classpath.
     * @throws SecurityException If a {@link SecurityManager} is installed and the caller hasn't {@link RuntimePermission}.
     */
    static Framework getFrameworkFrom(
        Path frameworkStorageDir,
        String systemPackagesExtra
    ) throws ClassNotFoundException, IOException, SecurityException {
        Optional<FrameworkFactory> optFactory = ServiceLoader.load(FrameworkFactory.class, OSGiFrameworkWrap.class.getClassLoader()).findFirst();
        if (optFactory.isEmpty()) {
            throw new ClassNotFoundException("No OSGi FrameworkFactory found.");
        }
        final FrameworkFactory frameworkFactory = optFactory.get();
        final Map<String, String> configurationMap = new LinkedHashMap<>();
        configurationMap.put(Constants.FRAMEWORK_STORAGE, frameworkStorageDir.toString());
        configurationMap.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        configurationMap.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, systemPackagesExtra);
        configurationMap.putAll(toStringMap(loadOSGiProperties(FRAMEWORK_PROPERTIES_RESOURCE)));
        configurationMap.putAll(toStringMap(System.getProperties()));
        if (logger.isDebugEnabled()) {
            configurationMap.forEach((key, value) -> logger.debug("OSGi property {} = {}.", key, value));
        }
        return frameworkFactory.newFramework(configurationMap);
    }

    /**
     * @param resource in the classpath containing a properties file.
     * @return a {@link Properties} object.
     * @throws IOException Failed to read OSGi properties.
     */
    private static Properties loadOSGiProperties(String resource) throws IOException {
        final Properties properties = new Properties();
        final URL resourceUrl = OSGiFrameworkMain.class.getClassLoader().getResource(resource);
        if (resourceUrl != null) {
            try (InputStream input = new BufferedInputStream(resourceUrl.openStream())) {
                properties.load(input);
            }
        }
        return properties;
    }

    /**
     * Return the {@code resource} as a comma separated list to be used as a property to configure the OSGi framework.
     * Ignore anything in a line after `#`.
     *
     * @param resource in the classpath from where to read the list.
     * @return the list loaded from {@code resource} as a comma separated text value.
     * @throws IOException If the {@code resource} can't be accessed.
     */
    static String getFrameworkPropertyFrom(String resource) throws IOException {
        final URL resourceUrl = OSGiFrameworkMain.class.getClassLoader().getResource(resource);
        if (resourceUrl == null) {
            throw new IOException("OSGi property resource " + resource + " not found in this classpath/jar.");
        }
        final List<String> propertyValueList;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream()))) {
            propertyValueList = reader.lines().map(OSGiFrameworkWrap::removeTrailingComment)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(toList());
        }
        return String.join(",", propertyValueList);
    }

    static String removeTrailingComment(String line) {
        final int commentIdx = line.indexOf('#');
        return (commentIdx < 0) ? line : line.substring(0, commentIdx);
    }

    /**
     * Return {@code true} if the {@code state} LSB is {@link Bundle#ACTIVE}
     *
     * Bundle states are expressed as a bit-mask though a bundle can only be in one state at any time,
     * the state in the lifecycle is represented in the LSB of the value returned by {@link Bundle#getState}.
     * See OSGi Core Release 7 <a href="https://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html">4.4.2 Bundle State</a>
     *
     * @param state of the bundle.
     *
     * @return {@code true} if the {@code state} LSB is {@link Bundle#ACTIVE}.
     */
    static Boolean isActive(int state) {
        // The bundle lifecycle state is represented by LSB.
        return (state & 0xff) == Bundle.ACTIVE;
    }

    /**
     * Return {@code true} if the {@code bundle} is an
     * OSGi <a href="https://www.osgi.org/developer/white-papers/semantic-versioning/bundles-and-fragments/">fragment</a>.
     * OSGi fragments are not subject to activation.
     *
     * @param bundle to check if it is fragment.
     *
     * @return Return {@code true} if the {@code bundle} is an OSGi fragment.
     */
    static boolean isFragment(Bundle bundle) {
        return bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null;
    }

    /**
     * Return {@code true} if the {@code state} LSB is between {@link Bundle#UNINSTALLED} and {@link Bundle#STOPPING} excluded
     * because the bundle is startable if {link Bundle#getState} is inside this range.
     *
     * Bundle states are expressed as a bit-mask though a bundle can only be in one state at any time,
     * the state in the lifecycle is represented in the LSB of the value returned by {@link Bundle#getState}.
     * See OSGi Core Release 7 <a href="https://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html">4.4.2 Bundle State</a>
     *
     * @param state of the bundle.
     *
     * @return {@code true} if the {@code state} LSB is between {@link Bundle#UNINSTALLED} and {@link Bundle#STOPPING} excluded.
     */
    private static boolean isStartable(int state) {
        // The bundle lifecycle state is represented by LSB.
        final int status = state & 0xff;
        return status > Bundle.UNINSTALLED && status < Bundle.STOPPING;
    }

    /**
     * Return `true` if the {@code state} LSB is between {@link Bundle#STARTING} and {@link Bundle#ACTIVE} excluded
     * because the bundle is stoppable if {@link Bundle#getState} is in this range.
     *
     * Bundle states are expressed as a bit-mask though a bundle can only be in one state at any time,
     * the state in the lifecycle is represented in the LSB of the value returned by [Bundle.getState].
     * See OSGi Core Release 7 <a href="https://docs.osgi.org/specification/osgi.core/7.0.0/framework.lifecycle.html">4.4.2 Bundle State</a>
     *
     * @param state of the bundle.
     *
     * @return {@code true} if the {@code state} LSB is between {@link Bundle#STARTING} and {@link Bundle#ACTIVE} excluded.
     */
    static boolean isStoppable(int state) {
        // The bundle lifecycle state is represented by LSB.
        final int status = state & 0xff;
        return status > Bundle.STARTING && state <= Bundle.ACTIVE;
    }

    private final Framework framework;

    /**
     * Map of the descriptors of bundles installed.
     */
    private final ConcurrentMap<Long, OSGiBundleDescriptor> bundleDescriptorMap;

    /**
     * @param framework to bootstrap.
     * Get the framework with {@link #getFrameworkFrom} if the framework and its factory are in this classpath.
     */
    OSGiFrameworkWrap(Framework framework) {
        this.framework = framework;
        bundleDescriptorMap = new ConcurrentHashMap<>();
    }

    /**
     * Activate (start) the bundles installed with {@link #install}.
     * Call the {@code start} methods of the classes implementing `BundleActivator` in the activated bundle.
     *
     * Bundle activation is idempotent.
     *
     * Thread safe.
     *
     * @return this.
     *
     * @throws BundleException if any bundled installed fails to start.
     * The first bundle failing to start interrupts the activation of each bundle it should activate next.
     */
    OSGiFrameworkWrap activate() throws BundleException {
        // Resolve every installed bundle together, as a unit.
        framework.adapt(FrameworkWiring.class).resolveBundles(null);

        final List<Bundle> bundles = bundleDescriptorMap.values().stream().map(OSGiBundleDescriptor::getBundle)
            .sorted(comparing(Bundle::getSymbolicName))
            .collect(toUnmodifiableList());
        for (Bundle bundle: bundles) {
            if (isFragment(bundle)) {
                logger.info("OSGi bundle {} ID = {} {} {} {} fragment.",
                    bundle.getLocation(),
                    bundle.getBundleId(),
                    bundle.getSymbolicName(),
                    bundle.getVersion(),
                    bundleStateMap.get(bundle.getState())
                );
            } else {
                bundle.start(Bundle.START_ACTIVATION_POLICY);
            }
        }
        return this;
    }

    /**
     * Return {@link Framework#getState} value.
     */
    int getState() {
        return framework.getState();
    }

    /**
     * Install the bundles represented by the {@code resource} in this classpath in the {@link Framework} wrapped by this object.
     * All installed bundles starts with the method {@link #activate}.
     *
     * Thread safe.
     *
     * @param resource represents the path in the classpath where bundles are described.
     * The resource can be:
     * * the bundle {@code .jar} file;
     * * the file describing where bundles are, for example the file {@code system_bundles} at the root of the classpath.
     *
     * Any {@code resource} not terminating with the {@code .jar} extension is considered a list of bundles.
     *
     * @return this.
     *
     * @throws BundleException If the bundle represented in the {@code resource} fails to install.
     * @throws IllegalStateException If the wrapped {@link Framework} is not active.
     * @throws IOException If the {@code resource} can't be read.
     * @throws SecurityException If the caller does not have the appropriate
     *         {@code AdminPermission[installed bundle,LIFECYCLE]}, and the Java Runtime Environment supports permissions.
     *
     * @see #installBundleJar
     * @see #installBundleList
     */
    synchronized  OSGiFrameworkWrap install(String resource) throws BundleException, IOException {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        if (resource.endsWith(JAR_EXTENSION)) {
            installBundleJar(resource, contextClassLoader);
        } else {
            installBundleList(resource, contextClassLoader);
        }

        return this;
    }

    /**
     * Install the bundle of the {@code jar} file represented in the {@code resource}.
     *
     * @param resource representing the bundle {@code .jar} file in the classpath.
     *                 The {@code resource} is read through {@link ClassLoader#getResource}.
     * @param classLoader used to read the {@code resource}.
     *
     * @throws BundleException If the bundle represented in the {@code resource} fails to install.
     * @throws IllegalStateException If the wrapped {@link Framework} is not active.
     * @throws IOException If the {@code resource} can't be read.
     * @throws SecurityException If the caller does not have the appropriate
     *         {@code AdminPermission[installed bundle,LIFECYCLE]}, and the Java Runtime Environment supports permissions.
     *
     * @see #install
     */
    private void installBundleJar(String resource, ClassLoader classLoader) throws BundleException, IOException {
        logger.debug("OSGi bundle {} installing...", resource);
        final URL resourceUrl = classLoader.getResource(resource);
        if (resourceUrl == null) {
            throw new IOException("OSGi bundle at " + resource + " not found");
        }
        try (InputStream inputStream = resourceUrl.openStream()) {
            final BundleContext bundleContext = framework.getBundleContext();
            if (bundleContext == null) {
                throw new IllegalStateException("OSGi framework not active yet.");
            }
            final Bundle bundle = bundleContext.installBundle(resource, inputStream);
            if (bundle.getSymbolicName() == null) {
                logger.error("Bundle {} has no symbolic name so is not a valid OSGi bundle; skipping", resourceUrl);
                bundle.uninstall();
            } else {
                if (!isFragment(bundle)) {
                    bundleDescriptorMap.put(bundle.getBundleId(), new OSGiBundleDescriptor(bundle));
                }
                logger.debug("OSGi bundle {} installed.", resource);
            }
        }
    }

    OSGiFrameworkWrap installFromDirectory(Path directory) throws BundleException, IOException {
        if (directory == null) {
            logger.info("No custom bundle folder specified, not loading bundles from *disk*");
        } else {
            if (Files.isDirectory(directory)) {
                final List<Path> paths;
                try (Stream<Path> stream = Files.walk(directory, 1)) {
                    paths = stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(JAR_EXTENSION))
                        .collect(toList());
                }
                // We want to install all files from this directory.
                // In the case of jdbc drivers we need the jdbc `Bundle` and
                // a companion pax-jdbc `Bundle` to register it for us.
                for (Path path: paths) {
                    installBundleFile(path.toUri());
                }
            }
        }
        return this;
    }

    /**
     * Install a bundle from the file system.
     *
     * @param fileUri URI to the file, i.e. `file:///tmp/some.jar`
     */
    private void installBundleFile(URI fileUri) throws BundleException, IOException {
        logger.debug("OSGi bundle {} installing...", fileUri);

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(fileUri.getPath()))) {
            final BundleContext bundleContext = framework.getBundleContext();
            if (bundleContext == null) {
                throw new IllegalStateException("OSGi framework not active yet.");
            }
            final Bundle bundle = bundleContext.installBundle(fileUri.getPath(), inputStream);


            // This is the mechanism by which we will allow customers to load their own JDBC drivers,
            // so we must report that visibly in the logs.
            if (bundle.getSymbolicName() == null) {
                logger.error("Bundle {} has no symbolic name so is not a valid OSGi bundle; uninstalling", fileUri);
                bundle.uninstall();
            } else {
                if (!isFragment(bundle)) {
                    bundleDescriptorMap.put(bundle.getBundleId(), new OSGiBundleDescriptor(bundle));
                }
                logger.info("OSGi bundle {} installed - {} {}", fileUri, bundle.getSymbolicName(), bundle.getVersion());
            }
        }
    }

    /**
     * Install the bundles listed in the {@code resource} file.
     * Each line represents the path to the resource representing one bundle.
     * Line text after the `#` char is ignored.
     * The resources are read through {@link ClassLoader#getResource}.
     *
     * @param resource representing the file list of the path to the resources representing the bundles to install.
     *                 The {@code resource} is read through {@link ClassLoader#getResource}.
     * @param classLoader used to read the {@code resource}.
     *
     * @throws BundleException If the bundle represented in the {@code resource} fails to install.
     * @throws IllegalStateException If the wrapped {@link Framework} is not active.
     * @throws IOException If the {@code resource} can't be read.
     * @throws SecurityException If the caller does not have the appropriate
     *         {@code AdminPermission[installed bundle,LIFECYCLE]}, and the Java Runtime Environment supports permissions.
     *
     * @see #install
     */
    private void installBundleList(String resource, ClassLoader classLoader) throws BundleException, IOException {
        final URL resourceUrl = classLoader.getResource(resource);
        if (resourceUrl == null) {
            throw new IOException("OSGi bundle list at " + resource + " not found");
        }

        logger.info("OSGi bundle list at {} loading...", resource);
        final List<String> bundleResources;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream()))) {
            bundleResources = reader.lines().map(OSGiFrameworkWrap::removeTrailingComment)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(toList());
        }
        for (String bundleResource: bundleResources) {
            install(bundleResource);
        }
        logger.info("OSGi bundle list at {} loaded.", resource);
    }

    /**
     * Start the {@link Framework} wrapped by this {@link OSGiFrameworkWrap}.
     * If the {@link Framework} can't start, the method logs a warning describing the actual state of the framework.
     * Start the framework multiple times is harmless, it just logs the warning.
     *
     * This method registers the {@link Shutdown} used by applications to ask to quit.
     * The {@link Shutdown#shutdown} implementation calls {@link #stop}: both this method and {@link #stop} are synchronized,
     * but there is no risk of deadlock because applications start-up from synchronized {@link #startApplication},
     * it runs only after this method returned and the service is registered.
     * The {@link Shutdown#shutdown} runs {@link #stop} in a separate thread.
     *
     * Thread safe.
     *
     * @return this.
     *
     * @throws BundleException If the wrapped {@link Framework} could not be started.
     * @throws IllegalStateException If the {@link Framework#getBundleContext} return an invalid object,
     * something should never happen for the OSGi system bundle.
     * @throws SecurityException If the caller does not have the appropriate {@code AdminPermission[this,EXECUTE]},
     * and the Java Runtime Environment supports permissions.
     *
     * @see Framework#start
     */
    synchronized OSGiFrameworkWrap start() throws BundleException {
        if (isStartable(framework.getState())) {
            framework.start();
            framework.getBundleContext().addBundleListener(bundleEvent -> {
                final Bundle bundle = bundleEvent.getBundle();
                if (isActive(bundle.getState())) {
                    final OSGiBundleDescriptor descriptor = bundleDescriptorMap.get(bundle.getBundleId());
                    if (descriptor != null) {
                        descriptor.getActive().countDown();
                    }
                }
                logger.info("OSGi bundle {} ID = {} {} {} {}.",
                    bundle.getLocation(),
                    bundle.getBundleId(),
                    bundle.getSymbolicName(),
                    bundle.getVersion(),
                    bundleStateMap.get(bundle.getState())
                );
            });
            framework.getBundleContext().registerService(
                Shutdown.class,
                // Called by applications using the 'ShutdownBootstrapper'.
                // No risk of deadlock because applications are registered by [startApplication]
                // after this method returned and [stop] runs in separate thread.
                bundle -> new Thread(OSGiFrameworkWrap.this::stop, "framework-stop").start(),
                null
            );
            logger.info("OSGi framework {} {} started.", framework.getClass().getName(), framework.getVersion());
        } else {
            logger.warn("OSGi framework {} start attempted: state is {}",
                framework.getClass().getName(),
                bundleStateMap.get(framework.getState())
            );
        }
        return this;
    }

    /**
     * Call the {@link Application#startup} method of the class implementing the {@link Application} interface:
     * this is the entry-point of the application distributed in the bootable JAR.
     *
     * If no class implements the {@link Application} interface in any bundle zipped in the bootable JAR,
     * it throws {@link ClassNotFoundException} exception.
     *
     * This method waits {@code timeout} ms for all bundles to be active,
     * if any bundle is not active yet, it logs a warning and try to startup the application.
     *
     * Thread safe.
     *
     * @param timeout in milliseconds to wait application bundles to be active before to call
     *          {@link Application#startup}.
     * @param args to pass to the {@link Application#startup} method of application bundles.
     *
     * @return this.
     *
     * @throws ClassNotFoundException If no class implements the {@link Application} interface in any bundle
     *          zipped in the bootable JAR
     * @throws IllegalStateException If this bundle has been uninstalled meanwhile this method runs.
     * @throws SecurityException If a security manager is present and the caller's class loader is not the same as,
     *          or the security manager denies access to the package of this class.
     */
    synchronized OSGiFrameworkWrap startApplication(
        long timeout,
        String[] args
    ) throws ClassNotFoundException, InterruptedException {
        for (OSGiBundleDescriptor bundleDescriptor: bundleDescriptorMap.values()) {
            if (!bundleDescriptor.getActive().await(timeout, MILLISECONDS)) {
                final Bundle bundle = bundleDescriptor.getBundle();
                logger.warn("OSGi bundle {} ID = {} {} {} {} activation time-out after {} ms.",
                    bundle.getLocation(),
                    bundle.getBundleId(),
                    bundle.getSymbolicName(),
                    bundle.getVersion(),
                    bundleStateMap.get(bundle.getState()),
                    timeout
                );
            }
        }
        final BundleContext frameworkContext = framework.getBundleContext();
        final ServiceReference<Application> applicationServiceReference = frameworkContext.getServiceReference(Application.class);

        //  Log errors that occur in the OSGi framework, such trying to instantiate missing implementations.
        frameworkContext.addFrameworkListener(evt -> {
            if ((evt.getType() & FrameworkEvent.ERROR) != 0) {
                final Throwable throwable = evt.getThrowable();
                logger.error(throwable.getLocalizedMessage(), throwable.getCause());
            }
        });

        if (applicationServiceReference != null) {
            final Application application = frameworkContext.getService(applicationServiceReference);
            if (application != null) {
                application.startup(args);
            } else {
                logger.error("Your Application could not be instantiated:\n" +
                        "* Check your constructor @Reference parameters\n" +
                        "  Remove all parameters and add them back one at a time to locate the problem.\n" +
                        "* Split packages are NOT allowed in OSGi:\n" +
                        "  check that your interface (bundle) and impl (bundle) are in different packages");
            }
        } else {
            throw new ClassNotFoundException(
                "No class implementing " + Application.class.getName() + " found to start the application.\n" +
                    "Check if the class implementing " + Application.class.getName() +
                    " has properties annotated with @Reference(service = <class>).\n" +
                    "Each referred <class> must be annotated as @Component(service = [<class>])" +
                    " else the class implementing " + Application.class.getName() + " can't be found at bootstrap." );
        }
        return this;
    }

    /**
     * This method performs the following actions to stop the application running in the OSGi framework.
     *
     *  1. Calls the {@link Application#shutdown} method of the class implementing the {@link Application} interface.
     *      If no class implements the {@link Application} interface in any bundle zipped in the bootable JAR,
     *      it logs a warning message and continues to shutdowns the OSGi framework.
     *  2. Deactivate installed bundles.
     *  3. Stop the {@link Framework} wrapped by this {@link OSGiFrameworkWrap}.
     *      If the {@link Framework} can't stop, the method logs a warning describing the actual state of the framework.
     *
     * To stop the framework multiple times is harmless, it just logs the warning.
     *
     * Thread safe.
     *
     * @return true if the framework stopped successfully, and false otherwise.
     *
     * @throws SecurityException  If the caller does not have the appropriate {@code AdminPermission[this,EXECUTE]},
     * and the Java Runtime Environment supports permissions.
     *
     * @see Framework#stop
     */
    synchronized boolean stop() {
        if (isStoppable(framework.getState())) {
            logger.debug("OSGi framework stop...");
            final BundleContext frameworkContext = framework.getBundleContext();
            final ServiceReference<Application> applicationServiceReference = frameworkContext.getServiceReference(Application.class);
            if (applicationServiceReference != null) {
                try {
                    final Application application = frameworkContext.getService(applicationServiceReference);
                    if (application != null) {
                        application.shutdown();
                    }
                } finally {
                    // Service objects are reference counted.
                    // Release the reference we took here, and also
                    // the one we took when starting the application.
                    // The framework will deactivate the [Application]
                    // service once its reference count reaches zero.
                    if (frameworkContext.ungetService(applicationServiceReference)) {
                        frameworkContext.ungetService(applicationServiceReference);
                    }
                }
            } else {
                logger.warn("{} service unregistered while application is still running.", Application.class.getName());
            }
            try {
                framework.stop();
            } catch (BundleException e) {
                logger.error("OSGi framework failed to stop", e);
                return false;
            }
        } else {
            logger.warn(
                "OSGi framework {} stop attempted: state is {}",
                framework.getClass().getName(),
                bundleStateMap.get(framework.getState())
            );
        }
        logger.debug("OSGi framework stopped");
        return true;
    }

    /**
     * Wait until this Framework has completely stopped.
     *
     * This method will only wait if called when the wrapped {@link Framework} is in the {@link Bundle#STARTING},
     * {@link Bundle#ACTIVE} or {@link Bundle#STOPPING} states, otherwise it will return immediately.
     *
     * @param timeout Maximum number of milliseconds to wait until the framework has completely stopped.
     * A value of zero will wait indefinitely.
     * @return A {@link FrameworkEvent} indicating the reason this method returned.
     * The following {@link FrameworkEvent} types may be returned:
     * * {@link FrameworkEvent#STOPPED} - The wrapped {@link Framework} has been stopped or never started.
     * * {@link FrameworkEvent#STOPPED_UPDATE} - The wrapped {@link Framework} has been updated which has shutdown
     *   and will restart now.
     * * {@link FrameworkEvent#STOPPED_SYSTEM_REFRESHED} - The wrapped {@link Framework} has been stopped because a refresh
     *   operation on the system bundle.
     * * {@link FrameworkEvent#ERROR} - The wrapped {@link Framework} encountered an error while shutting down or an error
     *   has occurred which forced the framework to shutdown.
     * * {@link FrameworkEvent#WAIT_TIMEDOUT} - This method has timed out and returned before this Framework has stopped.
     *
     * @throws InterruptedException If another thread interrupted the current thread before or while the current
     * thread was waiting for this Framework to completely stop.
     * @throws IllegalArgumentException If the value of timeout is negative.
     *
     * @see Framework#waitForStop
     */
    FrameworkEvent waitForStop(long timeout) throws InterruptedException {
        final FrameworkEvent frameworkEvent = framework.waitForStop(timeout);
        if (frameworkEvent != null ) {
            switch (frameworkEvent.getType()) {
                case FrameworkEvent.ERROR:
                    final Throwable throwable = frameworkEvent.getThrowable();
                    logger.error("OSGi framework stop error: " + throwable.getMessage(), throwable);
                    break;
                case FrameworkEvent.STOPPED:
                    logger.info("OSGi framework {} {} stopped.", framework.getClass().getCanonicalName(), framework.getVersion());
                    break;
                case FrameworkEvent.WAIT_TIMEDOUT:
                    logger.warn("OSGi framework {} {} time out!", framework.getClass().getCanonicalName(), framework.getVersion());
                    break;
                default:
                    logger.error("OSGi framework stop: unknown event type {}", frameworkEvent.getType());
                    break;
                }
        }
        return frameworkEvent;
    }

    //: AutoCloseable

    /**
     * Call {@link #stop}, implemented to provide {@link OSGiFrameworkWrap} in {@code try-with-resources/use} block.
     */
    @Override
    public void close() {
        stop();
    }

}
