package net.cordapp.testing.chat

import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.application.persistence.findAll
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.annotations.Suspendable
import net.cordapp.testing.utxo.Block
import net.cordapp.testing.utxo.db.Ledger

@Suspendable
fun storeBlock(block: Block, persistenceService: PersistenceService, serializationService: SerializationService) {
    // @@@ Is it worth checking if we already have it here?
    persistenceService.persist(
        Ledger(
            txid = block.hashCode().toLong(),
            block = serializationService.serialize(block).bytes
        )
    )
}

@Suspendable
fun allBlocks(persistenceService: PersistenceService, serializationService: SerializationService): Set<Block> {
    val allLedger = persistenceService.findAll<Ledger>().execute()
    val allBlocks = mutableSetOf<Block>()
    allLedger.forEach { ledgerEntry -> allBlocks.add(serializationService.deserialize(ledgerEntry.block)) }
    return allBlocks
}
