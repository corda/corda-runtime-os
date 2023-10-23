package net.corda.libs.statemanager.impl.repository.impl

import net.corda.libs.statemanager.impl.model.v1.StateEntity
import net.corda.v5.base.exceptions.CordaRuntimeException

class StateManagerBatchingException(val failedStates: List<StateEntity>, message: String, e: Exception? = null) :
    CordaRuntimeException(message, e)