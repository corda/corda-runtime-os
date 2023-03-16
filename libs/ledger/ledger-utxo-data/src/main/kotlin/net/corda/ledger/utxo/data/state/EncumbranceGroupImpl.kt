package net.corda.ledger.utxo.data.state

import net.corda.v5.ledger.utxo.EncumbranceGroup

data class EncumbranceGroupImpl(private val size: Int, private val tag: String) : EncumbranceGroup {

    override fun getTag(): String {
        return tag
    }

    override fun getSize(): Int {
        return size
    }
}
