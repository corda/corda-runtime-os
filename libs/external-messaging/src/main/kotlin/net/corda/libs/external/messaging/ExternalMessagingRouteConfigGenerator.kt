package net.corda.libs.external.messaging

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo

interface ExternalMessagingRouteConfigGenerator {
    /**
     * The method generates the configuration for external messaging.
     * N.B.: Please keep in mind that any historical configuration is lost when this method is called.
     */
    fun generateNewConfig(holdingId: HoldingIdentity, cpiId: CpiIdentifier, cpks: Collection<CpkMetadata>): String?

    /**
     * The method generates the configuration for external messaging.
     * N.B.: Please keep in mind that the historical configuration is preserved when this method is called.
     */
    fun generateUpgradeConfig(virtualNode: VirtualNodeInfo, cpiId: CpiIdentifier, cpks: Collection<CpkMetadata>): String?
}
