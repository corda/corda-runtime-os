package net.corda.serialization.checkpoint.factory

import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointSerializerBuilder

interface CheckpointSerializerBuilderFactory {
    /**
     * Create an instance of the [CheckpointSerializerBuilder]
     */
    fun createCheckpointSerializerBuilder(sandboxGroup: SandboxGroup): CheckpointSerializerBuilder
}
