package net.corda.p2p.crypto.protocol.api

import net.corda.v5.base.exceptions.CordaRuntimeException

class InvalidHandshakeMessageException : CordaRuntimeException("The handshake message was invalid.")
