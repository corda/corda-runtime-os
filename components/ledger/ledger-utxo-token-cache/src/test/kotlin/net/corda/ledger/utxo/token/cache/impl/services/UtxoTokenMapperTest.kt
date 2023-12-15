package net.corda.ledger.utxo.token.cache.impl.services

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.utxo.token.cache.services.UtxoTokenMapper
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.utxo.StateRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import javax.persistence.Tuple

class UtxoTokenMapperTest {
    private val transactionId = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "transaction_id".toByteArray())
    private val leafId = 0
    private val tokenAmount = BigDecimal(1)

    @Test
    fun `Map tuple fields to token`() {
        val mapper = UtxoTokenMapper()

        val tuple = getTuple("tag", "owner_hash")

        val cachedToken = mapper.map(listOf(tuple)).single()

        assertThat(cachedToken.stateRef).isEqualTo(StateRef(transactionId, leafId).toString())
        assertThat(cachedToken.tag).isEqualTo("tag")
        assertThat(cachedToken.ownerHash).isEqualTo("owner_hash")
        assertThat(cachedToken.amount).isEqualTo(tokenAmount)
    }

    @Test
    fun `Map tuple fields to token - with null tag`() {
        val mapper = UtxoTokenMapper()

        val tuple = getTuple(null, "owner_hash")

        val cachedToken = mapper.map(listOf(tuple)).single()

        assertThat(cachedToken.stateRef).isEqualTo(StateRef(transactionId, leafId).toString())
        assertThat(cachedToken.tag).isNull()
        assertThat(cachedToken.ownerHash).isEqualTo("owner_hash")
        assertThat(cachedToken.amount).isEqualTo(tokenAmount)
    }

    @Test
    fun `Map tuple fields to token - with null owner`() {
        val mapper = UtxoTokenMapper()

        val tuple = getTuple("tag", null)

        val cachedToken = mapper.map(listOf(tuple)).single()

        assertThat(cachedToken.stateRef).isEqualTo(StateRef(transactionId, leafId).toString())
        assertThat(cachedToken.tag).isEqualTo("tag")
        assertThat(cachedToken.ownerHash).isNull()
        assertThat(cachedToken.amount).isEqualTo(tokenAmount)
    }

    private fun getTuple(tag: String?, ownerHash: String?): Tuple {
        return mock<Tuple>().apply {
            whenever(get(0)).thenReturn(transactionId.toString())
            whenever(get(1)).thenReturn(leafId)
            whenever(get(2)).thenReturn(tag)
            whenever(get(3)).thenReturn(ownerHash)
            whenever(get(4)).thenReturn(tokenAmount)
        }
    }
}
