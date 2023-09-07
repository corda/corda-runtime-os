package net.corda.ledger.utxo.impl.token.selection.entities

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
data class TokenClaimCheckpointState (
    @JsonProperty("claimId")
    val claimId: String,
    @JsonProperty("poolKey")
    val poolKey: PoolKey
)