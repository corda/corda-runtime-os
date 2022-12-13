package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PoolKeyTest {

    @Test
    fun `fromTokenPoolCacheKey maps all values`() {
        val poolKey = TokenPoolCacheKey.newBuilder()
            .setShortHolderId("hid")
            .setTokenType("tt")
            .setIssuerHash("ih")
            .setNotaryX500Name("x5")
            .setSymbol("s")
            .build()

        val expectedPoolKey = PoolKey("hid", "tt", "ih", "x5", "s")

        assertThat(PoolKey.fromTokenPoolCacheKey(poolKey)).isEqualTo(expectedPoolKey)
    }

    @Test
    fun `toTokenPoolCacheKey maps all values`() {
        val poolKey = PoolKey("hid", "tt", "ih", "x5", "s")

        val expectedPoolKey = TokenPoolCacheKey.newBuilder()
            .setShortHolderId("hid")
            .setTokenType("tt")
            .setIssuerHash("ih")
            .setNotaryX500Name("x5")
            .setSymbol("s")
            .build()

        assertThat(poolKey.toTokenPoolCacheKey()).isEqualTo(expectedPoolKey)
    }
}