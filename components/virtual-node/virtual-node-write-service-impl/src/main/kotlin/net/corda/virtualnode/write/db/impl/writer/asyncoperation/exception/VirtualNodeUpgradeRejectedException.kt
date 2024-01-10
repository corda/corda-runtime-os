package net.corda.virtualnode.write.db.impl.writer.asyncoperation.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class VirtualNodeUpgradeRejectedException(val reason: String, requestId: String) : CordaRuntimeException("$reason (request $requestId)")