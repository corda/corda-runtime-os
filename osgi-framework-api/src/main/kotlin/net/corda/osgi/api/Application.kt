package net.corda.osgi.api

/**
 * The `osgi-framework-bootstrap` module calls [startup] of the class implementing this interface
 * as entry point of the application and [shutdown] before to stop the OSGi framework.
 *
 * **NOTE:**
 * *To distribute an application as a bootable JAR built with the `corda.common.app` plugin,
 * only one class must implement this interface because that class is the entry point of the application.*
 *
 * The class implementing this interface must define an OSGi component and register as an OSGi service.
 *
 * **EXAMPLE**
 */
interface Application : AutoCloseable {

    /**
     * Call [shutdown].
     *
     * @see [AutoCloseable.close]
     */
    override fun close() {
        shutdown()
    }

    /**
     * The `osgi-framework-bootstrap` module calls this method as entry point of the application.
     *
     * @param args passed from the OS starting the bootable JAR.
     */
    fun startup(args: Array<String>)

    /**
     * The `osgi-framework-bootstrap` module calls this method before to stop the OSGi framework.
     */
    fun shutdown()

}