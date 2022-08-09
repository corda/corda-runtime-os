package net.corda.flow.application.services

import net.corda.v5.application.services.ClaimCriteria
import java.time.Instant

data class ClaimCriteriaRequest (
    val criteria: ClaimCriteria,
    val awaitWhenClaimed: Boolean = false,
    val awaitExpiryTime: Instant? = null
)