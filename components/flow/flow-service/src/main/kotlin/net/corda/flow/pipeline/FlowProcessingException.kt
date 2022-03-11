package net.corda.flow.pipeline

import net.corda.v5.base.exceptions.CordaRuntimeException

class FlowProcessingException(message: String?) : CordaRuntimeException(message)