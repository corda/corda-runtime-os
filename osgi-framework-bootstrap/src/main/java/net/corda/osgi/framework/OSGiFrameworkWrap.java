package net.corda.osgi.framework;

import net.corda.osgi.api.Application;
import net.corda.osgi.api.Shutdown;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableMap;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static net.corda.osgi.framework.OSGiFrameworkUtils.isFragmentBundle;
import static net.corda.osgi.framework.OSGiFrameworkUtils.isBundleStartable;
import static net.corda.osgi.framework.OSGiFrameworkUtils.isBundleStoppable;

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
     * Wait for stop of the OSGi framework, without timeout.
     */
    private static final long NO_TIMEOUT = 0L;

    /**
     * Extension used to identify {@code jar} files to {@link #install}.
     *
     * @see #install
     */
    private static final String JAR_EXTENSION = ".jar";

    /**
     * Header name for bundle activation policy.
     */
    private static final String BUNDLE_ACTIVATION_POLICY_HEADER = "Bundle-ActivationPolicy";

    /**
     * Value of lazy activation policy header.
     */
    private static final String BUNDLE_ACTIVATION_POLICY_LAZY = "lazy";

    private final Framework framework;

    /**
     * List of bundles installed.
     */
    private final List<Bundle> bundles = Collections.synchronizedList(new ArrayList<>());

    private static final Logger logger = LoggerFactory.getLogger(OSGiFrameworkMain.class);

    /**
     * @param framework to bootstrap.
     */
    OSGiFrameworkWrap(Framework framework) {
        this.framework = framework;
    }

    /**
     * Activate (start) the bundles installed with {@link #install}.
     * Call the {@code start} methods of the classes implementing {@link BundleActivator} in the activated bundle.
     * <p>
     * Bundle activation is idempotent.
     * <p>
     * Thread safe.
     *
     * @return this.
     * @throws BundleException if any bundled installed fails to start.
     *                         The first bundle failing to start interrupts the activation of each bundle it should activate next.
     */
    OSGiFrameworkWrap activate() throws BundleException {
        // Resolve every installed bundle together, as a unit.
        framework.adapt(FrameworkWiring.class).resolveBundles(null);

        final List<Bundle> sortedBundles = this.bundles.stream()
                .sorted(comparing(Bundle::getSymbolicName))
                .collect(toUnmodifiableList());
        for (Bundle bundle : sortedBundles) {
            if (isFragmentBundle(bundle)) {
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
     * <p>
     * Thread safe.
     *
     * @param resource represents the path in the classpath where bundles are described.
     *                 The resource can be:
     *                 * the bundle {@code .jar} file;
     *                 * the file describing where bundles are, for example the file {@code system_bundles} at the root of the classpath.
     *                 <p>
     *                 Any {@code resource} not terminating with the {@code .jar} extension is considered a list of bundles.
     * @return this.
     * @throws BundleException       If the bundle represented in the {@code resource} fails to install.
     * @throws IllegalStateException If the wrapped {@link Framework} is not active.
     * @throws IOException           If the {@code resource} can't be read.
     * @throws SecurityException     If the caller does not have the appropriate
     *                               {@code AdminPermission[installed bundle,LIFECYCLE]}, and the Java Runtime Environment supports permissions.
     * @see #installBundleJar
     * @see #installBundleList
     */
    synchronized OSGiFrameworkWrap install(String resource) throws BundleException, IOException {
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
     * @param resource    representing the bundle {@code .jar} file in the classpath.
     *                    The {@code resource} is read through {@link ClassLoader#getResource}.
     * @param classLoader used to read the {@code resource}.
     * @throws BundleException       If the bundle represented in the {@code resource} fails to install.
     * @throws IllegalStateException If the wrapped {@link Framework} is not active.
     * @throws IOException           If the {@code resource} can't be read.
     * @throws SecurityException     If the caller does not have the appropriate
     *                               {@code AdminPermission[installed bundle,LIFECYCLE]}, and the Java Runtime Environment supports permissions.
     * @see #install
     */
    private void installBundleJar(String resource, ClassLoader classLoader) throws BundleException, IOException {
        logger.debug("OSGi bundle {} installing...", resource);
        final URL resourceUrl = classLoader.getResource(resource);
        if (resourceUrl == null) {
            throw new IOException("OSGi bundle at " + resource + " not found");
        }
        try (InputStream inputStream = new FixLogging(resourceUrl.openStream())) {
            final BundleContext bundleContext = framework.getBundleContext();
            if (bundleContext == null) {
                throw new IllegalStateException("OSGi framework not active yet.");
            }
            final Bundle bundle = bundleContext.installBundle(resource, inputStream);
            if (bundle.getSymbolicName() == null) {
                logger.error("Bundle {} has no symbolic name so is not a valid OSGi bundle; skipping", resourceUrl);
                bundle.uninstall();
            } else {
                if (!isFragmentBundle(bundle)) {
                    bundles.add(bundle);
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
                for (Path path : paths) {
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

        try (InputStream inputStream = new FixLogging(new BufferedInputStream(new FileInputStream(fileUri.getPath())))) {
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
                if (!isFragmentBundle(bundle)) {
                    bundles.add(bundle);
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
     * @param resource    representing the file list of the path to the resources representing the bundles to install.
     *                    The {@code resource} is read through {@link ClassLoader#getResource}.
     * @param classLoader used to read the {@code resource}.
     * @throws BundleException       If the bundle represented in the {@code resource} fails to install.
     * @throws IllegalStateException If the wrapped {@link Framework} is not active.
     * @throws IOException           If the {@code resource} can't be read.
     * @throws SecurityException     If the caller does not have the appropriate
     *                               {@code AdminPermission[installed bundle,LIFECYCLE]}, and the Java Runtime Environment supports permissions.
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
            bundleResources = reader.lines().map(OSGiFrameworkUtils::removeTrailingComment)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(toList());
        }
        for (String bundleResource : bundleResources) {
            install(bundleResource);
        }
        logger.info("OSGi bundle list at {} loaded.", resource);
    }

    /**
     * Start the {@link Framework} wrapped by this {@link OSGiFrameworkWrap}.
     * If the {@link Framework} can't start, the method logs a warning describing the actual state of the framework.
     * Start the framework multiple times is harmless, it just logs the warning.
     * <p>
     * This method registers the {@link Shutdown} used by applications to ask to quit.
     * The {@link Shutdown#shutdown} implementation calls {@link #stop}: both this method and {@link #stop} are synchronized,
     * but there is no risk of deadlock because applications start-up from synchronized {@link #startApplication},
     * it runs only after this method returned and the service is registered.
     * The {@link Shutdown#shutdown} runs {@link #stop} in a separate thread.
     * <p>
     * Thread safe.
     *
     * @return this.
     * @throws BundleException       If the wrapped {@link Framework} could not be started.
     * @throws IllegalStateException If the {@link Framework#getBundleContext} return an invalid object,
     *                               something should never happen for the OSGi system bundle.
     * @throws SecurityException     If the caller does not have the appropriate {@code AdminPermission[this,EXECUTE]},
     *                               and the Java Runtime Environment supports permissions.
     * @see Framework#start
     */
    synchronized OSGiFrameworkWrap start() throws BundleException {
        if (isBundleStartable(framework.getState())) {
            framework.init();
            framework.getBundleContext().addBundleListener(bundleEvent -> {
                final Bundle bundle = bundleEvent.getBundle();
                logger.info("OSGi bundle {} ID = {} {} {} {}.",
                        bundle.getLocation(),
                        bundle.getBundleId(),
                        bundle.getSymbolicName(),
                        bundle.getVersion(),
                        bundleStateMap.get(bundle.getState())
                );
            });
            framework.start();
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
     * <p>
     * If no class implements the {@link Application} interface in any bundle zipped in the bootable JAR,
     * it throws {@link ClassNotFoundException} exception.
     * <p>
     * if any bundle is not active yet, it logs a warning and try to startup the application.
     * <p>
     * Thread safe.
     *
     * @param args to pass to the {@link Application#startup} method of application bundles.
     * @return this.
     * @throws ClassNotFoundException If no class implements the {@link Application} interface in any bundle
     *                                zipped in the bootable JAR
     * @throws IllegalStateException  If this bundle has been uninstalled meanwhile this method runs.
     * @throws SecurityException      If a security manager is present and the caller's class loader is not the same as,
     *                                or the security manager denies access to the package of this class.
     */
    synchronized OSGiFrameworkWrap startApplication(
            String[] args
    ) throws ClassNotFoundException {
        for (Bundle bundle : bundles) {
            if (bundle.getState() != Bundle.ACTIVE) {
                if (bundle.getHeaders().get(BUNDLE_ACTIVATION_POLICY_HEADER).equals(BUNDLE_ACTIVATION_POLICY_LAZY)) {
                    if (bundle.getState() != Bundle.STARTING) {
                        logger.warn("OSGi bundle {} ID = {} {} {} is in state {} but should be starting (it is marked as lazy activation) or active",
                                bundle.getLocation(),
                                bundle.getBundleId(),
                                bundle.getSymbolicName(),
                                bundle.getVersion(),
                                bundleStateMap.get(bundle.getState())
                        );
                    }
                } else {
                    logger.warn("OSGi bundle {} ID = {} {} {} is in state {} but should be active",
                            bundle.getLocation(),
                            bundle.getBundleId(),
                            bundle.getSymbolicName(),
                            bundle.getVersion(),
                            bundleStateMap.get(bundle.getState())
                    );
                }
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
                            " else the class implementing " + Application.class.getName() + " can't be found at bootstrap.");
        }
        return this;
    }

    /**
     * This method performs the following actions to stop the application running in the OSGi framework.
     * <ol>
     * <li>Calls the {@link Application#shutdown} method of the class implementing the {@link Application} interface.
     *     If no class implements the {@link Application} interface in any bundle zipped in the bootable JAR,
     *     it logs a warning message and continues to shutdowns the OSGi framework.
     *  <li>Deactivate installed bundles.</li>
     *  <li>Stop the {@link Framework} wrapped by this {@link OSGiFrameworkWrap}.
     *      If the {@link Framework} can't stop, the method logs a warning describing the actual state of the framework.</li>
     * </ol>
     * To stop the framework multiple times is harmless, it just logs the warning.
     * <p>
     * Thread safe.
     *
     * @return true if the framework stopped successfully, and false otherwise.
     * @throws SecurityException If the caller does not have the appropriate {@code AdminPermission[this,EXECUTE]},
     *                           and the Java Runtime Environment supports permissions.
     * @see Framework#stop
     */
    synchronized boolean stop() {
        if (isBundleStoppable(framework.getState())) {
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
     * <p>
     * This method will only wait if called when the wrapped {@link Framework} is in the {@link Bundle#STARTING},
     * {@link Bundle#ACTIVE} or {@link Bundle#STOPPING} states, otherwise it will return immediately.
     *
     * @return A {@link FrameworkEvent} indicating the reason this method returned.
     * The following {@link FrameworkEvent} types may be returned:
     * * {@link FrameworkEvent#STOPPED} - The wrapped {@link Framework} has been stopped or never started.
     * * {@link FrameworkEvent#STOPPED_UPDATE} - The wrapped {@link Framework} has been updated which has shutdown
     * and will restart now.
     * * {@link FrameworkEvent#STOPPED_SYSTEM_REFRESHED} - The wrapped {@link Framework} has been stopped because a refresh
     * operation on the system bundle.
     * * {@link FrameworkEvent#ERROR} - The wrapped {@link Framework} encountered an error while shutting down or an error
     * has occurred which forced the framework to shutdown.
     * * {@link FrameworkEvent#WAIT_TIMEDOUT} - This method has timed out and returned before this Framework has stopped.
     * @throws InterruptedException     If another thread interrupted the current thread before or while the current
     *                                  thread was waiting for this Framework to completely stop.
     * @throws IllegalArgumentException If the value of timeout is negative.
     * @see Framework#waitForStop
     */
    FrameworkEvent waitForStop() throws InterruptedException {
        final FrameworkEvent frameworkEvent = framework.waitForStop(NO_TIMEOUT);
        if (frameworkEvent != null) {
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
