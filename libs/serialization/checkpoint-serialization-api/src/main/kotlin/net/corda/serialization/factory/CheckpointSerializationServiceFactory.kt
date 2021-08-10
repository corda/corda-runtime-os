package net.corda.serialization.factory

import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializationService

interface CheckpointSerializationServiceFactory {
    /**
     * Create an instance of the [CheckpointSerializationService]
     */
    fun createCheckpointSerializationService(sandboxGroup: SandboxGroup): CheckpointSerializationService
}