package net.corda.flow.token

data class ClaimedTokensRelease(
    val claimId:String,
    val tokenType: String,
    val issuerHash: String,
    val notaryHash: String,
    val symbol: String,
    val usedTokenRefs: List<String>,
    val releasedTokenRefs: List<String>
)