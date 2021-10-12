package net.corda.internal.serialization

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationFactory

/**
 * Global singletons to be used as defaults that are injected elsewhere (generally, in the node).
 *
 * Plus a static helper.
 */
object SerializationDefaults {
    object Config {
        var storageContext: () -> SerializationContext = { throw CordaRuntimeException("SerializationDefault accessed before being set by node startup") }
        var p2pContext: () -> SerializationContext = { throw CordaRuntimeException("SerializationDefault accessed before being set by node startup") }
        var serializationFactory: () -> SerializationFactory = { throw CordaRuntimeException("SerializationDefault accessed before being set by node startup") }
    }

    val SERIALIZATION_FACTORY: SerializationFactory get() = Config.serializationFactory()

    val STORAGE_CONTEXT: SerializationContext
        get() = Config.storageContext()

    /**
     * A default factory for serialization/deserialization, taking into account the [currentFactory] if set.
     */
    val currentOrDefaultFactory: SerializationFactory get() = SerializationFactory.currentFactory ?: SERIALIZATION_FACTORY

    /**
     * A context to use as a default if you do not require a specially configured context.  It will be the current context
     * if the use is somehow nested (see [currentContext]).
     */
    fun currentOrDefaultContext(factory: SerializationFactory): SerializationContext = factory.currentContext
            ?: Config.p2pContext()
}


