package net.corda.rest.ws

import net.corda.v5.base.exceptions.CordaRuntimeException

class WebSocketProtocolViolationException(message: String) : CordaRuntimeException(message)