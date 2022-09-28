package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * HoldingIdentity not found.
 */
class MgmGroupMismatchException(currentMgmGroupId: String, incorrectMgmGroupId: String) :
    CordaRuntimeException("Expected MGM GroupId $currentMgmGroupId but was $incorrectMgmGroupId in CPI.")