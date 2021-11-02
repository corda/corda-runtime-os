package net.corda.internal.serialization

import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationFactory

interface SerializationEnvironment {

    companion object {
        fun with(
            serializationFactory: SerializationFactory,
            p2pContext: SerializationContext,
            storageContext: SerializationContext? = null
        ): SerializationEnvironment =
            SerializationEnvironmentImpl(
                serializationFactory = serializationFactory,
                p2pContext = p2pContext,
                optionalStorageContext = storageContext
            )
    }

    val serializationFactory: SerializationFactory
    val p2pContext: SerializationContext
    val storageContext: SerializationContext

    val defaultContext: SerializationContext get() = serializationFactory.currentContext ?: p2pContext

    /**
     * Get the [SerializationEnvironment]'s default [P2pSerializationService].
     */
    val p2pSerialization: P2pSerializationService

    /**
     * Get the [SerializationEnvironment]'s default [StorageSerializationService].
     */
    val storageSerialization: StorageSerializationService

    /**
     * Retrieve a new [P2pSerializationService] that has applied the input [customization] to its [SerializationContext].
     *
     * @param customization The customization to apply to the services [SerializationContext].
     */
    fun p2pSerialization(customization: (context: SerializationContext) -> SerializationContext): P2pSerializationService

    /**
     * Retrieve a new [StorageSerializationService] that has applied the input [customization] to its [SerializationContext].
     *
     * @param customization The customization to apply to the services [SerializationContext].
     */
    fun storageSerialization(customization: (context: SerializationContext) -> SerializationContext): StorageSerializationService
}

@Suppress("LongParameterList")
private class SerializationEnvironmentImpl(
    override val serializationFactory: SerializationFactory,
    override val p2pContext: SerializationContext,
    private val optionalStorageContext: SerializationContext? = null
) : SerializationEnvironment {

    override val storageContext: SerializationContext get() = optionalStorageContext
        ?: throw UnsupportedOperationException("Storage serialization not supported in this environment")

    override val p2pSerialization: P2pSerializationService by lazy { P2pSerializationServiceImpl(SerializationServiceImpl(this, p2pContext)) }

    override val storageSerialization: StorageSerializationService by lazy {
        StorageSerializationServiceImpl(
            SerializationServiceImpl(
                this,
                storageContext
            )
        )
    }

    override fun p2pSerialization(customization: (context: SerializationContext) -> SerializationContext): P2pSerializationService {
        return P2pSerializationServiceImpl(SerializationServiceImpl(this, customization(p2pContext)))
    }

    override fun storageSerialization(customization: (context: SerializationContext) -> SerializationContext): StorageSerializationService {
        return StorageSerializationServiceImpl(SerializationServiceImpl(this, customization(storageContext)))
    }
}
