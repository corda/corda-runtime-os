package net.corda.flow.rpcops.flowstatus

import net.corda.rest.ws.WebSocketValidationException

class FlowStatusListenerValidationException(message: String) : WebSocketValidationException(message)