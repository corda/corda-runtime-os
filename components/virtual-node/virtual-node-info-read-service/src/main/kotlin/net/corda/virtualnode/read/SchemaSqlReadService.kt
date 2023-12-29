package net.corda.virtualnode.read

import net.corda.lifecycle.Lifecycle
import net.corda.reconciliation.ReconcilerReader
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

interface SchemaSqlReadService : ReconcilerReader<HoldingIdentity, VirtualNodeInfo>, Lifecycle {

    fun getSchemaSql(dbType: String, virtualNodeShortId: String? = null, cpiChecksum: String? = null): String
}
