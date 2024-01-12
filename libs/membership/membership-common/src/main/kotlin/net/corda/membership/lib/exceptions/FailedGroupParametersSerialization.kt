package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class FailedGroupParametersSerialization(
    message: String = "Failed to serialize the GroupParameters to KeyValuePairList",
    cause: Throwable? = null
) :
    CordaRuntimeException(message, cause)
