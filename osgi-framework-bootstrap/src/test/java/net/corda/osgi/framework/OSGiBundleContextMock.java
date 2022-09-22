package net.corda.osgi.framework;

import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Dictionary;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class OSGiBundleContextMock implements BundleContext {
    private final BundleContext mockBundleContext;
    private final OSGiFrameworkMock framework;
    private final Bundle bundle;

    /**
     * Set of {@link BundleListener} to notify when bundle
     */
    private final Set<BundleListener> bundleListenerSet;

    OSGiBundleContextMock(OSGiFrameworkMock framework, Bundle bundle) {
        this.mockBundleContext = MockOsgi.newBundleContext();
        this.framework = framework;
        this.bundle = bundle;
        bundleListenerSet = ConcurrentHashMap.newKeySet();
    }

    /**
     * Notify to {@link #bundleListenerSet} the bundleEvent.
     * OSGi Core r7 4.7.3 "Synchronization Pitfalls" requires call-backs don't run in synchronized sections.
     *
     * Thread safe.
     *
     * @param bundleEvent to notify.
     */
    void notifyToListeners(BundleEvent bundleEvent) {
        // Get a snapshot of listeners.
        final Set<BundleListener> snapshot = new LinkedHashSet<>(bundleListenerSet);
        for (BundleListener bundleListener : snapshot) {
            bundleListener.bundleChanged(bundleEvent);
        }
    }

    // : BundleContext

    /**
     * See {@link BundleContext#getBundle}.
     *
     * @return The {@link Bundle} object associated with this {@link BundleContext}.
     */
    @Override
    public Bundle getBundle() {
        return bundle;
    }

    /**
     * See {@link BundleContext#getBundle}.
     */
    @Override
    public Bundle getBundle(long id) {
        return framework.getBundle(id);
    }

    /**
     * See {@link BundleContext#getBundle}.
     */
    @Override
    public Bundle getBundle(String location) {
        return framework.getBundle(location);
    }

    /**
     * See {@link BundleContext#getBundles}.
     */
    @Override
    public Bundle[] getBundles() {
        return framework.getBundles();
    }

    @Override
    public String getProperty(String key) {
        return mockBundleContext.getProperty(key);
    }

    @Override
    public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
        mockBundleContext.addServiceListener(listener, filter);
    }

    @Override
    public void addServiceListener(ServiceListener listener) {
        mockBundleContext.addServiceListener(listener);
    }

    @Override
    public void removeServiceListener(ServiceListener listener) {
        mockBundleContext.removeServiceListener(listener);
    }

    @Override
    public void addBundleListener(BundleListener listener) {
        mockBundleContext.addBundleListener(listener);
    }

    @Override
    public void removeBundleListener(BundleListener listener) {
        mockBundleContext.removeBundleListener(listener);
    }

    @Override
    public void addFrameworkListener(FrameworkListener listener) {
        mockBundleContext.addFrameworkListener(listener);
    }

    @Override
    public void removeFrameworkListener(FrameworkListener listener) {
        mockBundleContext.removeFrameworkListener(listener);
    }

    @Override
    public ServiceRegistration<?> registerService(String[] clazzes, Object service, Dictionary<String, ?> properties) {
        return mockBundleContext.registerService(clazzes, service, properties);
    }

    @Override
    public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
        return mockBundleContext.registerService(clazz, service, properties);
    }

    @Override
    public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
        return mockBundleContext.registerService(clazz, service, properties);
    }

    @Override
    public <S> ServiceRegistration<S> registerService(Class<S> clazz, ServiceFactory<S> factory, Dictionary<String, ?> properties) {
        return mockBundleContext.registerService(clazz, factory, properties);
    }

    @Override
    public ServiceReference<?>[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        return mockBundleContext.getServiceReferences(clazz, filter);
    }

    @Override
    public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        return mockBundleContext.getAllServiceReferences(clazz, filter);
    }

    @Override
    public ServiceReference<?> getServiceReference(String clazz) {
        return mockBundleContext.getServiceReference(clazz);
    }

    @Override
    public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
        return mockBundleContext.getServiceReference(clazz);
    }

    @Override
    public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter) throws InvalidSyntaxException {
        return mockBundleContext.getServiceReferences(clazz, filter);
    }

    @Override
    public <S> S getService(ServiceReference<S> reference) {
        return mockBundleContext.getService(reference);
    }

    @Override
    public boolean ungetService(ServiceReference<?> reference) {
        return mockBundleContext.ungetService(reference);
    }

    @Override
    public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> reference) {
        return mockBundleContext.getServiceObjects(reference);
    }

    @Override
    public File getDataFile(String filename) {
        return mockBundleContext.getDataFile(filename);
    }

    @Override
    public Filter createFilter(String filter) throws InvalidSyntaxException {
        return mockBundleContext.createFilter(filter);
    }

    /**
     * See {@link BundleContext#installBundle}.
     */
    @Override
    public Bundle installBundle(String location) throws BundleException {
        return installBundle(location, null);
    }

    /**
     * See {@link BundleContext#installBundle}.
     *
     * The specified {@code location} identifier will be used as the identity of
     * the bundle. Every installed bundle is uniquely identified by its location
     * identifier which is typically in the form of a URL.
     *
     * @return The {@link Bundle} object of the installed bundle.
     * @throws BundleException If the installation failed.
     */
    @Override
    public Bundle installBundle(String location, InputStream input) throws BundleException {
        // If the specified `InputStream` is `null`, the Framework must
        // create the `InputStream` from which to read the bundle by
        // interpreting, in an implementation dependent manner, the specified `location`.

        // The following steps are required to install a bundle.
        // If a bundle containing the same location identifier is already
        // installed, the `Bundle` object for that bundle is returned.
        Bundle bundle = framework.getBundle(location);
        if (bundle == null) {
            bundle = framework.installBundle(location);

            // The bundle's associated resources are allocated. The associated
            // resources minimally consist of a unique identifier and a persistent
            // storage area if the platform has file system support. If this step fails,
            // a `BundleException` is thrown.
            // The bundle's state is set to `Bundle.INSTALLED`.
            notifyToListeners(new BundleEvent(BundleEvent.INSTALLED, bundle));
            // The `Bundle` object for the newly or previously installed bundle is returned.
        }
        return bundle;
    }
}
