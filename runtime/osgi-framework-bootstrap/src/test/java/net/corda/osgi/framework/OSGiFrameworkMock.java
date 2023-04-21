package net.corda.osgi.framework;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Mockito.mock;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import java.io.File;
import java.io.InputStream;
import java.lang.IllegalArgumentException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

final class OSGiFrameworkMock implements Framework {
    static final int GO_LATCH = 0;
    static final int INIT_START_LEVEL = 0;
    static final int SAFE_START_LEVEL = 1;
    static final int WAIT_LATCH = 1;

    private final Map<String, String> configurationMap;
    private final int beginningStartLevel;

    private final AtomicReference<OSGiBundleContextMock> bundleContextAtomic;
    private final AtomicLong bundleIdAccumulator;
    private final ConcurrentMap<Long, Bundle> bundleMap;
    private final ConcurrentMap<String, Bundle> bundleLocationMap;
    private final AtomicReference<CountDownLatch> shutdownLatchAtomic;
    private final AtomicInteger stateAtomic;
    private final AtomicInteger startLevelAtomic;
    private final AtomicReference<Version> versionAtomic;

    /**
     * OSGi Core r7 4.2.8
     * Each framework must have a unique identity every time before the framework is started.
     *
     * @see #init
     */
    private final AtomicReference<UUID> uuidAtomic;

    OSGiFrameworkMock(Map<String, String> configurationMap, Version version, int beginningStartLevel) {
        this.configurationMap = configurationMap;
        this.beginningStartLevel = beginningStartLevel;
        this.bundleContextAtomic = new AtomicReference<>();
        this.bundleIdAccumulator = new AtomicLong(Constants.SYSTEM_BUNDLE_ID);
        this.bundleMap = new ConcurrentHashMap<>();
        this.bundleLocationMap = new ConcurrentHashMap<>();
        this.shutdownLatchAtomic = new AtomicReference<>(new CountDownLatch(GO_LATCH));
        this.stateAtomic = new AtomicInteger(Bundle.INSTALLED);
        this.startLevelAtomic = new AtomicInteger(INIT_START_LEVEL);
        this.versionAtomic = new AtomicReference<>(version);
        this.uuidAtomic = new AtomicReference<>();

        bundleMap.put(Constants.SYSTEM_BUNDLE_ID, this);
        bundleLocationMap.put(Constants.SYSTEM_BUNDLE_LOCATION, this);
    }

    OSGiFrameworkMock(Map<String, String> configurationMap) {
        this(configurationMap, new Version(0, 0, 0, "mock"), SAFE_START_LEVEL);
    }

    Bundle getBundle(String location) {
        return bundleLocationMap.get(location);
    }

    Bundle getBundle(long id) {
        return bundleMap.get(id);
    }

    Bundle[] getBundles() {
        return bundleMap.values().toArray(new Bundle[0]);
    }

    OSGiBundleMock installBundle(String location) {
        final long bundleId = bundleIdAccumulator.incrementAndGet();
        final OSGiBundleMock bundle = new OSGiBundleMock(bundleId, location);
        bundleMap.put(bundleId, bundle);
        bundleLocationMap.put(location, bundle);
        return bundle;
    }

    // : Framework

    /**
     * See {@link Framework#getBundleId}.
     *
     * @return {@link Constants#SYSTEM_BUNDLE_ID}.
     */
    @Override
    public long getBundleId() {
        return Constants.SYSTEM_BUNDLE_ID;
    }

    /**
     * See {@link Framework#getLocation}.
     *
     * @return The string "System Bundle".
     */
    @Override
    public String getLocation() {
        return Constants.SYSTEM_BUNDLE_LOCATION;
    }

    /**
     * See {@link Framework#getSymbolicName}.
     *
     * @return The string "system.bundle".
     */
    @Override
    public String getSymbolicName() {
        return Constants.SYSTEM_BUNDLE_SYMBOLICNAME;
    }

    /**
     * See {@link Framework#init}.
     *
     * @throws BundleException if this Framework could not be initialized.
     */
    @Override
    public void init() throws BundleException {
        init(new FrameworkListener[0]);
    }

