package net.corda.ledger.utxo.token.selection.impl.factories

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimFactoryImpl
import net.corda.ledger.utxo.impl.token.selection.impl.ALICE_X500
import net.corda.ledger.utxo.impl.token.selection.impl.ALICE_X500_NAME
import net.corda.ledger.utxo.impl.token.selection.impl.BOB_X500
import net.corda.ledger.utxo.impl.token.selection.impl.toAmount
import net.corda.ledger.utxo.impl.token.selection.impl.toSecureHash
import net.corda.ledger.utxo.impl.token.selection.impl.toStateRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.math.BigDecimal

class TokenClaimFactoryImplTest {

    private val tokenStateRef = "s1".toStateRef()
    private val tokenIssuerHash = "issuer".toSecureHash()
    private val tokenOwnerHash = "owner".toSecureHash()
    private val tokenPoolCacheKey = TokenPoolCacheKey().apply {
        shortHolderId = BOB_X500
        tokenType = "tt"
        issuerHash = tokenIssuerHash.toString()
        symbol = "s"
        notaryX500Name = ALICE_X500
    }

    private val token = Token().apply {
        stateRef = tokenStateRef.toString()
        tag = "t"
        ownerHash = tokenOwnerHash.toString()
        amount = 10L.toAmount()
    }

    @Test
    fun `createTokenClaim returns instance of TokenClaim`() {
        assertThat(TokenClaimFactoryImpl(mock()).createTokenClaim("c1", tokenPoolCacheKey, listOf()))
            .isNotNull
    }

    @Test
    fun `createClaimedToken stateRef`() {
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.stateRef).isEqualTo(tokenStateRef)
    }

    @Test
    fun `createClaimedToken tokenType`() {
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.tokenType).isEqualTo("tt")
    }

    @Test
    fun `createClaimedToken issuerHash`() {
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.issuerHash).isEqualTo(tokenIssuerHash)
    }

    @Test
    fun `createClaimedToken notaryX500Name`() {
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.notaryX500Name).isEqualTo(ALICE_X500_NAME)
    }

    @Test
    fun `createClaimedToken symbol`() {
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.symbol).isEqualTo("s")
    }

    @Test
    fun `createClaimedToken tag`() {
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.tag).isEqualTo("t")
    }

    @Test
    fun `createClaimedToken ownerHash`() {
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.ownerHash).isEqualTo(tokenOwnerHash)
    }

    @Test
    fun `createClaimedToken null ownerHash`() {
        token.ownerHash = null
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.ownerHash).isNull()
    }

    @Test
    fun `createClaimedToken amount`() {
        token.ownerHash = null
        val result = TokenClaimFactoryImpl(mock()).createClaimedToken(tokenPoolCacheKey, token)

        assertThat(result.amount).isEqualTo(BigDecimal(10))
    }
}
