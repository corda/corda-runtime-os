package net.corda.osgi.framework;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class OSGiBundleMock implements Bundle {
    private final long id;
    private final String location;

    //: Bundle

    private final AtomicInteger stateAtomic;

    OSGiBundleMock(long id, String location) {
        this.id = id;
        this.location = location;
        stateAtomic = new AtomicInteger(Bundle.INSTALLED);
    }

    /**
     * See {@link Bundle#getState}.
     *
     * @return An element of {@link Bundle#UNINSTALLED}, {@link Bundle#INSTALLED}, {@link Bundle#RESOLVED},
     *                       {@link Bundle#STARTING}, {@link Bundle#STOPPING}, {@link Bundle#ACTIVE}.
     */
    @Override
    public int getState() {
        return stateAtomic.get();
    }

    //: Comparable

    /**
     * See {@link Comparable#compareTo}
     *
     * @param other bundle to compare.
     * @return a negative integer, zero, or a positive integer as this object is
     *         less than, equal to, or greater than the specified object.
     */
    @Override
    public int compareTo(Bundle other) {
        return Long.signum(id - other.getBundleId());
    }

    public void start(int ignored) {
        start();
    }

    @Override
    public void start() {
        stateAtomic.set(Bundle.ACTIVE);
    }

    @Override
    public void stop(int options) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void update(InputStream input) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void update() {
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
        return getHeaders();
    }

    @Override
    public long getBundleId() {
        return id;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean hasPermission(Object permission) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public URL getResource(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getSymbolicName() {
        return "mock-symbolic-name";
    }

    @Override
    public Class<?> loadClass(String name) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

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
    public BundleContext getBundleContext() {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Version getVersion() {
        return new Version(0, 0, 0, "mock");
    }

    @Override
    public <A> A adapt(Class<A> type) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public File getDataFile(String filename) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
