package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.membership.httprpc.v1.KeysRpcOps
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
) : KeysRpcOps, PluggableRPCOps<KeysRpcOps>, Lifecycle {

    override fun listKeys(holdingIdentityId: String): Collection<String> {
        return cryptoOpsClient.lookup(
            holdingIdentityId,
            0,
            500,
            CryptoKeyOrderBy.NONE,
            emptyMap()
        ).map { it.id }
    }

    override fun generateKeyPair(holdingIdentityId: String, alias: String, hsmCategory: String): String {
        return cryptoOpsClient.generateKeyPair(
            tenantId = holdingIdentityId,
            category = hsmCategory,
            alias = alias
        ).publicKeyId()
    }

    override fun generateKeyPem(
        holdingIdentityId: String,
        keyId: String
    ): String {
        val key = cryptoOpsClient.lookup(
            tenantId = holdingIdentityId,
            ids = listOf(keyId)
        ).firstOrNull() ?: throw ResourceNotFoundException("Can not find any key with ID $keyId for $holdingIdentityId")

        val publicKey = keyEncodingService.decodePublicKey(key.publicKey.array())
        return keyEncodingService.encodeAsString(publicKey)
    }

    override val targetInterface = KeysRpcOps::class.java

    override val protocolVersion = 1

    override val isRunning
        get() = cryptoOpsClient.isRunning

    override fun start() {
        cryptoOpsClient.start()
    }

    override fun stop() {
        cryptoOpsClient.stop()
    }
}
