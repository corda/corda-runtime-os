package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

object FailedGroupParametersSerialization :
    CordaRuntimeException("Failed to serialize the GroupParameters to KeyValuePairList")