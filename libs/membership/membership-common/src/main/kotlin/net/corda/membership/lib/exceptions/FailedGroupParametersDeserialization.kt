package net.corda.membership.lib.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

object FailedGroupParametersDeserialization :
    CordaRuntimeException("Failed to deserialize the serialized GroupParameters")