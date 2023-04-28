package net.corda.flow.fiber.cache

import net.corda.virtualnode.HoldingIdentity

/**
 * Flow fiber cache is keyed by the holding identity and flow ID.
 */
data class FlowFiberCacheKey(
    val holdingIdentity: HoldingIdentity,
    val flowId: String
)