package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.publicKeyId
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [PluggableRPCOps::class])
class KeysRpcOpsImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : KeysRpcOps, PluggableRPCOps<KeysRpcOps>, Lifecycle {
    override fun listSchemes(
        tenantId: String,
        hsmCategory: String,
    ): Collection<String> = cryptoOpsClient.getSupportedSchemes(
        tenantId = tenantId,
        category = hsmCategory
    )

    override fun listKeys(tenantId: String): Map<String, KeyMetaData> {
        return cryptoOpsClient.lookup(
            tenantId,
            0,
            500,
            CryptoKeyOrderBy.NONE,
            emptyMap()
        ).associate { it.id to KeyMetaData(keyId = it.id, alias = it.alias, hsmCategory = it.category, scheme = it.schemeCodeName) }
    }

    override fun generateKeyPair(
        tenantId: String,
        alias: String,
        hsmCategory: String,
        scheme: String
    ): String {
        return cryptoOpsClient.generateKeyPair(
            tenantId = tenantId,
            category = hsmCategory,
            alias = alias,
            scheme = scheme
        ).publicKeyId()
    }

    override fun generateKeyPem(
        tenantId: String,
        keyId: String
    ): String {
        val key = cryptoOpsClient.lookup(
            tenantId = tenantId,
            ids = listOf(keyId)
        ).firstOrNull() ?: throw ResourceNotFoundException("Can not find any key with ID $keyId for $tenantId")

        val publicKey = keyEncodingService.decodePublicKey(key.publicKey.array())
        return keyEncodingService.encodeAsString(publicKey)
    }

    override val targetInterface = KeysRpcOps::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<KeysRpcOps>(
        protocolVersion.toString()
    )
    private fun updateStatus(status: LifecycleStatus, reason: String) {
        coordinator.updateStatus(status, reason)
    }

    private fun activate(reason: String) {
        updateStatus(LifecycleStatus.UP, reason)
    }

    private fun deactivate(reason: String) {
        updateStatus(LifecycleStatus.DOWN, reason)
    }

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
        )
    )
    private val coordinator = lifecycleCoordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
