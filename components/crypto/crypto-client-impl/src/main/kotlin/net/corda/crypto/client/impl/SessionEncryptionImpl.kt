package net.corda.crypto.client.impl

import net.corda.crypto.core.ApiNames.DECRYPT_PATH
import net.corda.crypto.core.ApiNames.ENCRYPT_PATH
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants
import net.corda.data.crypto.wire.ops.encryption.request.DecryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.request.EncryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.response.CryptoDecryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.CryptoEncryptionResult
import net.corda.data.crypto.wire.ops.encryption.response.DecryptionOpsResponse
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsError
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.messaging.api.publisher.HttpRpcClient
import net.corda.messaging.api.publisher.send
import net.corda.schema.configuration.BootConfig.CRYPTO_WORKER_REST_ENDPOINT
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.exceptions.CryptoException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.ByteBuffer

@Suppress("ThrowsCount")
class SessionEncryptionImpl(
    private val sender: HttpRpcClient,
    platformInfoProvider: PlatformInfoProvider,
    messagingConfig: SmartConfig,
) {
    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun encryptSessionData(plainBytes: ByteArray, alias: String?): ByteArray {
        logger.info(
            "Sending '{}'(alias={})",
            EncryptRpcCommand::class.java.simpleName,
            alias,
        )
        val request = EncryptRpcCommand(
            CryptoConsts.Categories.ENCRYPTION_SECRET,
            alias,
            ByteBuffer.wrap(plainBytes),
        )

        val response = sender.send<EncryptionOpsResponse>(
            getRequestUrl(ENCRYPT_PATH),
            request,
        )?.response ?: throw CordaRuntimeException(
            "Received empty response for ${request::class.java.name} for tenant '${CryptoTenants.P2P}'.",
        )

        return when (response) {
            is CryptoEncryptionResult -> response.cipherBytes.array()
            is EncryptionOpsError -> throw CryptoException(
                "${response.errorMessage.errorType} - ${response.errorMessage.errorMessage}",
            )
            else -> throw CordaRuntimeException(
                "Unexpected response type ${response::class.java} for ${request::class.java.name}.",
            )
        }
    }

    fun decryptSessionData(cipherBytes: ByteArray, alias: String?): ByteArray {
        logger.info(
            "Sending '{}'(alias={})",
            DecryptRpcCommand::class.java.simpleName,
            alias,
        )
        val request = DecryptRpcCommand(
            CryptoConsts.Categories.ENCRYPTION_SECRET,
            alias,
            ByteBuffer.wrap(cipherBytes),
        )
        val response = sender.send<DecryptionOpsResponse>(
            getRequestUrl(DECRYPT_PATH),
            request,
        )?.response ?: throw CordaRuntimeException(
            "Received empty response for ${request::class.java.name} for tenant '${CryptoTenants.P2P}'.",
        )

        return when (response) {
            is CryptoDecryptionResult -> response.plainBytes.array()
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
