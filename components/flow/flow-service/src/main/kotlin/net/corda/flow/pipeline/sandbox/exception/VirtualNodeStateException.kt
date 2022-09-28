package net.corda.flow.pipeline.sandbox.exception

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.VirtualNodeState

class VirtualNodeStateException(virtualNodeShortHash: String, virtualNodeState: VirtualNodeState) :
    CordaRuntimeException("Virtual node $virtualNodeShortHash is in ${virtualNodeState.name} state.")