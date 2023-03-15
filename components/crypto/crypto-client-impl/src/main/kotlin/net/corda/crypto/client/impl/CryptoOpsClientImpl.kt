package net.corda.crypto.client.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.cipher.suite.toStringShort
import net.corda.crypto.component.impl.retry
import net.corda.crypto.component.impl.toClientException
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.impl.createWireRequestContext
import net.corda.crypto.impl.toMap
import net.corda.crypto.impl.toWire
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.SecureHashes
import net.corda.data.crypto.ShortHashes
import net.corda.data.crypto.wire.CryptoDerivedSharedSecret
import net.corda.data.crypto.wire.CryptoKeySchemes
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.DeriveSharedSecretCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateWrappingKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.publisher.RPCSender
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.debug
import net.corda.v5.base.util.EncodingUtils.toBase58
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Duration
import java.util.UUID

@Suppress("TooManyFunctions")
class CryptoOpsClientImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse>,
    private val digestService: PlatformDigestService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun SecureHashes.toDto(): List<SecureHash> =
            this.hashes.map {
                SecureHashImpl(it.algorithm, it.bytes.array())
            }

        private fun SecureHash.toAvro(): net.corda.data.crypto.SecureHash =
            net.corda.data.crypto.SecureHash(this.algorithm, ByteBuffer.wrap(bytes))
    }

    fun getSupportedSchemes(tenantId: String, category: String): List<String> {
        logger.info(
            "Sending '{}'(tenant={},category={})",
            SupportedSchemesRpcQuery::class.java.simpleName,
            tenantId,
            category
        )
        val request = createRequest(
            tenantId = tenantId,
            request = SupportedSchemesRpcQuery(category)
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoKeySchemes::class.java)
        return response!!.codes
    }

    // TODO Users who are using this API need to revisit to determine if they need to migrate to search by full Id
    fun filterMyKeys(
        tenantId: String,
        candidateKeys: Collection<PublicKey>,
        usingFullIds: Boolean
    ): Collection<PublicKey> {
        val keyIdsForLogging = mutableListOf<String>()
        val candidateKeyIds =
            if (usingFullIds) {
                SecureHashes(
                    candidateKeys.map {
                        val secureHash = it.fullId(schemeMetadata, digestService)
                            .also { secureHash -> keyIdsForLogging.add(secureHash.toString()) }
                        secureHash.toAvro()
                    }
                )
            } else {
                ShortHashes(
                    candidateKeys.map {
                        publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(it))
                            .also { shortHash -> keyIdsForLogging.add(shortHash) }
                    }
                )
            }

        logger.info(
            "Sending '{}'(tenant={},candidateKeys={})",
            ByIdsRpcQuery::class.java.simpleName,
            tenantId,
            keyIdsForLogging.joinToString { it }
        )

        val request = createRequest(
            tenantId = tenantId,
            request = ByIdsRpcQuery(candidateKeyIds)
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)
        return response!!.keys.map {
            schemeMetadata.decodePublicKey(it.publicKey.array())
        }
    }

    // This path is not being currently used - consider removing it
    fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys {
        val publicKeyIds = candidateKeys.map {
            publicKeyIdFromBytes(it.array())
        }
        return lookupKeysByIdsProxy(tenantId, ShortHashes(publicKeyIds))
    }

    // This method is only used by `filterMyKeysProxy` which is not used - consider removing it
    private fun lookupKeysByIdsProxy(tenantId: String, keyIds: ShortHashes): CryptoSigningKeys {
        logger.info(
            "Sending '{}'(tenant={},candidateKeys={})",
            ByIdsRpcQuery::class.java.simpleName,
            tenantId,
            keyIds.hashes.joinToString { it }
        )

        val request = createRequest(
            tenantId = tenantId,
            request = ByIdsRpcQuery(keyIds)
        )
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!
    }

    @Suppress("MaxLineLength")
    fun lookupKeysByFullIdsProxy(tenantId: String, fullKeyIds: SecureHashes): CryptoSigningKeys {
        logger.info(
            "Sending '{}'(tenant={},candidateKeys={})", ByIdsRpcQuery::class.java.simpleName, tenantId, fullKeyIds.toDto().joinToString()
        )

        val request = createRequest(tenantId, request = ByIdsRpcQuery(fullKeyIds))
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!
    }

    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={},category={},alias={})",
            GenerateKeyPairCommand::class.java.simpleName,
            tenantId,
            category,
            alias
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateKeyPairCommand(category, alias, null, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    @Suppress("LongParameterList")
    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={},category={},alias={})",
            GenerateKeyPairCommand::class.java.simpleName,
            tenantId,
            category,
            alias
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateKeyPairCommand(category, alias, externalId, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun freshKey(
        tenantId: String,
        category: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(category, null, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun freshKey(
        tenantId: String,
        category: String,
        externalId: String,
        scheme: String,
        context: Map<String, String>
    ): PublicKey {
        logger.info(
            "Sending '{}'(tenant={},externalId={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId,
            externalId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(category, externalId, scheme, context.toWire())
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        logger.info(
            "Sending '{}'(tenant={}, publicKey={}, signatureSpec={})",
            SignRpcCommand::class.java.simpleName,
            tenantId,
            publicKey.toStringShort().take(12),
            signatureSpec
        )
        val request = createRequest(
            tenantId,
            SignRpcCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                signatureSpec.toWire(schemeMetadata),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.execute(Duration.ofSeconds(20), CryptoSignatureWithKey::class.java)
        return DigitalSignature.WithKey(
            schemeMetadata.decodePublicKey(response!!.publicKey.array()),
            response.bytes.array(),
            response.context.toMap()
        )
    }

    fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        signatureSpec: CryptoSignatureSpec,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey {
        logger.debug {
            "Sending '${SignRpcCommand::class.java.simpleName}'(tenant=${tenantId}," +
                    "publicKey=${toBase58(publicKey.array().sha256Bytes()).take(12)}..)"
        }
        val request = createRequest(
            tenantId,
            SignRpcCommand(
                publicKey,
                signatureSpec,
                data,
                context
            )
        )
        return request.execute(Duration.ofSeconds(20), CryptoSignatureWithKey::class.java)!!
    }

    fun createWrappingKey(
        hsmId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) {
        logger.info(
            "Sending '{}'(hsmId={},failIfExists={},masterKeyAlias={})",
            GenerateWrappingKeyRpcCommand::class.java.simpleName,
            hsmId,
            failIfExists,
            masterKeyAlias
        )
        val request = createRequest(
            CryptoTenants.CRYPTO,
            GenerateWrappingKeyRpcCommand(
                hsmId,
                masterKeyAlias,
                failIfExists,
                context.toWire()
            )
        )
        request.execute(Duration.ofSeconds(20), CryptoNoContentValue::class.java, allowNoContentValue = true)
    }

    fun deriveSharedSecret(
        tenantId: String,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        context: Map<String, String>
    ): ByteArray {
        logger.info(
            "Sending '{}'(publicKey={},otherPublicKey={})",
            DeriveSharedSecretCommand::class.java.simpleName,
            publicKey.publicKeyId(),
            otherPublicKey.publicKeyId()
        )
        val request = createRequest(
            tenantId,
            DeriveSharedSecretCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(otherPublicKey)),
                context.toWire()
            )
        )
        return request.execute(
            Duration.ofSeconds(20),
            CryptoDerivedSharedSecret::class.java
        )!!.secret.array()
    }

    fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: CryptoKeyOrderBy,
        filter: Map<String, String>
    ): List<CryptoSigningKey> {
        logger.debug {
            "Sending '${KeysRpcQuery::class.java.simpleName}'($tenantId, $skip, $take, $orderBy," +
                    " [${filter.map { it }.joinToString { "${it.key}=${it.value}" }}])"
        }
        val request = createRequest(
            tenantId,
            KeysRpcQuery(
                skip,
                take,
                CryptoKeyOrderBy.valueOf(orderBy.name),
                filter.toWire()
            )
        )
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!.keys
    }

    // TODO Users who are using this API need to revisit to determine if they need to migrate to search ByFullIds
    fun lookupKeysByIds(tenantId: String, keyIds: List<ShortHash>): List<CryptoSigningKey> {
        logger.debug { "Sending '${ByIdsRpcQuery::class.java.simpleName}'(tenant=$tenantId, ids=[${keyIds.joinToString()}])" }
        require(keyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of items exceeds ${KEY_LOOKUP_INPUT_ITEMS_LIMIT}"
        }

        val request = createRequest(
            tenantId,
            ByIdsRpcQuery(ShortHashes(keyIds.map { it.value }))
        )
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!.keys
    }

    @Suppress("MaxLineLength")
    fun lookupKeysByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): List<CryptoSigningKey> {
        logger.debug { "Sending '${ByIdsRpcQuery::class.java.simpleName}'(tenant=$tenantId, ids=[${fullKeyIds.joinToString { it.toString() }}])" }
        require(fullKeyIds.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) { "The number of items exceeds ${KEY_LOOKUP_INPUT_ITEMS_LIMIT}" }

        val request = createRequest(
            tenantId,
            ByIdsRpcQuery(
                SecureHashes(
                    fullKeyIds.map {
                        it.toAvro()
                    }
                )
            ))
        return request.execute(Duration.ofSeconds(20), CryptoSigningKeys::class.java)!!.keys
    }

    private fun createRequest(tenantId: String, request: Any): RpcOpsRequest =
        RpcOpsRequest(
            createWireRequestContext<CryptoOpsClientImpl>(requestId = UUID.randomUUID().toString(), tenantId),
            request
        )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> RpcOpsRequest.execute(
        timeout: Duration,
        respClazz: Class<RESPONSE>,
        allowNoContentValue: Boolean = false,
        retries: Int = 3
    ): RESPONSE? = try {
        val response = retry(retries, logger) {
            sender.sendRequest(this).getOrThrow(timeout)
        }
        check(
            response.context.requestingComponent == context.requestingComponent &&
                    response.context.tenantId == context.tenantId
        ) {
            "Expected ${context.tenantId} tenant and ${context.requestingComponent} component, but " +
                    "received ${response.response::class.java.name} with ${response.context.tenantId} tenant" +
                    " ${response.context.requestingComponent} component"
        }
        if (response.response::class.java == CryptoNoContentValue::class.java && allowNoContentValue) {
            logger.debug {
                "Received empty response for ${request::class.java.name} for tenant ${context.tenantId}"
            }
            null
        } else {
            check(response.response != null && (response.response::class.java == respClazz)) {
                "Expected ${respClazz.name} for ${context.tenantId} tenant, but " +
                        "received ${response.response::class.java.name} with ${response.context.tenantId} tenant"
            }
            logger.debug {
                "Received response ${respClazz.name} for tenant ${context.tenantId}"
            }
            response.response as RESPONSE
        }
    } catch (e: CordaRPCAPIResponderException) {
        throw e.toClientException()
    } catch (e: Throwable) {
        logger.error("Failed executing ${request::class.java.name} for tenant ${context.tenantId}", e)
        throw e
    }
}

private fun PublicKey.fullId(keyEncodingService: KeyEncodingService, digestService: PlatformDigestService): SecureHash =
    digestService.hash(
        keyEncodingService.encodeAsByteArray(this),
        DigestAlgorithmName.SHA2_256
    )