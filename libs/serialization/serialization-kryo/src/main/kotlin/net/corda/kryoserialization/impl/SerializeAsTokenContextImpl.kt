package net.corda.kryoserialization.impl

import net.corda.serialization.CheckpointSerializationContext
import net.corda.serialization.CheckpointSerializer
import net.corda.kryoserialization.SerializeAsTokenContextInternal
import net.corda.v5.serialization.SerializationToken
import net.corda.v5.serialization.SerializeAsToken
import net.corda.v5.serialization.SerializeAsTokenContext
import net.corda.v5.serialization.SingletonSerializeAsToken

val serializationContextKey = SerializeAsTokenContext::class.java

fun CheckpointSerializationContext.withTokenContext(serializationContext: SerializeAsTokenContext):
        CheckpointSerializationContext = this.withProperty(serializationContextKey, serializationContext)

/**
 * A context for mapping SerializationTokens to/from SerializeAsTokens.
 *
 * A context is initialised with an object containing all the instances of [SerializeAsToken] to eagerly register all the tokens.
 *
 * Then it is a case of using the companion object methods on [SerializeAsTokenSerializer] to set and clear context as necessary
 * when serializing to enable/disable tokenization.
 */
class CheckpointSerializeAsTokenContextImpl(init: SerializeAsTokenContext.() -> Unit) :
    SerializeAsTokenContextInternal {
    constructor(toBeTokenized: Any, serializer: CheckpointSerializer, context: CheckpointSerializationContext) : this({
        serializer.serialize(toBeTokenized, context.withTokenContext(this))
    })

    private val identifierToInstance = mutableMapOf<String, SerializeAsToken>()
    private val singletonToToken = mutableMapOf<SerializeAsToken, SerializationToken>()
    private var readOnly = false

    init {
        /**
         * Go ahead and eagerly serialize the object to register all of the tokens in the context.
         *
         * This results in the toToken() method getting called for any [SingletonSerializeAsToken] instances which
         * are encountered in the object graph as they are serialized and will therefore register the token to
         * object mapping for those instances.  We then immediately set the readOnly flag to stop further adhoc or
         * accidental registrations from occurring as these could not be deserialized in a deserialization-first
         * scenario if they are not part of this initial context construction serialization.
         */
        init(this)
        readOnly = true
    }

    override fun withIdentifier(id: String, toBeTokenized: SerializeAsToken) {
        if (id !in identifierToInstance) {
            // Only allowable if we are in SerializeAsTokenContext init (readOnly == false)
            if (readOnly) {
                throw UnsupportedOperationException("Attempt to write token for lazy registered $id. All tokens " +
                        "should be registered during context construction.")
            }
            identifierToInstance[id] = toBeTokenized
        }
    }

    override fun fromIdentifier(id: String) = identifierToInstance[id]
        ?: throw IllegalStateException("Unable to find tokenized instance of $id in context $this")

    override fun toSingletonToken(serializeAsToken: SingletonSerializeAsToken): SerializationToken {
        return singletonToToken.getOrPut(serializeAsToken) {
            serializeAsToken.toToken(this).also { withIdentifier(it.className, serializeAsToken) }
        }
    }
}