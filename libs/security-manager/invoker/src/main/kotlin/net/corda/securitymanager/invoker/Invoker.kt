package net.corda.securitymanager.invoker

/** Used to test whether a bundle has the permissions to perform various actions. */
interface Invoker {
    /** Triggers a permission check against the `getenv.{variable name}` permission target. */
    fun performActionRequiringGetEnvRuntimePermission()

    /** Triggers a permission check against the `getProtectionDomain` permission target. */
    fun performActionRequiringGetProtectionDomainRuntimePermission()

    /** Triggers a permission check against the OSGi `ServicePermission.GET` action. */
    fun performActionRequiringServiceGetPermission()

    /** Triggers a permission check against the OSGi `ServicePermission.REGISTER` action. */
    fun performActionRequiringServiceRegisterPermission()
}