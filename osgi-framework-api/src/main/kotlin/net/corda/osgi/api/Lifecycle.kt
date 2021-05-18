package net.corda.osgi.api

import org.osgi.framework.Bundle

/**
 * The class implementing this interface tags the OSGi bundle as an *application* bundle.
 *
 * The `osgi-framework-bootstrap` module calls [startup] as entry point of the application.
 */
interface Lifecycle {

    companion object {

        /**
         * JAR metadata header expressing the class implementing the [Lifecycle],
         * used by [OSGiFrameworkWrap.startLifecycle] to call the entrypoint of application bundles.
         */
        const val METADATA_HEADER = "Lifecycle-Class"
    }

    /**
     * The `osgi-framework-bootstrap` module calls this method as entry point of the application.
     *
     * @param args passed from the OS starting the bootable JAR.
     * @param bundle where is this application and the class implementing this interface.
     */
    fun startup(args: Array<String>, bundle: Bundle)

    /**
     * The `osgi-framework-bootstrap` module calls this method before to stop the OSGi framework when asked to
     * quit the application.
     */
    fun shutdown(bundle: Bundle)

}