package net.corda.osgi.framework;

import org.osgi.framework.Bundle;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Description of bundles handled by {@link OSGiFrameworkWrap}.
 * <p/>
 * The class describes the {@link #bundle} and its property needed for a synchronization of
 * bundle state.
 * <p/>
 * The {@link #active} latch is decremented once: the first time the bundle results
 * activated. OSGi framework can notify bundle states more than once.
 */
final class OSGiBundleDescriptor {
    private final Bundle bundle;
    private final CountDownLatch active;

    OSGiBundleDescriptor(Bundle bundle, CountDownLatch active) {
        this.bundle = bundle;
        this.active = active;
    }

    OSGiBundleDescriptor(Bundle bundle) {
        this(bundle, new CountDownLatch(1));
    }

    Bundle getBundle() {
        return bundle;
    }

    CountDownLatch getActive() {
        return active;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundle, active);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof OSGiBundleDescriptor)) {
            return false;
        }
        final OSGiBundleDescriptor other = (OSGiBundleDescriptor) obj;
        return bundle.equals(other.bundle) && active.equals(other.active);
    }
}
