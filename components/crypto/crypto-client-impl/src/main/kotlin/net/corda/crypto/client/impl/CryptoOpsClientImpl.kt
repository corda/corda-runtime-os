package net.corda.crypto.client.impl

import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoSignatureParameterSpec
import net.corda.data.crypto.wire.CryptoSignatureSchemes
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.CryptoSigningKeys
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.GenerateKeyPairCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.commands.SignWithSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.queries.AssignedHSMRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.ByIdsRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.crypto.wire.ops.rpc.queries.KeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.queries.SupportedSchemesRpcQuery
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.toBase58
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.KEY_LOOKUP_INPUT_ITEMS_LIMIT
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.corda.v5.crypto.sha256Bytes
import net.corda.v5.crypto.toStringShort
import java.nio.ByteBuffer
import java.security.PublicKey

@Suppress("TooManyFunctions")
class CryptoOpsClientImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse>
) {
    companion object {
        private val logger = contextLogger()
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
        val response = request.execute(CryptoSignatureSchemes::class.java)
        return response!!.codes
    }

    fun filterMyKeys(tenantId: String, candidateKeys: Collection<PublicKey>): Collection<PublicKey> {
        logger.info(
            "Sending '{}'(tenant={},candidateKeys={})",
            ByIdsRpcQuery::class.java.simpleName,
            tenantId,
            candidateKeys.joinToString { it.toStringShort().take(12) + ".." }
        )
        val request = createRequest(
            tenantId = tenantId,
            request = ByIdsRpcQuery(
                candidateKeys.map {
                    publicKeyIdFromBytes(schemeMetadata.encodeAsByteArray(it))
                }
            )
        )
        val response = request.execute(CryptoSigningKeys::class.java)
        return response!!.keys.map {
            schemeMetadata.decodePublicKey(it.publicKey.array())
        }
    }

    fun filterMyKeysProxy(tenantId: String, candidateKeys: Iterable<ByteBuffer>): CryptoSigningKeys {
        logger.info(
            "Sending '{}'(tenant={},candidateKeys={})",
            ByIdsRpcQuery::class.java.simpleName,
            tenantId,
            candidateKeys.joinToString { it.array().sha256Bytes().toBase58().take(12) + ".." }
        )
        val request = createRequest(
            tenantId = tenantId,
            request = ByIdsRpcQuery(
                candidateKeys.map {
                    publicKeyIdFromBytes(it.array())
                }
            )
        )
        return request.execute(CryptoSigningKeys::class.java)!!
    }

    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
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
            request = GenerateKeyPairCommand(category, alias, null, context.toWire())
        )
        val response = request.execute(CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun generateKeyPair(
        tenantId: String,
        category: String,
        alias: String,
        externalId: String,
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
            request = GenerateKeyPairCommand(category, alias, externalId, context.toWire())
        )
        val response = request.execute(CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun freshKey(tenantId: String, context: Map<String, String>): PublicKey {
        logger.info(
            "Sending '{}'(tenant={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(null, context.toWire())
        )
        val response = request.execute(CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun freshKey(tenantId: String, externalId: String, context: Map<String, String>): PublicKey {
        logger.info(
            "Sending '{}'(tenant={},externalId={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId,
            externalId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(externalId, context.toWire())
        )
        val response = request.execute(CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    fun freshKeyProxy(tenantId: String, context: KeyValuePairList): CryptoPublicKey {
        logger.info(
            "Sending '{}'(tenant={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(null, context)
        )
        return request.execute(CryptoPublicKey::class.java)!!
    }

    fun freshKeyProxy(tenantId: String, externalId: String, context: KeyValuePairList): CryptoPublicKey {
        logger.info(
            "Sending '{}'(tenant={},externalId={})",
            GenerateFreshKeyRpcCommand::class.java.simpleName,
            tenantId,
            externalId
        )
        val request = createRequest(
            tenantId = tenantId,
            request = GenerateFreshKeyRpcCommand(externalId, context)
        )
        return request.execute(CryptoPublicKey::class.java)!!
    }

    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        logger.info(
            "Sending '{}'(tenant={},publicKey={}..)",
            SignRpcCommand::class.java.simpleName,
            tenantId,
            publicKey.toStringShort().take(12)
        )
        val request = createRequest(
            tenantId,
            SignRpcCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.execute(CryptoSignatureWithKey::class.java)
        return DigitalSignature.WithKey(
            by = schemeMetadata.decodePublicKey(response!!.publicKey.array()),
            bytes = response.bytes.array()
        )
    }

    fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        logger.info(
            "Sending '{}'(tenant={},publicKey={}..,signatureSpec={})",
            SignWithSpecRpcCommand::class.java.simpleName,
            tenantId,
            publicKey.toStringShort().take(12),
            signatureSpec
        )
        val request = createRequest(
            tenantId,
            SignWithSpecRpcCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                signatureSpec.toWire(),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.execute(CryptoSignatureWithKey::class.java)
        return DigitalSignature.WithKey(
            by = schemeMetadata.decodePublicKey(response!!.publicKey.array()),
            bytes = response.bytes.array()
        )
    }

    fun signProxy(
        tenantId: String,
        publicKey: ByteBuffer,
        data: ByteBuffer,
        context: KeyValuePairList
    ): CryptoSignatureWithKey {
        logger.info(
            "Sending '{}'(tenant={},publicKey={}..)",
            SignRpcCommand::class.java.simpleName,
            tenantId,
            publicKey.array().sha256Bytes().toBase58().take(12)
        )
        val request = createRequest(
            tenantId,
            SignRpcCommand(
                publicKey,
                data,
                context
            )
        )
        return request.execute(CryptoSignatureWithKey::class.java)!!
    }

    fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: CryptoKeyOrderBy,
        filter: Map<String, String>
    ): List<CryptoSigningKey> {
        logger.debug(
            "Sending '{}'({}, {}, {}, {}, [{}])",
            KeysRpcQuery::class.java.simpleName,
            tenantId,
            skip,
            take,
            orderBy,
            filter.map { it }.joinToString { "${it.key}=${it.value}" }
        )
        val request = createRequest(
            tenantId,
            KeysRpcQuery(
                skip,
                take,
                CryptoKeyOrderBy.valueOf(orderBy.name),
                filter.toWire()
            )
        )
        return request.execute(CryptoSigningKeys::class.java)!!.keys
    }

    fun lookup(tenantId: String, ids: List<String>): List<CryptoSigningKey> {
        logger.debug(
            "Sending '{}'(tenant={}, ids=[{}])",
            ByIdsRpcQuery::class.java.simpleName,
            tenantId,
            ids.joinToString()
        )
        require(ids.size <= KEY_LOOKUP_INPUT_ITEMS_LIMIT) {
            "The number of items exceeds $KEY_LOOKUP_INPUT_ITEMS_LIMIT"
        }
        val request = createRequest(
            tenantId,
            ByIdsRpcQuery(ids)
        )
        return request.execute(CryptoSigningKeys::class.java)!!.keys
    }

    fun findHSM(tenantId: String, category: String): HSMInfo? {
        logger.info(
            "Sending '{}'(tenant={},category={})",
            AssignedHSMRpcQuery::class.java.simpleName,
            tenantId,
            category
        )
        val request = createRequest(
            tenantId,
            AssignedHSMRpcQuery(category)
        )
        return request.execute(HSMInfo::class.java, allowNoContentValue = true)
    }

    private fun createRequest(tenantId: String, request: Any): RpcOpsRequest =
        RpcOpsRequest(
            createWireRequestContext<CryptoOpsClientImpl>(tenantId),
            request
        )

    private fun SignatureSpec.toWire() = CryptoSignatureSpec(
        signatureName,
        customDigestName?.name,
        if (params != null) {
            val params = schemeMetadata.serialize(params!!)
            CryptoSignatureParameterSpec(params.clazz, ByteBuffer.wrap(params.bytes))
        } else {
            null
        }
    )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> RpcOpsRequest.execute(
        respClazz: Class<RESPONSE>,
        allowNoContentValue: Boolean = false
    ): RESPONSE? {
        while (true) {
            try {
                val response = sender.sendRequest(this).getOrThrow()
                require(
                    response.context.requestingComponent == context.requestingComponent &&
                            response.context.tenantId == context.tenantId
                ) {
                    "Expected ${context.tenantId} tenant and ${context.requestingComponent} component, but " +
                            "received ${response.response::class.java.name} with ${response.context.tenantId} tenant" +
                            " ${response.context.requestingComponent} component"
                }
                if (response.response::class.java == CryptoNoContentValue::class.java && allowNoContentValue) {
                    logger.debug(
                        "Received empty response for {} for tenant {}",
                        request::class.java.name,
                        context.tenantId
                    )
                    return null
                }
                require(response.response != null && (response.response::class.java == respClazz)) {
                    "Expected ${respClazz.name} for ${context.tenantId} tenant, but " +
                            "received ${response.response::class.java.name} with ${response.context.tenantId} tenant"
                }
                logger.debug("Received response {} for tenant {}", respClazz.name, context.tenantId)
                return response.response as RESPONSE
            } catch (e: CryptoServiceLibraryException) {
                logger.error("Failed executing ${request::class.java.name} for tenant ${context.tenantId}", e)
                throw e
            } catch (e: Throwable) {
                val message = "Failed executing ${request::class.java.name} for tenant ${context.tenantId}"
                logger.error(message, e)
                throw CryptoServiceLibraryException(message, e)
            }
        }
    }
}