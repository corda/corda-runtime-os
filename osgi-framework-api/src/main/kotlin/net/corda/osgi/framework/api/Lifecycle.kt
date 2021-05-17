package net.corda.osgi.framework.api

interface Lifecycle {

    companion object {

        /**
         * JAR metadata header expressing the class implementing the [Lifecycle],
         * used by [OSGiFrameworkWrap.startLifecycle] to call the entrypoint of application bundles.
         */
        const val METADATA_HEADER = "Lifecycle-Class"
    }

    fun startup(args: Array<String>)
    fun shutdown()

}