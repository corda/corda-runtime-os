package net.corda.simulator.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class SessionAlreadyClosedException : CordaRuntimeException("Session already closed")