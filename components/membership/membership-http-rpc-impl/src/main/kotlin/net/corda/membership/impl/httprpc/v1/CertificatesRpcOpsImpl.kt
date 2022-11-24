package net.corda.membership.impl.httprpc.v1

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.DefaultSignatureOIDMap
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.httprpc.v1.CertificatesRpcOps
import net.corda.membership.httprpc.v1.CertificatesRpcOps.Companion.SIGNATURE_SPEC
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.ofOrThrow
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
import java.security.cert.CertificateFactory
import javax.security.auth.x500.X500Principal

@Component(service = [PluggableRPCOps::class])
class CertificatesRpcOpsImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CertificatesClient::class)
    private val certificatesClient: CertificatesClient,
) : CertificatesRpcOps, PluggableRPCOps<CertificatesRpcOps>, Lifecycle {

    private companion object {
        private val logger = contextLogger()

        private val defaultCodeNameToSpec = mapOf(
            ECDSA_SECP256K1_CODE_NAME to SignatureSpec.ECDSA_SHA256,
            ECDSA_SECP256R1_CODE_NAME to SignatureSpec.ECDSA_SHA256,
            EDDSA_ED25519_TEMPLATE to SignatureSpec.EDDSA_ED25519,
            GOST3410_GOST3411_TEMPLATE to SignatureSpec.GOST3410_GOST3411,
            RSA_CODE_NAME to SignatureSpec.RSA_SHA512,
            SM2_CODE_NAME to SignatureSpec.SM2_SM3,
            SPHINCS256_CODE_NAME to SignatureSpec.SPHINCS256_SHA512,
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

    override fun generateCsr(
        tenantId: String,
        keyId: String,
        x500Name: String,
        subjectAlternativeNames: List<String>?,
        contextMap: Map<String, String?>?,
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
        subjectAlternativeNames?.forEach { name ->
            val altName = GeneralName(dNSName, name)
            val subjectAltName = GeneralNames(altName)
            extensionsGenerator.addExtension(subjectAlternativeName, true, subjectAltName)
        }
        val signatureSpec = contextMap?.get(SIGNATURE_SPEC)

        val spec = getSignatureSpec(key, signatureSpec)

        val signer = CsrContentSigner(spec, publicKey, tenantId)

        val p10Builder = JcaPKCS10CertificationRequestBuilder(
            X500Principal(x500Name), publicKey
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

    override fun importCertificateChain(
        usage: String,
        holdingIdentityId: String?,
        alias: String,
        certificates: List<HttpFileUpload>,
    ) {
        if (alias.isBlank()) {
            throw InvalidInputDataException(
                details = mapOf("alias" to "Empty alias")
            )
        }
        // validate certificate
        if (certificates.isEmpty()) {
            throw InvalidInputDataException(
                details = mapOf("certificate" to "No certificates")
            )
        }
        val holdingIdentityShortHash = if (holdingIdentityId != null) {
            ShortHash.ofOrThrow(holdingIdentityId)
        } else {
            null
        }
        val usageType = CertificateUsage.values().firstOrNull {
            it.publicName.equals(usage.trim(), ignoreCase = true)
        } ?: throw InvalidInputDataException(
            details = mapOf("usage" to "Unknown usage: $usage")
        )
        val rawCertificates = certificates.map {
            it.content.reader().readText()
        }
        try {
            rawCertificates.forEach { rawCertificate ->
                if (CertificateFactory
                    .getInstance("X.509")
                    .generateCertificates(rawCertificate.byteInputStream()).isEmpty()
                ) {
                    throw InvalidInputDataException(
                        "No certificates in PEM"
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Invalid certificate", e)
            throw InvalidInputDataException(
                details = mapOf("certificate" to "Not a valid certificate: ${e.message}")
            )
        }
        try {
            certificatesClient.importCertificates(
                usageType,
                holdingIdentityShortHash,
                alias,
                rawCertificates.joinToString(separator = "\n"),
            )
        } catch (e: Exception) {
            logger.warn("Could not import certificate", e)
            throw InternalServerException("Could not import certificate: ${e.message}")
        }
    }

    override fun getCertificateAliases(usage: String, holdingIdentityId: String?): List<String> {
        val holdingIdentityShortHash = if (holdingIdentityId != null) {
            ShortHash.ofOrThrow(holdingIdentityId)
        } else {
            null
        }
        val usageType = CertificateUsage.values().firstOrNull {
            it.publicName.equals(usage.trim(), ignoreCase = true)
        } ?: throw InvalidInputDataException(
            details = mapOf("usage" to "Unknown usage: $usage")
        )
        return try {
            certificatesClient.getCertificateAliases(
                usageType,
                holdingIdentityShortHash,
            ).toList()
        } catch (e: Exception) {
            logger.warn("Could not get certificate aliases", e)
            throw InternalServerException("Could not get certificate aliases: ${e.message}")
        }
    }

    override fun getCertificateChain(usage: String, holdingIdentityId: String?, alias: String): String {
        if (alias.isBlank()) {
            throw InvalidInputDataException(
                details = mapOf("alias" to "Empty alias")
            )
        }
        val holdingIdentityShortHash = if (holdingIdentityId != null) {
            ShortHash.ofOrThrow(holdingIdentityId)
        } else {
            null
        }
        val usageType = CertificateUsage.values().firstOrNull {
            it.publicName.equals(usage.trim(), ignoreCase = true)
        } ?: throw InvalidInputDataException(
            details = mapOf("usage" to "Unknown usage: $usage")
        )
        return try {
            certificatesClient.retrieveCertificates(
                holdingIdentityShortHash,
                usageType,
                alias
            ) ?: throw ResourceNotFoundException(alias, "alias")
        } catch (e: ResourceNotFoundException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Could not get certificate aliases", e)
            throw InternalServerException("Could not get certificate aliases: ${e.message}")
        }
    }

    override val targetInterface = CertificatesRpcOps::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<CertificatesRpcOps>(
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
            LifecycleCoordinatorName.forComponent<CertificatesClient>(),
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
