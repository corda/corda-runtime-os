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

    override val storageContext: SerializationContext get() = optionalStorageContext ?:
        throw UnsupportedOperationException("Storage serialization not supported in this environment")

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

private val _nodeSerializationEnv = SimpleToggleField<SerializationEnvironment>("nodeSerializationEnv", true)
/** Should be set once in main. */
var nodeSerializationEnv by _nodeSerializationEnv

val _driverSerializationEnv = SimpleToggleField<SerializationEnvironment>("driverSerializationEnv")

val _contextSerializationEnv = ThreadLocalToggleField<SerializationEnvironment>("contextSerializationEnv")

val _inheritableContextSerializationEnv = InheritableThreadLocalToggleField<SerializationEnvironment>("inheritableContextSerializationEnv") { stack ->
    stack.fold(false) { isAGlobalThreadBeingCreated, e ->
        isAGlobalThreadBeingCreated ||
                (e.className == "io.netty.util.concurrent.GlobalEventExecutor" && e.methodName == "startThread") ||
                (e.className == "java.util.concurrent.ForkJoinPool\$DefaultForkJoinWorkerThreadFactory" && e.methodName == "newThread")
    }
}

private val serializationEnvFields = listOf(
        _nodeSerializationEnv,
        _driverSerializationEnv,
        _contextSerializationEnv,
        _inheritableContextSerializationEnv
)

val _allEnabledSerializationEnvs: List<Pair<String, SerializationEnvironment>>
    get() = serializationEnvFields.mapNotNull { it.get()?.let { env -> Pair(it.name, env) } }

val effectiveSerializationEnv: SerializationEnvironment
    get() {
        return _allEnabledSerializationEnvs.let {
            checkNotNull(it.singleOrNull()?.second) {
                "Expected exactly 1 of {${serializationEnvFields.joinToString(", ") { it.name }}} " +
                        "but got: {${it.joinToString(", ") { it.first }}}"
            }
        }
    }

fun setupSerializationDefaults() {
    SerializationDefaults.Config.storageContext = { effectiveSerializationEnv.storageContext }
    SerializationDefaults.Config.p2pContext = { effectiveSerializationEnv.p2pContext }
    SerializationDefaults.Config.serializationFactory = { effectiveSerializationEnv.serializationFactory }
}
