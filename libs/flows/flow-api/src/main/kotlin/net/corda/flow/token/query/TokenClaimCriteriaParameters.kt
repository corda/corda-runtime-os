package net.corda.flow.token.query

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import java.math.BigDecimal

@CordaSerializable
data class TokenClaimCriteriaParameters(val deduplicationId: String, val tokenClaimCriteria: TokenClaimCriteriaRequest)


data class TokenClaimCriteriaRequest(
    val tokenType: String,
    val issuerHash: SecureHash,
    val notaryX500Name: MemberX500Name,
    val symbol: String,
    val targetAmount: BigDecimal,
    val tagRegex: String?,
    val ownerHash: SecureHash?,
)