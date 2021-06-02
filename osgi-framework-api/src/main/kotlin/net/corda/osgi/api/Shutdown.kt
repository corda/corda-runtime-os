package net.corda.osgi.api

import org.osgi.framework.Bundle

/**
 * Get this service to ask to the `osgi-framework-bootstrap` to stop the OSGi framework running the current
 * application.
 *
 * [Application.shutdown] method will be called after [shutdown] is called and before the OSGi framework stops.
 *
 * **EXAMPLE**
 *
 * ``` kotlin
 * class App: Application {
 *
 *   override fun startup(args: Array<String>) {
 *      println("START-UP")
 *      Thread.sleep(1000)
 *      Thread {
 *          shutdownOSGiFramework()
 *      }.start()
 *  }
 *
 *  override fun shutdown() {
 *      println("SHUTDOWN")
 *  }
 *
 *  private fun shutdownOSGiFramework() {
 *      val bundleContext: BundleContext? = FrameworkUtil.getBundle(GoodbyeWorld::class.java).bundleContext
 *      if (bundleContext != null) {
 *          val shutdownServiceReference: ServiceReference<Shutdown>? =
 *          bundleContext.getServiceReference(Shutdown::class.java)
 *          if (shutdownServiceReference != null) {
 *              bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
 *          }
 *      }
 *  }
 *
 * }
 * ```
 */
interface Shutdown {

    /**
     * Call this method to ask to `osgi-framework-bootstrap` to stop the application implementing this interface
     * and the OSGi framework.
     *
     * @param bundle where is this application asking to shutdown.
     */
    fun shutdown(bundle: Bundle)

}