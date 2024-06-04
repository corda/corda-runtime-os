package net.corda.crypto.client.impl

import java.net.URI
import net.corda.crypto.core.ApiNames.ENCRYPT_PATH
import net.corda.crypto.core.ApiNames.LOOKUP_PATH
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.messaging.api.publisher.send
import net.corda.schema.configuration.BootConfig.CRYPTO_WORKER_REST_ENDPOINT
import net.corda.v5.base.exceptions.CordaRuntimeException

class ReconcilerCryptoOpsClientImpl(
    private val sender: HttpRpcClient,
    platformInfoProvider: PlatformInfoProvider,
    messagingConfig: SmartConfig,
) {



    fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey> {
        val response = sender.send<EncryptionOpsResponse>(
            getRequestUrl(LOOKUP_PATH),
            request,
        )?.response ?: throw CordaRuntimeException(
            "Received empty response for ${request::class.java.name} for tenant '${CryptoTenants.P2P}'.",
        )
    }

    private val baseUrl by lazy {
        val platformVersion = platformInfoProvider.localWorkerSoftwareShortVersion
        "http://${messagingConfig.getString(CRYPTO_WORKER_REST_ENDPOINT)}/api/$platformVersion"
    }

    private fun getRequestUrl(path: String): URI {
        return URI.create("$baseUrl$path")
    }

}