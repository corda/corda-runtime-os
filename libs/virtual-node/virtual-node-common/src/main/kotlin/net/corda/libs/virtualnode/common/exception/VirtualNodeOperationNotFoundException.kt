package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class VirtualNodeOperationNotFoundException(requestId: String) :
    CordaRuntimeException("Could not find a virtual node operation with requestId of $requestId")