package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class FailedGroupParametersSerialization(cause: Throwable? = null) :
    CordaRuntimeException("Failed to serialize the GroupParameters to KeyValuePairList", cause)