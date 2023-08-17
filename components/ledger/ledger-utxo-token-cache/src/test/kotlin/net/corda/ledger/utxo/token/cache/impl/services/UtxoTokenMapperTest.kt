package net.corda.ledger.utxo.token.cache.impl.services

import java.math.BigDecimal
import org.junit.jupiter.api.Test
import javax.persistence.Tuple
import javax.persistence.TupleElement
import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.utxo.token.cache.services.UtxoTokenMapper
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.utxo.StateRef
import org.assertj.core.api.Assertions.assertThat

class UtxoTokenMapperTest {

    private class TupleImpl(private val container: List<Any>) : Tuple {

        override fun <X : Any?> get(tupleElement: TupleElement<X>?): X {
            TODO("Not yet implemented")
        }

        override fun <X : Any?> get(alias: String?, type: Class<X>?): X {
            TODO("Not yet implemented")
        }

        override fun get(alias: String?): Any {
            TODO("Not yet implemented")
        }

        override fun <X : Any?> get(i: Int, type: Class<X>?): X {
            TODO("Not yet implemented")
        }

        override fun get(i: Int): Any {
            return container[i]
        }

        override fun toArray(): Array<Any> {
            TODO("Not yet implemented")
        }

        override fun getElements(): MutableList<TupleElement<*>> {
            TODO("Not yet implemented")
        }
    }

    @Test
    fun `Total balance must match available balance when there is not claimed tokens`() {
        val transactionId = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "transaction_id".toByteArray())
        val leafId = 0
        val tag = "tag"
        val ownerHash =
            SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "owner_hash".toByteArray())
        val tokenAmount = BigDecimal(1)
        val mapper = UtxoTokenMapper()
        val tuples =
            listOf(TupleImpl(listOf(transactionId.toString(), leafId, tag, ownerHash.toString(), tokenAmount)))

        val cachedToken = mapper.map(tuples)

        assertThat(cachedToken.first().stateRef).isEqualTo(StateRef(transactionId, leafId).toString())
        assertThat(cachedToken.first().tag).isEqualTo(tag)
        assertThat(cachedToken.first().ownerHash).isEqualTo(ownerHash.toString())
        assertThat(cachedToken.first().amount).isEqualTo(tokenAmount)
    }
}
