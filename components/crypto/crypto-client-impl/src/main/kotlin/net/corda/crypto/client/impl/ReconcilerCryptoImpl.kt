package net.corda.crypto.client.impl

import java.net.URI
import net.corda.crypto.core.ApiNames.LOOKUP_PATH
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsError
import net.corda.data.crypto.wire.ops.reconciliation.request.LookUpKeyById
import net.corda.data.crypto.wire.ops.reconciliation.response.LookupKeyByIdResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.messaging.api.publisher.send
import net.corda.schema.configuration.BootConfig.CRYPTO_WORKER_REST_ENDPOINT
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.exceptions.CryptoException

class ReconcilerCryptoImpl(
    private val sender: HttpRpcClient,
    platformInfoProvider: PlatformInfoProvider,
    messagingConfig: SmartConfig,
) {

    fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey> {
        val request = LookUpKeyById(ShortHashes(keyIds.map { it.toString() }), tenantId)
        val response = sender.send<LookupKeyByIdResponse>(
            getRequestUrl(LOOKUP_PATH),
            request,
        )?.response ?: throw CordaRuntimeException(
            "Received empty response for ${request::class.java.name} for tenant '${CryptoTenants.P2P}'.",
        )

        when (response) {
            is CryptoSigningKeys -> return response.keys
            is EncryptionOpsError -> throw CryptoException(
                "${response.errorMessage.errorType} - ${response.errorMessage.errorMessage}",
            )
            else -> throw CordaRuntimeException(
                "Unexpected response type ${response::class.java} for ${request::class.java.name}.",
            )
        }
    }

    private val baseUrl by lazy {
        val platformVersion = platformInfoProvider.localWorkerSoftwareShortVersion
        "http://${messagingConfig.getString(CRYPTO_WORKER_REST_ENDPOINT)}/api/$platformVersion"
    }

    private fun getRequestUrl(path: String): URI {
        return URI.create("$baseUrl$path")
    }

}