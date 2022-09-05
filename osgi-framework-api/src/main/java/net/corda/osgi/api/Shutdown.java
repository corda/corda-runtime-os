package net.corda.osgi.api;

import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Bundle;

/**
 * Get this service to ask the {@code osgi-framework-bootstrap} to stop the OSGi framework running the current
 * application.
 * <p/>
 * {@link Application#shutdown} method will be called after {@link #shutdown} is called and before the OSGi framework stops.
 * <p/>
 * <b>EXAMPLE</b>
 * <br/>
 * <pre>{@code
 * class App: Application {
 *
 *     override fun startup(args: Array<String>) {
 *         println("START-UP")
 *         Thread.sleep(1000)
 *         Thread {
 *             shutdownOSGiFramework()
 *         }.start()
 *     }
 *
 *     override fun shutdown() {
 *         println("SHUTDOWN")
 *     }
 *
 *     private fun shutdownOSGiFramework() {
 *         val bundleContext: BundleContext? = FrameworkUtil.getBundle(GoodbyeWorld::class.java).bundleContext
 *         if (bundleContext != null) {
 *             val shutdownServiceReference: ServiceReference<Shutdown>? =
 *             bundleContext.getServiceReference(Shutdown::class.java)
 *             if (shutdownServiceReference != null) {
 *                 bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
 *             }
 *         }
 *     }
 * }}</pre>
 */
public interface Shutdown {

    /**
     * Call this method to ask to {@code osgi-framework-bootstrap} to stop the application implementing this interface
     * and the OSGi framework.
     *
     * @param bundle which is asking this application to shut down.
     */
    void shutdown(@NotNull Bundle bundle);

}
