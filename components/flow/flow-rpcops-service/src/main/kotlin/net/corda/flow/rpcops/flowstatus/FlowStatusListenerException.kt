package net.corda.flow.rpcops.flowstatus

import net.corda.v5.base.exceptions.CordaRuntimeException

class FlowStatusListenerException(message: String, errors: List<String>) : CordaRuntimeException("$message\n$errors")