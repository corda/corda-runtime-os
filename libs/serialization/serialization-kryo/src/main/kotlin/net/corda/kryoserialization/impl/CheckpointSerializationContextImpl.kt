package net.corda.kryoserialization.impl

import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.sandbox.SandboxGroup
import net.corda.v5.serialization.SerializationEncoding

data class CheckpointSerializationContextImpl constructor(
    override val encoding: SerializationEncoding?,
    override val deserializationClassLoader: ClassLoader,
    override val properties: Map<Any, Any>,
    override val objectReferencesEnabled: Boolean,
    override val classInfoService: ClassInfoService,
    override val sandboxGroup: SandboxGroup
) : CheckpointSerializationContext
