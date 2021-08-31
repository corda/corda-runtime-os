package net.corda.securitymanager.localpermissions

/** Used to test whether local permissions are correctly applied. */
interface LocalPermissions {
    fun getBundleContext()

    fun startBundle()

    fun installBundle()

    fun addListener()

    fun loadClass()

    fun getLocation()

    fun refreshBundles()

    fun adaptBundle()

    fun getService()

    fun readFile()
}