package net.corda.v5.ledger.obsolete.contracts

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.toStringShort
import java.security.PublicKey

/** Command data/content plus pubkey pair: the signature is stored at the end of the serialized bytes */
@CordaSerializable
data class Command<out T : CommandData>(val value: T, val signers: List<PublicKey>) {

    init {
        require(signers.isNotEmpty()) { "The list of signers cannot be empty" }
    }

    constructor(data: T, key: PublicKey) : this(data, listOf(key))

    private fun commandDataToString() = value.toString().let { if (it.contains("@")) it.replace('$', '.').split("@")[0] else it }
    override fun toString() = "${commandDataToString()} with pubkeys ${signers.joinToString { it.toStringShort() }}"
}

/** Marker interface for classes that represent commands */
@CordaSerializable
interface CommandData

/** Commands that inherit from this are intended to have no data items: it's only their presence that matters. */
abstract class TypeOnlyCommandData : CommandData {
    override fun equals(other: Any?) = other?.javaClass == javaClass
    override fun hashCode() = javaClass.name.hashCode()
}

/** A common move command for contract states which can change owner. */
interface MoveCommand : CommandData {
    /**
     * Contract code the moved state(s) are for the attention of, for example to indicate that the states are moved in
     * order to settle an obligation contract's state object(s).
     */
    val contract: Class<out Contract>?
}