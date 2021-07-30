package net.corda.kryoserialization.factory

import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.kryoserialization.CheckpointSerializationService
import net.corda.kryoserialization.CheckpointSerializer

interface CheckpointSerializationServiceFactory {
    /**
     * Create an instance of the [CheckpointSerializationService]
     */
    fun createCheckpointSerializationService(context: CheckpointSerializationContext,
                                             serializer: CheckpointSerializer): CheckpointSerializationService
}