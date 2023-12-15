package net.corda.ledger.utxo.impl.token.selection.entities

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class TokenClaimsCheckpointState(
    @JsonProperty("claims")
    val claims: MutableList<TokenClaimCheckpointState>
)
