package net.corda.ledger.utxo.token.cache.impl.converters

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimQuery
import net.corda.data.ledger.utxo.token.selection.data.TokenClaimRelease
import net.corda.data.ledger.utxo.token.selection.data.TokenLedgerChange
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.impl.POOL_CACHE_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.ByteBuffer

class EntityConverterImplTest {

    @Test
    fun `toCachedToken creates and instance of CachedToken`() {
        assertThat(EntityConverterImpl().toCachedToken(Token())).isInstanceOf(CachedToken::class.java)
    }

    @Test
    fun `toClaimQuery creates and instance of ClaimQuery`() {
        val bigDecimal = BigDecimal(123.456)
        val targetAmount = TokenAmount().apply {
            unscaledValue = ByteBuffer.wrap(bigDecimal.unscaledValue().toByteArray())
            scale = bigDecimal.scale()
        }
        val tokenClaimQuery = TokenClaimQuery().apply {
            requestContext = ExternalEventContext().apply {
                requestId = "r1"
                flowId = "f1"
            }
            this.poolKey = POOL_CACHE_KEY
            this.targetAmount = targetAmount
            this.ownerHash = "oh"
            this.tagRegex = "tr"
        }

        val result = EntityConverterImpl()
            .toClaimQuery(POOL_CACHE_KEY, tokenClaimQuery)

        assertThat(result.externalEventRequestId).isEqualTo("r1")
        assertThat(result.flowId).isEqualTo("f1")
        assertThat(result.targetAmount).isEqualTo(bigDecimal)
        assertThat(result.poolKey).isEqualTo(POOL_CACHE_KEY)
    }

    @Test
    fun `toClaimRelease creates and instance of ClaimRelease`() {
        val tokenClaimRelease = TokenClaimRelease().apply {
            requestContext = ExternalEventContext().apply {
                requestId = "r1"
                flowId = "f1"
            }
            claimId = "c1"
            usedTokenStateRefs = listOf("s1", "s2")
        }

        val result = EntityConverterImpl()
            .toClaimRelease(POOL_CACHE_KEY, tokenClaimRelease)

        assertThat(result.claimId).isEqualTo("c1")
        assertThat(result.flowId).isEqualTo("f1")
        assertThat(result.usedTokens).containsOnly("s1", "s2")
        assertThat(result.poolKey).isEqualTo(POOL_CACHE_KEY)
    }

    @Test
    fun `toTokenCache creates and instance of TokenCache`() {
        assertThat(EntityConverterImpl().toTokenCache(TokenPoolCacheState())).isInstanceOf(TokenCache::class.java)
    }

    @Test
    fun `toLedgerChange creates an instance of LedgerChange`(){
        val token1 = Token().apply { stateRef = "s1" }
        val token2 = Token().apply { stateRef = "s2" }
        val token3 = Token().apply { stateRef = "s3" }
        val token4 = Token().apply { stateRef = "s4" }

        val ledgerChange = TokenLedgerChange().apply {
            this.poolKey = POOL_CACHE_KEY
            this.producedTokens= listOf(token1,token2)
            this.consumedTokens= listOf(token3,token4)
        }

        val result = EntityConverterImpl()
            .toLedgerChange(POOL_CACHE_KEY, ledgerChange)

        assertThat(result.poolKey).isEqualTo(POOL_CACHE_KEY)
        assertThat(result.producedTokens.map { it.stateRef }).containsOnly("s1","s2")
        assertThat(result.consumedTokens.map { it.stateRef }).containsOnly("s3","s4")
    }

    @Test
    fun `amountToBigDecimal converts the token amount to a big decimal`() {
        val bigDecimal = BigDecimal(123.456)
        val tokenAmount = TokenAmount().apply {
            unscaledValue = ByteBuffer.wrap(bigDecimal.unscaledValue().toByteArray())
            scale = bigDecimal.scale()
        }

        assertThat(
            EntityConverterImpl().amountToBigDecimal(tokenAmount)
        ).isEqualTo(bigDecimal)
    }

    @Test
    fun `amountToBigDecimal can read amount multiple times`() {
        /**
         * This test was added to ensure the byte buffer position of the underlying avro object is reset after each
         * read.
         */
        val bigDecimal = BigDecimal(123.456)
        val tokenAmount = TokenAmount().apply {
            unscaledValue = ByteBuffer.wrap(bigDecimal.unscaledValue().toByteArray())
            scale = bigDecimal.scale()
        }

        assertThat(
            EntityConverterImpl().amountToBigDecimal(tokenAmount)
        ).isEqualTo(bigDecimal)

        assertThat(
            EntityConverterImpl().amountToBigDecimal(tokenAmount)
        ).isEqualTo(bigDecimal)
    }
}
