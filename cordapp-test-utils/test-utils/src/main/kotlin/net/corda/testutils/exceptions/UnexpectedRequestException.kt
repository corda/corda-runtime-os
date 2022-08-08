package net.corda.testutils.exceptions

import net.corda.v5.base.exceptions.CordaRuntimeException

class UnexpectedRequestException(request: String) : CordaRuntimeException(
    "No response has been set up for this request:${System.lineSeparator()}$request")