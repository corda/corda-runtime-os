package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import java.math.BigDecimal

class CachedTokenImpl(
    private val token: Token,
    private val entityConverter: EntityConverter
) : CachedToken {

    override val stateRef: String
        get() = token.stateRef

    override val amount: BigDecimal by lazy(LazyThreadSafetyMode.PUBLICATION) {
        entityConverter.amountToBigDecimal(token.amount)
    }

    override val tag: String
        get() = token.tag

    override val ownerHash: String
        get() = token.ownerHash

    override fun toAvro(): Token {
        return token
    }
}
