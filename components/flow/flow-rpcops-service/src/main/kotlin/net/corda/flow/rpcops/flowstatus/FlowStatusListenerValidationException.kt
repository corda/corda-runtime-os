package net.corda.flow.rpcops.flowstatus

import net.corda.httprpc.ws.WebSocketValidationException

class FlowStatusListenerValidationException(message: String) : WebSocketValidationException(message)