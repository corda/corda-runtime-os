package net.corda.securitymanager.invoker

/** Used to test whether a bundle has the permissions to perform various actions. */
interface Invoker {
    fun performActionRequiringRuntimePermission()

    fun performActionRequiringServiceGetPermission()

    fun performActionRequiringServiceRegisterPermission()
}