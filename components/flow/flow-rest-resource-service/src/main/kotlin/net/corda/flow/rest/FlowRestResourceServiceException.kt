package net.corda.flow.rest

import net.corda.v5.base.exceptions.CordaRuntimeException

/** Exceptions related to the [FlowRestResourceService]. */
class FlowRestResourceServiceException(message: String, e: Exception? = null) : CordaRuntimeException(message, e)