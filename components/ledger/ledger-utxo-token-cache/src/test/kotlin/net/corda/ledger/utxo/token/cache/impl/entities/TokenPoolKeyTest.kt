package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TokenPoolKeyTest {

    @Test
    fun `toAvro creates an avro representation of they key`() {
        val expectedAvro = TokenPoolCacheKey.newBuilder()
            .setTokenType("tt")
            .setSymbol("sym")
            .setNotaryX500Name("not")
            .setIssuerHash("ih")
            .setShortHolderId("shid")
            .build()

        val target = TokenPoolKey("shid", "tt", "ih", "not", "sym")

        assertThat(target.toAvro()).isEqualTo(expectedAvro)
    }

    @Test
    fun `toString creates a string representation of they key`() {
        val target = TokenPoolKey("shid", "tt", "ih", "not", "sym")

        assertThat(target.toString()).isEqualTo("shid-tt-ih-not-sym")
    }
}