    /**
     * See {@link Framework#init}.
     *
     * This Framework will not actually be started until {@link #start} is called,
     * but if {@link #start} is called before this method, {@link #start} calls {@link #init}.
     *
     * The method is effective if bundle state is {@link Bundle#INSTALLED} or {@link Bundle#RESOLVED} or {@link Bundle#UNINSTALLED}.
     *
     * @param listeners Zero or more listeners to be notified when framework events occur
     * only while initializing the framework.
     *
     * @throws BundleException if this Framework could not be initialized.
     */
    @Override
    public void init(FrameworkListener... listeners) throws BundleException {
        // Effective only when this Framework is in Bundle.INSTALLED or Bundle.RESOLVED or Bundle.UNINSTALLED.
        switch (startLevelAtomic.get()) {
            // This method does nothing if called when this Framework is
            // in the `Bundle.STARTING`, `Bundle.ACTIVE` or `Bundle.STOPPING` states.
            case Bundle.STARTING:
            case Bundle.ACTIVE:
            case Bundle.STOPPING:
                return;
        }

        // Be in the `Bundle.STARTING` state.
        stateAtomic.set(Bundle.STARTING);

        // After calling this method, this Framework must:
        // Have generated a new framework UUID.
        uuidAtomic.set(UUID.randomUUID());
        // Have a valid Bundle Context.
        bundleContextAtomic.set(new OSGiBundleContextMock(this, this));

        // Be at start level 0.
        startLevelAtomic.set(INIT_START_LEVEL);
        // Have event handling enabled.

        // Have reified Bundle objects for all installed bundles.

        // Have registered any framework services.

        // Be adaptable to the OSGi defined types to which a system bundle can be adapted.

        // Have called the start method of the extension bundle activator for all resolved extension bundles.
        final FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, this, null);
        for (FrameworkListener listener: listeners) {
            listener.frameworkEvent(frameworkEvent);
        }
    }

    /**
     * See {@link Framework#start}.
     *
     * If {@link #init} wasn't called before, call {@link #init}.
     *
     *  @throws BundleException if this {@link Framework} could not be started.
     *  @throws SecurityException if the caller does not have the appropriate {@code AdminPermission(this,EXECUTE)},
     * and the Java Runtime Environment supports permissions.
     */
    @Override
    public void start() throws BundleException {
        // If this Framework is not in the Bundle.STARTING state, initialize this Framework.
        if (stateAtomic.get() != Bundle.STARTING) {
            init();
        }

        // All installed bundles must be started in accordance with each bundle's persistent autostart setting.

        // The start level of this Framework is moved to the start level specified by the beginning start level
        // framework property, as described in the Start Level Specification.
        startLevelAtomic.set(beginningStartLevel);
        // This Framework's state is set to Bundle.ACTIVE.
        stateAtomic.set(Bundle.ACTIVE);

        // A bundle event of type BundleEvent.STARTED is fired.
        bundleContextAtomic.get().notifyToListeners(new BundleEvent(BundleEvent.STARTED, this));
        // A framework event of type Framework.STARTED is fired.
    }

    /**
     * See {@link Framework#start}.
     *
     * @param ignored There are no start options for the {@link Framework}.
     * @throws BundleException If this Framework could not be started.
     * @throws SecurityException If the caller does not have the appropriate {@code AdminPermission(this,EXECUTE)},
     * and the Java Runtime Environment supports permissions.
     * @see #start
     */
    @Override
    public void start(int ignored) throws BundleException {
        start();
    }

    /**
     * See {@link Framework#stop}.
     *
     * The method returns immediately to the caller
     *
     * @throws BundleException if stopping this Framework could not be initiated.
     * @throws SecurityException if the caller does not have the appropriate {@code AdminPermission(this,EXECUTE)},
     * and the Java Runtime Environment supports permissions.
     */
    @Override
    public void stop() throws BundleException {
        shutdownLatchAtomic.set(new CountDownLatch(WAIT_LATCH));

        // This Framework's state is set to Bundle.STOPPING.
        stateAtomic.set(Bundle.STOPPING);
        bundleContextAtomic.get().notifyToListeners(new BundleEvent(BundleEvent.STOPPING, this));
        // All installed bundles must be stopped without changing each bundle's persistent autostart setting.
        // The start level of this Framework is moved to start level zero (0).
        startLevelAtomic.set(INIT_START_LEVEL);
        // Unregister all services registered by this Framework.

        // Event handling is disabled.

        // This Framework's state is set to Bundle.RESOLVED.
        stateAtomic.set(Bundle.RESOLVED);
        // All resources held by this Framework are released.
        bundleContextAtomic.set(null);
        shutdownLatchAtomic.get().countDown();
    }

    /**
     * See {@link Framework#stop}.
     *
     * @param ignored  There are no stop options for the {@link Framework}.
     * @throws BundleException if stopping this Framework could not be initiated.
     * @throws SecurityException if the caller does not have the appropriate {@code AdminPermission(this,EXECUTE)},
     * and the Java Runtime Environment supports permissions.
     */
    @Override
    public void stop(int ignored) throws BundleException {
        stop();
    }

    /**
     * See {@link Framework#waitForStop}.
     *
     * @param timeout Maximum number of milliseconds to wait until this {@link Framework} has completely stopped.
     *                A value of zero will wait indefinitely.
     * @return A {@link FrameworkEvent} indicating the reason this method returned.
     * The following FrameworkEvent types may be returned by this method.
     * * {@link FrameworkEvent#STOPPED} This {@link Framework} has been stopped.
     * * {@link FrameworkEvent#WAIT_TIMEDOUT} This method timed out before this {@link Framework} has stopped.
     * @throws InterruptedException if another thread interrupted the current thread before or while the current thread
     * was waiting for this Framework to completely stop.
     * @throws IllegalArgumentException if the value of {@code timeout} is negative.
     */
    @Override
    public FrameworkEvent waitForStop(long timeout) throws InterruptedException {
        if (shutdownLatchAtomic.get().await(timeout, MILLISECONDS)) {
            return new FrameworkEvent(FrameworkEvent.STOPPED, this, null);
        } else {
            return new  FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, this, new TimeoutException("$timeout ms time-out"));
        }
    }

