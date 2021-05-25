package net.corda.p2p.crypto.protocol.api

import java.lang.RuntimeException

class IncorrectAPIUsageException(message: String): RuntimeException(message)