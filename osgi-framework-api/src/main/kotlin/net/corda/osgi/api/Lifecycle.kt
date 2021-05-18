package net.corda.osgi.api

import org.osgi.framework.Bundle

interface Lifecycle {

    companion object {

        /**
         * JAR metadata header expressing the class implementing the [Lifecycle],
         * used by [OSGiFrameworkWrap.startLifecycle] to call the entrypoint of application bundles.
         */
        const val METADATA_HEADER = "Lifecycle-Class"
    }

    fun startup(args: Array<String>, bundle: Bundle)
    fun shutdown(bundle: Bundle)

}