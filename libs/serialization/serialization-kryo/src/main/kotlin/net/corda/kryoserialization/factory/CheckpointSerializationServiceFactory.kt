package net.corda.kryoserialization.factory

import net.corda.kryoserialization.CheckpointSerializationService
import net.corda.sandbox.SandboxGroup

interface CheckpointSerializationServiceFactory {
    /**
     * Create an instance of the [CheckpointSerializationService]
     */
    fun createCheckpointSerializationService(sandboxGroup: SandboxGroup): CheckpointSerializationService
}