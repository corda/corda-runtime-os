package net.corda.ledger.utxo.token.cache.impl.entities

import net.corda.ledger.utxo.token.cache.entities.DbCachedToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.ByteBuffer

class DbCachedTokenTest {

    @Test
    fun `convert token to avro`() {
        val avroToken = DbCachedToken("sr", BigDecimal.ONE, "tag", "owner").toAvro()

        assertThat(avroToken.stateRef).isEqualTo("sr")
        assertThat(avroToken.ownerHash).isEqualTo("owner")
        assertThat(avroToken.amount.scale).isEqualTo(0)
        assertThat(avroToken.amount.unscaledValue).isEqualTo(
            ByteBuffer.wrap(BigDecimal.ONE.unscaledValue().toByteArray())
        )
    }
}
