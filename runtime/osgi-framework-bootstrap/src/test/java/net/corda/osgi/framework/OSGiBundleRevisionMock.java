package net.corda.osgi.framework;

import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import java.util.List;

final class OSGiBundleRevisionMock implements BundleRevision {
    private final Bundle bundle;

    OSGiBundleRevisionMock(Bundle bundle) {
        this.bundle = bundle;
    }

    @Override
    public String getSymbolicName() {
        return bundle.getSymbolicName();
    }

    @Override
    public Version getVersion() {
        return bundle.getVersion();
    }

    @Override
    public List<BundleCapability> getDeclaredCapabilities(String s) {
        throw new UnsupportedOperationException("getDeclaredCapabilities - not implemented");
    }

    @Override
    public List<BundleRequirement> getDeclaredRequirements(String s) {
        throw new UnsupportedOperationException("getDeclaredRequirements - not implemented");
    }

    @Override
    public int getTypes() {
        return 0;
    }

    @Override
    public BundleWiring getWiring() {
        throw new UnsupportedOperationException("getWiring - not implemented");
    }

    @Override
    public List<Capability> getCapabilities(String s) {
        throw new UnsupportedOperationException("getCapabilities - not implemented");
    }

    @Override
    public List<Requirement> getRequirements(String s) {
        throw new UnsupportedOperationException("getRequirements - not implemented");
    }

    @Override
    public Bundle getBundle() {
        return bundle;
    }
}
