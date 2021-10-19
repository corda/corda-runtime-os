package net.corda.securitymanager.osgiinvoker

// TODO - Rename - no longer just used to invoke OSGi permissions.
// TODO - Update description.
/** Used to test whether the bundle has permission to invoke various OSGi methods. */
interface OsgiInvoker {
    fun performActionRequiringRuntimePermission()

    fun performActionRequiringServiceGetPermission()

    fun performActionRequiringServiceRegisterPermission()
}