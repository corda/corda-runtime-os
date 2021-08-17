package net.corda.internal.serialization

import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationFactory

interface SerializationEnvironment {

    companion object {
        fun with(
                serializationFactory: SerializationFactory,
                p2pContext: SerializationContext,
                rpcServerContext: SerializationContext? = null,
                rpcClientContext: SerializationContext? = null,
                storageContext: SerializationContext? = null
        ): SerializationEnvironment =
                SerializationEnvironmentImpl(
                        serializationFactory = serializationFactory,
                        p2pContext = p2pContext,
                        optionalRpcServerContext = rpcServerContext,
                        optionalRpcClientContext = rpcClientContext,
                        optionalStorageContext = storageContext
                )
    }

    val serializationFactory: SerializationFactory
    val p2pContext: SerializationContext
    val rpcServerContext: SerializationContext
    val rpcClientContext: SerializationContext
    val storageContext: SerializationContext

    val defaultContext: SerializationContext get() = serializationFactory.currentContext ?: p2pContext

    /**
     * Get the [SerializationEnvironment]'s default [P2pSerializationService].
     */
    val p2pSerialization: P2pSerializationService
    /**
     * Get the [SerializationEnvironment]'s default [RpcServerSerializationService].
     */
    val rpcServerSerialization: RpcServerSerializationService
    /**
     * Get the [SerializationEnvironment]'s default [RpcClientSerializationService].
     */
    val rpcClientSerialization: RpcClientSerializationService
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
     * Retrieve a new [RpcServerSerializationService] that has applied the input [customization] to its [SerializationContext].
     *
     * @param customization The customization to apply to the services [SerializationContext].
     */
    fun rpcServerSerialization(customization: (context: SerializationContext) -> SerializationContext): RpcServerSerializationService

    /**
     * Retrieve a new [RpcClientSerializationService] that has applied the input [customization] to its [SerializationContext].
     *
     * @param customization The customization to apply to the services [SerializationContext].
     */
    fun rpcClientSerialization(customization: (context: SerializationContext) -> SerializationContext): RpcClientSerializationService

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
    private val optionalRpcServerContext: SerializationContext? = null,
    private val optionalRpcClientContext: SerializationContext? = null,
    private val optionalStorageContext: SerializationContext? = null
) : SerializationEnvironment {

    override val rpcServerContext: SerializationContext get() = optionalRpcServerContext ?:
            throw UnsupportedOperationException("RPC server serialization not supported in this environment")

    override val rpcClientContext: SerializationContext get() = optionalRpcClientContext ?:
        throw UnsupportedOperationException("RPC client serialization not supported in this environment")

    override val storageContext: SerializationContext get() = optionalStorageContext ?:
        throw UnsupportedOperationException("Storage serialization not supported in this environment")

    override val p2pSerialization: P2pSerializationService by lazy { P2pSerializationServiceImpl(SerializationServiceImpl(this, p2pContext)) }

    override val rpcServerSerialization: RpcServerSerializationService by lazy {
        RpcServerSerializationServiceImpl(
            SerializationServiceImpl(
                this,
                rpcServerContext
            )
        )
    }

    override val rpcClientSerialization: RpcClientSerializationService by lazy {
        RpcClientSerializationServiceImpl(
            SerializationServiceImpl(
                this,
                rpcClientContext
            )
        )
    }

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

    override fun rpcServerSerialization(customization: (context: SerializationContext) -> SerializationContext): RpcServerSerializationService {
        return RpcServerSerializationServiceImpl(SerializationServiceImpl(this, customization(rpcServerContext)))
    }

    override fun rpcClientSerialization(customization: (context: SerializationContext) -> SerializationContext): RpcClientSerializationService {
        return RpcClientSerializationServiceImpl(SerializationServiceImpl(this, customization(rpcClientContext)))
    }

    override fun storageSerialization(customization: (context: SerializationContext) -> SerializationContext): StorageSerializationService {
        return StorageSerializationServiceImpl(SerializationServiceImpl(this, customization(storageContext)))
    }
}

private val _nodeSerializationEnv = SimpleToggleField<SerializationEnvironment>("nodeSerializationEnv", true)
/** Should be set once in main. */
var nodeSerializationEnv by _nodeSerializationEnv

val _driverSerializationEnv = SimpleToggleField<SerializationEnvironment>("driverSerializationEnv")

val _rpcClientSerializationEnv = SimpleToggleField<SerializationEnvironment>("rpcClientSerializationEnv")

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
        _inheritableContextSerializationEnv,
        _rpcClientSerializationEnv
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
    SerializationDefaults.Config.rpcServerContext = { effectiveSerializationEnv.rpcServerContext }
    SerializationDefaults.Config.rpcClientContext = { effectiveSerializationEnv.rpcClientContext }
    SerializationDefaults.Config.storageContext = { effectiveSerializationEnv.storageContext }
    SerializationDefaults.Config.p2pContext = { effectiveSerializationEnv.p2pContext }
    SerializationDefaults.Config.serializationFactory = { effectiveSerializationEnv.serializationFactory }
}
