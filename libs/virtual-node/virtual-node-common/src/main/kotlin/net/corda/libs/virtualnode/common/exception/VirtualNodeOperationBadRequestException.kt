package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class VirtualNodeOperationBadRequestException(requestId: String) :
    CordaRuntimeException("Bad request - virtual node operation with requestId of $requestId")
