package net.corda.flow.token.query

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria

@CordaSerializable
data class TokenClaimCriteriaParameters(val deduplicationId: String, val tokenClaimCriteria: TokenClaimCriteria)