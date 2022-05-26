package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.DefaultSignatureOIDMap
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.httprpc.v1.KeysRpcOps
import net.corda.membership.httprpc.v1.types.response.KeyMetaData
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.EDDSA_ED25519_NONE_SIGNATURE_SPEC
import net.corda.v5.crypto.GOST3410_GOST3411_SIGNATURE_SPEC
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.RSA_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SM2_SM3_SIGNATURE_SPEC
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_SHA512_SIGNATURE_SPEC
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_extensionRequest
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extension.subjectAlternativeName
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralName.dNSName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.jce.X509KeyUsage.digitalSignature
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.security.PublicKey
import javax.security.auth.x500.X500Principal

@Component(service = [PluggableRPCOps::class])
class KeysRpcOpsImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : KeysRpcOps, PluggableRPCOps<KeysRpcOps>, Lifecycle {

    private companion object {
        private val defaultCodeNameToSpec = mapOf(
            ECDSA_SECP256K1_CODE_NAME to SignatureSpec("SHA512withECDSA"),
            ECDSA_SECP256R1_CODE_NAME to SignatureSpec("SHA512withECDSA"),
            EDDSA_ED25519_TEMPLATE to EDDSA_ED25519_NONE_SIGNATURE_SPEC,
            GOST3410_GOST3411_TEMPLATE to GOST3410_GOST3411_SIGNATURE_SPEC,
            RSA_CODE_NAME to RSA_SHA512_SIGNATURE_SPEC,
            SM2_CODE_NAME to SM2_SM3_SIGNATURE_SPEC,
            SPHINCS256_CODE_NAME to SPHINCS256_SHA512_SIGNATURE_SPEC,
        )

        fun getSignatureSpec(
            key: CryptoSigningKey,
            defaultSpec: String?
        ): SignatureSpec {
            if (defaultSpec != null) {
                return SignatureSpec(defaultSpec)
            }

            return defaultCodeNameToSpec[key.schemeCodeName]
                ?: throw ResourceNotFoundException("Can not find any spec for ${key.schemeCodeName}. Use signatureSpec explicitly")
        }
    }

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
        keyId: String,
    ): String {
        val key = cryptoOpsClient.lookup(
            tenantId = tenantId,
            ids = listOf(keyId)
        ).firstOrNull() ?: throw ResourceNotFoundException("Can not find any key with ID $keyId for $tenantId")

        val publicKey = keyEncodingService.decodePublicKey(key.publicKey.array())
        return keyEncodingService.encodeAsString(publicKey)
    }

    override fun generateCsr(
        tenantId: String,
        keyId: String,
        x500name: String,
        certificateRole: String,
        subjectAlternativeNames: List<String>?,
        signatureSpec: String?,
        contextMap: Map<String, String?>?,
    ): String {
        if (contextMap != null && contextMap.isNotEmpty()) {
            throw InvalidInputDataException("contextMap for $certificateRole can not hold any content")
        }
        val key = cryptoOpsClient.lookup(
            tenantId = tenantId,
            ids = listOf(keyId)
        ).firstOrNull() ?: throw ResourceNotFoundException("Can not find any key with ID $keyId for $tenantId")
        val publicKey = keyEncodingService.decodePublicKey(key.publicKey.array())

        val extensionsGenerator = ExtensionsGenerator()
        extensionsGenerator.addExtension(
            Extension.keyUsage, true, KeyUsage(digitalSignature)
        )
        subjectAlternativeNames?.forEach { name ->
            val altName = GeneralName(dNSName, name)
            val subjectAltName = GeneralNames(altName)
            extensionsGenerator.addExtension(subjectAlternativeName, true, subjectAltName)
        }

        val spec = getSignatureSpec(key, signatureSpec)

        val signer = CsrContentSigner(spec, publicKey, tenantId)

        val p10Builder = JcaPKCS10CertificationRequestBuilder(
            X500Principal(x500name), publicKey
        )

        p10Builder
            .addAttribute(pkcs_9_at_extensionRequest, extensionsGenerator.generate())

        val csr = p10Builder.build(signer)

        return StringWriter().use {
            JcaPEMWriter(it).use { jcaPEMWriter ->
                jcaPEMWriter.writeObject(csr)
            }
            it.toString()
        }
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

    private inner class CsrContentSigner(
        private val signatureSpec: SignatureSpec,
        private val publicKey: PublicKey,
        private val tenantId: String,
    ) : ContentSigner {
        private val outputStream = ByteArrayOutputStream()

        override fun getAlgorithmIdentifier(): AlgorithmIdentifier {
            return DefaultSignatureOIDMap.inferSignatureOID(publicKey, signatureSpec)
                ?: throw ResourceNotFoundException("Can not find algorithm identifier for ${signatureSpec.signatureName}")
        }

        override fun getOutputStream() = outputStream

        override fun getSignature(): ByteArray {
            return cryptoOpsClient.sign(
                tenantId = tenantId,
                publicKey = publicKey,
                signatureSpec = signatureSpec,
                outputStream.toByteArray(),
            ).bytes
        }
    }
}
