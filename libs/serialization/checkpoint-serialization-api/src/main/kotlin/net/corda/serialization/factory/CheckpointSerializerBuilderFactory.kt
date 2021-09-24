package net.corda.serialization.factory

import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializerBuilder

interface CheckpointSerializerBuilderFactory {
    /**
     * Create an instance of the [CheckpointSerializerBuilder]
     */
    fun createCheckpointSerializerBuilder(sandboxGroup: SandboxGroup): CheckpointSerializerBuilder
}
