package net.corda.virtualnode.write.db

import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerWriter
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

/**
 * Cpi Info writer (PUBLISHER) interface.
 */
interface VirtualNodeInfoWriteService : ReconcilerWriter<HoldingIdentity, VirtualNodeInfo>, Lifecycle {
    // We define these methods via ReconcilerWriter
    override fun put(recordKey: HoldingIdentity, recordValue: VirtualNodeInfo)

    override fun remove(recordKey: HoldingIdentity)
}
