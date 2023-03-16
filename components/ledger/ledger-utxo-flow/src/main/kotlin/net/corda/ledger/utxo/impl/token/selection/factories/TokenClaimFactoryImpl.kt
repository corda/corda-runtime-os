package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.crypto.core.parseSecureHash
import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.impl.token.selection.impl.PoolKey
import net.corda.ledger.utxo.impl.token.selection.impl.TokenClaimImpl
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.math.BigDecimal
import java.math.BigInteger

@Component(service = [TokenClaimFactory::class])
class TokenClaimFactoryImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor
) : TokenClaimFactory {

    override fun createTokenClaim(
        claimId: String,
        poolKey: TokenPoolCacheKey,
        claimedTokens: List<ClaimedToken>
    ): TokenClaim {
        return TokenClaimImpl(claimId, PoolKey.fromTokenPoolCacheKey(poolKey), claimedTokens, externalEventExecutor)
    }

    override fun createClaimedToken(poolKey: TokenPoolCacheKey, token: Token): ClaimedToken {
        return ClaimedTokenImpl(
            parseStateRef(token.stateRef),
            poolKey.tokenType,
            parseSecureHash(poolKey.issuerHash),
            MemberX500Name.parse(poolKey.notaryX500Name),
            poolKey.symbol,
            token.tag,
            token.ownerHash.toNullableSecureHash(),
            token.amount.toBigDecimal()
        )
    }

    private fun String?.toNullableSecureHash(): SecureHash? {
        return if (this != null && this.isNotEmpty()) {
            parseSecureHash(this)
        } else {
            null
        }
    }

    private fun TokenAmount.toBigDecimal(): BigDecimal {
        return BigDecimal(
            BigInteger(
                ByteArray(unscaledValue.remaining())
                    .apply { unscaledValue.get(this) }
            ),
            scale
        )
    }

    private data class ClaimedTokenImpl(
        private val stateRef: StateRef,
        private val tokenType: String,
        private val issuerHash: SecureHash,
        private val notaryX500Name: MemberX500Name,
        private val symbol: String,
        private var tag: String?,
        private var ownerHash: SecureHash?,
        private val amount: BigDecimal
    ) : ClaimedToken {

        override fun getStateRef(): StateRef {
            return stateRef
        }

        override fun getTokenType(): String {
            return tokenType
        }

        override fun getIssuerHash(): SecureHash {
            return issuerHash
        }

        override fun getNotaryX500Name(): MemberX500Name {
            return notaryX500Name
        }

        override fun getSymbol(): String {
            return symbol
        }

        override fun getTag(): String? {
            return tag
        }

        override fun getOwnerHash(): SecureHash? {
            return ownerHash
        }

        override fun getAmount(): BigDecimal {
            return amount
        }
    }
}

// Just a copy of public API `StateRef.parse` for internal purposes, i.e.
//  without using `DigestService` (which is public API) for secure hash parsing,
//  for to use it outside sandboxes.
private fun parseStateRef(value: String): StateRef {
    return try {
        val transactionHash = parseSecureHash(value.substringBeforeLast(":"))
        val index = value.substringAfterLast(":").toInt()

        StateRef(transactionHash, index)
    } catch (ex: NumberFormatException) {
        throw IllegalArgumentException(
            "Failed to parse a StateRef from the specified value. The index is malformed: $value.",
            ex
        )
    } catch (ex: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Failed to parse a StateRef from the specified value. The transaction id is malformed: $value.",
            ex
        )
    }
}