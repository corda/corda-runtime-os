package net.corda.libs.permissions.manager

import net.corda.lifecycle.Lifecycle

/**
 * Definition of a service that provides basic authentication using the permission system.
 */
interface BasicAuthenticationService : Lifecycle {
    /**
     * Authenticate a user with the given loginName and password using the permission system.
     *
     * @param loginName the login name of the user to authenticate.
     * @param password the clear text password for the user.
     * @return whether the user is authenticated or not.
     */
    fun authenticateUser(loginName: String, password: CharArray): Boolean
}