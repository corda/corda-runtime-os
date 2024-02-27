package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.data.TokenAmount
import java.math.BigDecimal
import java.nio.ByteBuffer

data class DbCachedToken(
    override val stateRef: String,
    override val amount: BigDecimal,
    override val tag: String?,
    override val ownerHash: String?
) : CachedToken {
    override fun toAvro(): Token {
        return createToken(stateRef, amount, tag, ownerHash)
    }

    private fun createToken(stateRef: String, amount: BigDecimal, tag: String?, ownerHash: String?): Token =
        Token().apply {
            this.stateRef = stateRef
            this.amount = TokenAmount(
                amount.scale(),
                ByteBuffer.wrap(amount.unscaledValue().toByteArray())
            )
            this.tag = tag
            this.ownerHash = ownerHash
        }
}
