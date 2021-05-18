package net.corda.osgi.api

import org.osgi.framework.Bundle

/**
 * Get this service to ask to the `osgi-framework-bootstrap` to stop the OSGi framework running the current
 * application.
 *
 * [Lifecycle.shutdown] method will be called after [shutdown] is called and before the OSGi framework stops.
 *
 * **EXAMPLE**
 *
 * ``` kotlin
 * class App: LifeCycle {
 *
 *      private lateinit var bundle: Bundle
 *
 *      override fun startup(args: Array<String>, bundle: Bundle) {
 *          this.bundle = bundle
 *      }
 *
 *      override fun shutdown(bundle: Bundle) {
 *          // Called as consequence of askToQuit().
 *      }
 *
 *      fun askToQuit() {
 *          val shutdownReference = bundle.bundleContext.getServiceReference(ShutdownService::class.java.name)
 *          if (shutdownReference != null) {
 *              val shutdownService: ShutdownService? =
 *                  bundle.bundleContext.getService(shutdownReference) as ShutdownService
 *              shutdownService?.shutdown(bundle)
 *          }
 *      }
 *
 * }
 * ```
 */
interface ShutdownService {

    /**
     * Call this method to ask to `osgi-framework-bootstrap` to stop the application implementing this interface
     * and the OSGi framework.
     *
     * @param bundle where is this application.
     */
    fun shutdown(bundle: Bundle)

}