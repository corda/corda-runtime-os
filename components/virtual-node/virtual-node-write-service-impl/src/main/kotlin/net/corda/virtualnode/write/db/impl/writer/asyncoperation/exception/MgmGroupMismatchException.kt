package net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class MgmGroupMismatchException(currentMgmGroupId: String, incorrectMgmGroupId: String) :
    CordaRuntimeException("Expected MGM GroupId $currentMgmGroupId but was $incorrectMgmGroupId in CPI.")