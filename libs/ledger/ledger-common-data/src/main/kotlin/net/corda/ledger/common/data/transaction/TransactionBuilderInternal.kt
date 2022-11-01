package net.corda.ledger.common.data.transaction

import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.serialization.SerializationService

interface TransactionBuilderInternal {
    fun calculateComponentGroups(
        serializationService: SerializationService,
        metadataBytes: ByteArray,
        currentSandboxGroup: SandboxGroup
    ): List<List<ByteArray>>
}