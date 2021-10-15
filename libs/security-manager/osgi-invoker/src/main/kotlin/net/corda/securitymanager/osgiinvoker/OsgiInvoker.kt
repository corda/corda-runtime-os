package net.corda.securitymanager.osgiinvoker

/** Used to test whether the bundle has permission to invoke various OSGi methods. */
interface OsgiInvoker {
    fun getBundleContext()

    fun startBundle()

    fun installBundle()

    fun addListener()

    fun loadClass()

    fun getLocation()

    fun refreshBundles()

    fun adaptBundle()

    fun getService()
}