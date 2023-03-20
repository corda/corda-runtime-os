package net.corda.flow.rest.flowstatus

import net.corda.rest.ws.WebSocketValidationException

class FlowStatusListenerValidationException(message: String) : WebSocketValidationException(message)