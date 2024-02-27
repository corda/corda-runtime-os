package net.corda.p2p.crypto.protocol.api

import net.corda.v5.base.exceptions.CordaRuntimeException

class IncorrectAPIUsageException(message: String) : CordaRuntimeException(message)
