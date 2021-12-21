package net.corda.crypto.client

import net.corda.crypto.CryptoOpsClient
import net.corda.data.crypto.config.HSMInfo
import net.corda.data.crypto.wire.CryptoNoContentValue
import net.corda.data.crypto.wire.CryptoPublicKey
import net.corda.data.crypto.wire.CryptoPublicKeys
import net.corda.data.crypto.wire.CryptoSignature
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.crypto.wire.ops.rpc.AssignedHSMRpcQuery
import net.corda.data.crypto.wire.ops.rpc.FilterMyKeysRpcQuery
import net.corda.data.crypto.wire.ops.rpc.GenerateFreshKeyRpcCommand
import net.corda.data.crypto.wire.ops.rpc.HSMAliasRpcQuery
import net.corda.data.crypto.wire.ops.rpc.PublicKeyRpcQuery
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.crypto.wire.ops.rpc.SignRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithAliasSpecRpcCommand
import net.corda.data.crypto.wire.ops.rpc.SignWithSpecRpcCommand
import net.corda.messaging.api.publisher.RPCSender
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.UUID

class CryptoOpsPublisher(
    private val schemeMetadata: CipherSchemeMetadata,
    private val sender: RPCSender<RpcOpsRequest, RpcOpsResponse>
) : CryptoOpsClient {
    companion object {
        private val logger = contextLogger()
    }

    override fun findPublicKey(tenantId: String, alias: String): PublicKey? {
        val request = createRequest(
            tenantId,
            PublicKeyRpcQuery(alias)
        )
        val response = request.execute(CryptoPublicKey::class.java, allowNoContentValue = true)
        return if (response != null) {
            schemeMetadata.decodePublicKey(response.key.array())
        } else {
            null
        }
    }

    override fun filterMyKeys(tenantId: String, candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> {
        val request = createRequest(
            tenantId,
            FilterMyKeysRpcQuery(
                candidateKeys.map {
                    ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(it))
                }
            )
        )
        val response = request.execute(CryptoPublicKeys::class.java)
        return response!!.keys.map {
            schemeMetadata.decodePublicKey(it.array())
        }
    }

    override fun freshKey(tenantId: String, context: Map<String, String>): PublicKey {
        val request = createRequest(
            tenantId,
            GenerateFreshKeyRpcCommand(null, context.toWire())
        )
        val response = request.execute(CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    override fun freshKey(tenantId: String, externalId: UUID, context: Map<String, String>): PublicKey {
        val request = createRequest(
            tenantId,
            GenerateFreshKeyRpcCommand(externalId.toString(), context.toWire())
        )
        val response = request.execute(CryptoPublicKey::class.java)
        return schemeMetadata.decodePublicKey(response!!.key.array())
    }

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
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

    override fun sign(
        tenantId: String,
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): DigitalSignature.WithKey {
        val request = createRequest(
            tenantId,
            SignWithSpecRpcCommand(
                ByteBuffer.wrap(schemeMetadata.encodeAsByteArray(publicKey)),
                CryptoSignatureSpec(signatureSpec.signatureName, signatureSpec.customDigestName?.name),
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

    override fun sign(tenantId: String, alias: String, data: ByteArray, context: Map<String, String>): ByteArray {
        val request = createRequest(
            tenantId,
            SignWithAliasRpcCommand(
                alias,
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.execute(CryptoSignature::class.java)
        return response!!.bytes.array()
    }

    override fun sign(
        tenantId: String,
        alias: String,
        signatureSpec: SignatureSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray {
        val request = createRequest(
            tenantId,
            SignWithAliasSpecRpcCommand(
                alias,
                CryptoSignatureSpec(signatureSpec.signatureName, signatureSpec.customDigestName?.name),
                ByteBuffer.wrap(data),
                context.toWire()
            )
        )
        val response = request.execute(CryptoSignature::class.java)
        return response!!.bytes.array()
    }

    override fun findHSMAlias(tenantId: String, alias: String): String? {
        val request = createRequest(
            tenantId,
            HSMAliasRpcQuery(alias)
        )
        return request.execute(String::class.java, allowNoContentValue = true)
    }

    override fun getHSM(tenantId: String, category: String): HSMInfo? {
        val request = createRequest(
            tenantId,
            AssignedHSMRpcQuery(category)
        )
        return request.execute(HSMInfo::class.java, allowNoContentValue = true)
    }

    private fun createRequest(tenantId: String, request: Any): RpcOpsRequest =
        RpcOpsRequest(
            createWireRequestContext<CryptoOpsPublisher>(tenantId),
            request
        )

    @Suppress("ThrowsCount", "UNCHECKED_CAST", "ComplexMethod")
    private fun <RESPONSE> RpcOpsRequest.execute(
        respClazz: Class<RESPONSE>,
        allowNoContentValue: Boolean = false
    ): RESPONSE? {
        while (true) {
            try {
                logger.info("Sending {} for member {}", request::class.java.name, context.tenantId)
                val response = sender.sendRequest(this).getOrThrow()
                require(
                    response.context.requestingComponent == context.requestingComponent &&
                            response.context.tenantId == context.tenantId
                ) {
                    "Expected ${context.tenantId} member and ${context.requestingComponent} component, but " +
                            "received ${response.response::class.java.name} with ${response.context.tenantId} member" +
                            " ${response.context.requestingComponent} component"
                }
                if (response.response::class.java == CryptoNoContentValue::class.java && allowNoContentValue) {
                    logger.debug(
                        "Received empty response for {} for member {}",
                        request::class.java.name,
                        context.tenantId
                    )
                    return null
                }
                require(response.response != null && (response.response::class.java == respClazz)) {
                    "Expected ${respClazz.name} for ${context.tenantId} member, but " +
                            "received ${response.response::class.java.name} with ${response.context.tenantId} member"
                }
                logger.debug("Received response {} for member {}", respClazz.name, context.tenantId)
                return response.response as RESPONSE
            } catch (e: CryptoServiceLibraryException) {
                logger.error("Failed executing ${request::class.java.name} for member ${context.tenantId}", e)
                throw e
            } catch (e: Throwable) {
                val message = "Failed executing ${request::class.java.name} for member ${context.tenantId}"
                logger.error(message, e)
                throw CryptoServiceLibraryException(message, e)
            }
        }
    }
}