// : Bundle

    /**
     * See {@link Bundle#getBundleContext}.
     *
     * @return the {@link BundleContext} for this bundle or `null` if this bundle has no valid {@link BundleContext}.
     * If {@link #init} or {@link #start} wasn't called before, it returns {@code null}.
     * @throws SecurityException if the caller does not have the appropriate {@code AdminPermission(this,CONTEXT)},
     * and the Java Runtime Environment supports permissions.
     */
    @Override
    public BundleContext getBundleContext() {
        return bundleContextAtomic.get();
    }

    /**
     * See {@link Bundle#getState}.
     *
     * @return An element of {@link Bundle#UNINSTALLED}, {@link Bundle#INSTALLED}, {@link Bundle#RESOLVED},
     * {@link Bundle#STARTING}, {@link Bundle#STOPPING}, {@link Bundle#ACTIVE}.
     */
    @Override
    public int getState() {
        return stateAtomic.get();
    }

    /**
     * See {@link Bundle#getVersion}.
     *
     * @return The {@link Version} of this bundle.
     */
    @Override
    public Version getVersion() {
        return versionAtomic.get();
    }

// : Comparable

    /**
     * See {@link Comparable#compareTo}
     *
     * @param other bundle to compare.
     * @return a negative integer, zero, or a positive integer as this object is
     * less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(Bundle other) {
        return Long.signum(getBundleId() - other.getBundleId());
    }

    @Override
    public void update() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void update(InputStream input) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void uninstall() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return new Hashtable<>();
    }

    @Override
    public Dictionary<String, String> getHeaders(String ignored) {
        return new Hashtable<>();
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public ServiceReference<?>[] getServicesInUse() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public boolean hasPermission(Object permission) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public URL getResource(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Class<?> loadClass(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Enumeration<URL> getResources(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public URL getEntry(String path) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long getLastModified() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public <A> A adapt(Class<A> type) {
        return mock(type);
    }

    @Override
    public File getDataFile(String filename) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
