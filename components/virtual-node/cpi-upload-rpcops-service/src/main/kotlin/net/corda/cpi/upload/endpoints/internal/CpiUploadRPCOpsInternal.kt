package net.corda.cpi.upload.endpoints.internal

import net.corda.libs.virtualnode.endpoints.v1.CpiUploadRPCOps
import net.corda.virtualnode.common.endpoints.LateInitRPCOps

interface CpiUploadRPCOpsInternal : CpiUploadRPCOps, LateInitRPCOps