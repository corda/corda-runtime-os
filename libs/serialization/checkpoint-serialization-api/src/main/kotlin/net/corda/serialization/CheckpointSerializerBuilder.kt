package net.corda.serialization

import net.corda.sandbox.SandboxGroup

interface CheckpointSerializerBuilder {
    fun newCheckpointSerializer(sandboxGroup: SandboxGroup): CheckpointSerializerBuilder
    fun addSerializer(clazz: Class<*>, serializer: CheckpointInternalCustomSerializer<*>): CheckpointSerializerBuilder
    fun addSerializerForClasses(classes: List<Class<*>>, serializer: CheckpointInternalCustomSerializer<*>): CheckpointSerializerBuilder
    fun addNoReferencesWithin(clazz: Class<*>): CheckpointSerializerBuilder
    fun build(): CheckpointSerializer
}
