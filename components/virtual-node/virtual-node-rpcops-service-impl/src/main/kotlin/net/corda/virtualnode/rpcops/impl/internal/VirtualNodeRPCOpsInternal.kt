package net.corda.virtualnode.rpcops.impl.internal

import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRPCOps
import net.corda.virtualnode.common.endpoints.LateInitRPCOps

interface VirtualNodeRPCOpsInternal : VirtualNodeRPCOps, LateInitRPCOps