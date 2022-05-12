package net.corda.utilities.security

import java.security.AccessController
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction

/**
 * Performs the specified PrivilegedExceptionAction with privileges enabled. The action is performed with all of the
 * permissions possessed by the caller's protection domain.
 */
fun <T> doWithPrivileges(action: PrivilegedExceptionAction<T>): T {
    return try {
        AccessController.doPrivileged(action)
    } catch (e: PrivilegedActionException) {
        throw e.exception
    }
}