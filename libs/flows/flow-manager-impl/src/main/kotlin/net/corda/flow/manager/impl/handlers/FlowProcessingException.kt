package net.corda.flow.manager.impl.handlers

import net.corda.v5.base.exceptions.CordaRuntimeException

class FlowProcessingException(message: String?) : CordaRuntimeException(message)