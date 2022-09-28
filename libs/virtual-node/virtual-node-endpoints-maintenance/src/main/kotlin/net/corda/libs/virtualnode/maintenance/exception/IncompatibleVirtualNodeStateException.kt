package net.corda.libs.virtualnode.maintenance.exception

import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException

class IncompatibleVirtualNodeStateException(currentState: String, expectedState: String, operation: String) :
    HttpApiException(
        ResponseCode.METHOD_NOT_ALLOWED,
        "Virtual node was in $currentState state, but must be in $expectedState to perform $operation."
    )