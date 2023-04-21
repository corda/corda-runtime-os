package net.corda.session.manager

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Generic exception indicating a runtime error has occurred within the session manager library.
 */
class SessionManagerException(message: String?, exception: Exception? = null) :
    CordaRuntimeException(message, exception)
