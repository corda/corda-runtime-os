package net.corda.flow.application.services

import net.corda.v5.application.services.ClaimedToken

@Suppress("LongParameterList", "Unused")
class ClaimedTokenImpl(
    override val stateRef: String,
    override val tokenType: String,
    override val issuerHash: String,
    override val notaryHash: String,
    override val symbol: String,
    override val tag: String?,
    override val ownerHash: String?,
    override val amount: Long
) : ClaimedToken