package net.cordapp.testing.utxo.utils

import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.cordapp.testing.chat.allBlocks
import net.cordapp.testing.utxo.Block

@Suspendable
fun unspentBlocks(persistenceService: PersistenceService, serializationService: SerializationService): Set<Block> {
    val allBlocks = allBlocks(persistenceService, serializationService)
    val spentBlockHashes = mutableSetOf<Long>()
    allBlocks.forEach { block ->
        // Anything that is an input is spent
        spentBlockHashes.addAll(block.inputs)
    }
    val allBlocksMap = allBlocks.associateBy { it.hashCode().toLong() }
    // filter everything that isn't in the spent hashes set
    return allBlocksMap.filter { !spentBlockHashes.contains(it.key) }.values.toSet()
}

@Suspendable
fun backChainBlocksFor(
    unspentBlocksToSpend: Set<Block>,
    persistenceService: PersistenceService,
    serializationService: SerializationService
): Set<Block> {
    val allBlocks = allBlocks(persistenceService, serializationService)
    val allBlocksMap = allBlocks.associateBy { it.hashCode().toLong() }

    val output = mutableSetOf<Block>()
    unspentBlocksToSpend.forEach {
        output.addAll(getBackChain(it, allBlocksMap))
    }
    return output
}

private fun getBackChain(block: Block, allBlocksMap: Map<Long, Block>): Set<Block> {
    val output = mutableSetOf<Block>()
    block.inputs.forEach {
        val inputBlock = allBlocksMap[it]!!
        output.add(inputBlock)
        output.addAll(getBackChain(inputBlock, allBlocksMap))
    }
    return output
}

fun blocksForSpend(blocks: Set<Block>, spend: Long): Set<Block> {
    val output = mutableSetOf<Block>()
    var runningTotal = 0L
    blocks.forEach { block ->
        output.add(block)
        runningTotal += block.amount
        if (runningTotal >= spend) return@forEach
    }
    return output
}

fun remainderFromSpend(blocks: Set<Block>, spend: Long, owner: MemberX500Name): Block {
    val change = blocks.fold(0L) { amount, block -> amount + block.amount } - spend
    // All blocks identified are inputs to this transaction
    return Block(blocks.map { it.hashCode().toLong() }, owner.toString(), change)
}
