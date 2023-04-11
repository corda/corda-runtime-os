package net.corda.libs.external.messaging

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.virtualnode.HoldingIdentity

interface ExternalMessagingRouteConfigGenerator {
    fun generateConfig(holdingId: HoldingIdentity, cpiId: CpiIdentifier, cpks: Set<CpkMetadata>): String
}