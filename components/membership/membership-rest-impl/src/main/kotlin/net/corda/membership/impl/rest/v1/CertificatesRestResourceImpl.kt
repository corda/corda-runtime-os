package net.corda.membership.impl.rest.v1

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.DefaultSignatureOIDMap
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.rest.HttpFileUpload
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.messagebus.MessageBusUtils.tryWithExceptionHandling
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.rest.v1.CertificatesRestResource
import net.corda.membership.rest.v1.CertificatesRestResource.Companion.SIGNATURE_SPEC
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.read.rpc.extensions.createKeyIdOrHttpThrow
import net.corda.virtualnode.read.rpc.extensions.getByHoldingIdentityShortHashOrThrow
import net.corda.virtualnode.read.rpc.extensions.ofOrThrow
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.apache.commons.validator.routines.InetAddressValidator
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.pkcs_9_at_extensionRequest
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extension.subjectAlternativeName
import org.bouncycastle.asn1.x509.ExtensionsGenerator
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralName.dNSName
import org.bouncycastle.asn1.x509.GeneralName.iPAddress
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.jce.X509KeyUsage.digitalSignature
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

@Component(service = [PluggableRestResource::class])
class CertificatesRestResourceImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CertificatesClient::class)
    private val certificatesClient: CertificatesClient,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : CertificatesRestResource, PluggableRestResource<CertificatesRestResource>, Lifecycle {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

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
        val key = tryWithExceptionHandling(logger, "find key with ID $keyId for $tenantId") {
            cryptoOpsClient.lookupKeysByIds(
                tenantId = tenantId,
                keyIds = listOf(createKeyIdOrHttpThrow(keyId))
            )
        }.firstOrNull() ?: throw ResourceNotFoundException("Can not find any key with ID $keyId for $tenantId")
        val principal = when (key.category) {
            CryptoConsts.Categories.SESSION_INIT -> validateSessionCertificateSubject(
                tenantId,
                x500Name,
            )
            CryptoConsts.Categories.TLS -> {
                validateNodeSessionCertificateSubject(x500Name).x500Principal
            }
            else -> {
                validateX500Name(x500Name)
            }
        }
        val publicKey = keyEncodingService.decodePublicKey(key.publicKey.array())

        val extensionsGenerator = ExtensionsGenerator()
        extensionsGenerator.addExtension(
            Extension.keyUsage, true, KeyUsage(digitalSignature)
        )
        subjectAlternativeNames?.forEach { name ->
            if (InetAddressValidator.getInstance().isValid(name)) {
                val altName = GeneralName(iPAddress, name)
                val subjectAltName = GeneralNames(altName)
                extensionsGenerator.addExtension(subjectAlternativeName, true, subjectAltName)
            } else {
                val altName = GeneralName(dNSName, name)
                val subjectAltName = GeneralNames(altName)
                extensionsGenerator.addExtension(subjectAlternativeName, true, subjectAltName)
            }
        }
        val signatureSpec = contextMap?.get(SIGNATURE_SPEC)

        val spec = getSignatureSpec(key, signatureSpec)

        val signer = CsrContentSigner(spec, publicKey, tenantId)

        val p10Builder = JcaPKCS10CertificationRequestBuilder(
            principal, publicKey
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
        val x509Certificates = try {
            rawCertificates.flatMap { rawCertificate ->
                CertificateFactory
                    .getInstance("X.509")
                    .generateCertificates(rawCertificate.byteInputStream())
            }
        } catch (e: Exception) {
            logger.warn("Invalid certificate", e)
            throw InvalidInputDataException(
                details = mapOf("certificate" to "Not a valid certificate: ${e.message}")
            )
        }.filterIsInstance<X509Certificate>()

        if (x509Certificates.isEmpty()) {
            throw InvalidInputDataException(
                "No certificates in PEM"
            )
        }
        if (usageType == CertificateUsage.P2P_SESSION) {
            if (holdingIdentityShortHash == null) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityId" to
                            "P2P Session certificate can only be imported to holding identity."
                    )
                )
            }
            val node =
                virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
                    ?: throw InvalidInputDataException(
                        details = mapOf(
                            "holdingIdentityId" to
                                "Can not find virtual node $holdingIdentityShortHash."
                        )
                    )

            val firstCertificate = x509Certificates.first()
            val subject = try {
                MemberX500Name.build(firstCertificate.subjectX500Principal)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "certificate" to
                            "The X500 name of the certificate is not a valid Corda X500 name: ${e.message}."
                    )
                )
            }
            if (subject != node.holdingIdentity.x500Name) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "certificate" to
                            "The session certificate subject must be the same as the member name."
                    )
                )
            }
        }

        tryWithExceptionHandling(logger, "import certificate") {
            certificatesClient.importCertificates(
                usageType,
                holdingIdentityShortHash,
                alias,
                rawCertificates.joinToString(separator = "\n"),
            )
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

        return tryWithExceptionHandling(logger, "get certificate aliases") {
            certificatesClient.getCertificateAliases(
                usageType,
                holdingIdentityShortHash,
            )
        }.toList()
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

        return tryWithExceptionHandling(logger, "get certificate chain") {
            certificatesClient.retrieveCertificates(
                holdingIdentityShortHash,
                usageType,
                alias
            )
        } ?: throw ResourceNotFoundException(alias, "alias")
    }

    override val targetInterface = CertificatesRestResource::class.java

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<CertificatesRestResource>(
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

    private val lifecycleHandler = RestResourceLifecycleHandler(
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

    private fun validateX500Name(x500Name: String): X500Principal {
        return try {
            X500Principal(x500Name)
        } catch (e: IllegalArgumentException) {
            throw InvalidInputDataException(
                "The X500 name of the certificate is invalid: ${e.message}.",
                mapOf("x500Name" to x500Name)
            )
        }
    }
    private fun validateNodeSessionCertificateSubject(x500Name: String): MemberX500Name {
        return try {
            MemberX500Name.parse(x500Name)
        } catch (e: IllegalArgumentException) {
            throw InvalidInputDataException(
                "The X500 name of the certificate is not a valid Corda X500 name: ${e.message}.",
                mapOf("x500Name" to x500Name)
            )
        }
    }

    private fun validateSessionCertificateSubject(
        tenantId: String,
        x500Name: String,
    ): X500Principal {
        val name = validateNodeSessionCertificateSubject(x500Name)
        if (tenantId == P2P) {
            val exists = virtualNodeInfoReadService.getAll().any {
                it.holdingIdentity.x500Name == name
            }
            if (!exists) {
                throw InvalidInputDataException(
                    "Can not generate cluster session certificate with subject $name. No virtual node with that name.",
                    mapOf("x500Name" to x500Name)
                )
            }
        } else {
            val nodeId = ShortHash.parseOrThrow(tenantId)
            val node = virtualNodeInfoReadService.getByHoldingIdentityShortHashOrThrow(nodeId)
            if (node.holdingIdentity.x500Name != name) {
                throw InvalidInputDataException(
                    "Can not generate session certificate for ${node.holdingIdentity.x500Name} with subject $name.",
                    mapOf("x500Name" to x500Name)
                )
            }
        }
        return name.x500Principal
    }
}
