package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.crypto.wire.CryptoSigningKey
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
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.publicKeyId
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_emailAddress
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_extensionRequest
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extension.extendedKeyUsage
import org.bouncycastle.asn1.x509.Extension.subjectAlternativeName
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralName.dNSName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
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
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata,
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
        emailAddress: String,
        keyUsageExtension: String?,
        subjectAlternativeNames: Collection<String>?,
    ): String {
        val key = cryptoOpsClient.lookup(
            tenantId = tenantId,
            ids = listOf(keyId)
        ).firstOrNull() ?: throw ResourceNotFoundException("Can not find any key with ID $keyId for $tenantId")
        val publicKey = keyEncodingService.decodePublicKey(key.publicKey.array())

        val extensionsGenerator = ExtensionsGenerator()
        extensionsGenerator.addExtension(
            Extension.keyUsage, true, KeyUsage(digitalSignature)
        )
        val purpose = if (keyUsageExtension == null) {
            KeyPurposeId.id_kp_serverAuth
        } else {
            KeyPurposeId.getInstance(ASN1ObjectIdentifier(keyUsageExtension))
        }
        extensionsGenerator.addExtension(
            extendedKeyUsage,
            true,
            ExtendedKeyUsage(
                arrayOf(
                    purpose
                )
            )
        )
        subjectAlternativeNames?.forEach { name ->
            val altName = GeneralName(dNSName, name)
            val subjectAltName = GeneralNames(altName)
            extensionsGenerator.addExtension(subjectAlternativeName, true, subjectAltName)
        }

        val signer = CsrContentSigner(key, publicKey)

        val p10Builder = JcaPKCS10CertificationRequestBuilder(
            X500Principal(x500name), publicKey
        )

        p10Builder
            .addAttribute(pkcs_9_at_emailAddress, DERUTF8String(emailAddress))
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
        private val cryptoSigningKey: CryptoSigningKey,
        private val publicKey: PublicKey,
    ) : ContentSigner {
        private val outputStream = ByteArrayOutputStream()
        override fun getAlgorithmIdentifier(): AlgorithmIdentifier {
            val scheme = cipherSchemeMetadata.schemes.firstOrNull {
                it.codeName == cryptoSigningKey.schemeCodeName
            } ?: throw ResourceNotFoundException("Can not find any schema ${cryptoSigningKey.schemeCodeName}")
            return scheme.signatureSpec.signatureOID
                ?: throw ResourceNotFoundException("Can not find algorithm identifier for ${scheme.signatureSpec.signatureName}")
        }

        override fun getOutputStream() = outputStream

        override fun getSignature(): ByteArray {
            return cryptoOpsClient.sign(
                tenantId = cryptoSigningKey.tenantId,
                publicKey = publicKey,
                outputStream.toByteArray()
            ).bytes
        }
    }
}
