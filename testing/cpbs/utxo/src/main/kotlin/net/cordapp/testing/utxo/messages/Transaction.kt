package net.cordapp.testing.utxo.messages

import net.corda.v5.base.annotations.CordaSerializable
import net.cordapp.testing.utxo.Block

/**
 * A simple container which holds a message and can be serialized by Corda.
 */
@CordaSerializable
data class Transaction(
    val inputBlocksToNewTransaction: Set<Block>,
    val backChainBlocks: Set<Block>,
    val amount: Long
)
