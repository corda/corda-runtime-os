package net.corda.ledger.utxo.impl.token.selection.entities

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TokenClaimsCheckpointStateTest {
    @Test
    fun `serialize and deserialize`() {
        val poolKey = PoolKey("a","b","c","d","e")
        val checkpointState = TokenClaimCheckpointState("id",poolKey )
        val claimsState = TokenClaimsCheckpointState(claims = mutableListOf(checkpointState))

        val objectMapper = ObjectMapper()

        val claimStateJson = objectMapper.writeValueAsString(claimsState)
        val result = objectMapper.readValue(claimStateJson, TokenClaimsCheckpointState::class.java)

        assertThat(result).isEqualTo(claimsState)
    }
}