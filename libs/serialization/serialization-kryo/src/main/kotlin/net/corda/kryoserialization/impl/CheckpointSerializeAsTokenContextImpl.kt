package net.corda.kryoserialization.impl

import net.corda.serialization.CheckpointSerializationContext
import net.corda.v5.serialization.SerializeAsTokenContext

val serializationContextKey = SerializeAsTokenContext::class.java

fun CheckpointSerializationContext.withTokenContext(serializationContext: SerializeAsTokenContext):
        CheckpointSerializationContext = this.withProperty(serializationContextKey, serializationContext)

