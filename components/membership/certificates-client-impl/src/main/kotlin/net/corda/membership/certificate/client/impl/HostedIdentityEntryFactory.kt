package net.corda.membership.certificate.client.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoTenants.P2P
import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.messaging.api.records.Record
import net.corda.rest.exception.BadRequestException
import net.corda.schema.Schemas
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.security.InvalidKeyException
import java.security.SignatureException
import java.security.cert.CertificateFactory

@Suppress("LongParameterList")
internal class HostedIdentityEntryFactory(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val cryptoOpsClient: CryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val mtlsMgmClientCertificateKeeper: MtlsMgmClientCertificateKeeper,
    private val retrieveCertificates: (ShortHash?, CertificateUsage, String) -> String?,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun getNode(holdingIdentityShortHash: ShortHash): VirtualNodeInfo {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
            ?: throw CertificatesResourceNotFoundException("No node with ID $holdingIdentityShortHash")
    }

    private fun getSessionKey(
        tenantId: String,
        sessionKeyId: ShortHash,
    ): String {
        return cryptoOpsClient.lookupKeysByIds(
            tenantId = tenantId,
            keyIds = listOf(sessionKeyId)
        ).firstOrNull()
            ?.toPem()
            ?: throw CertificatesResourceNotFoundException("Can not find session key for $tenantId")
    }

    private fun CryptoSigningKey.toPem(): String {
        return keyEncodingService.encodeAsString(
            keyEncodingService.decodePublicKey(
                this.publicKey.array()
            )
        )
    }

    private fun getCertificates(
        certificateHoldingId: ShortHash?,
        usage: CertificateUsage,
        certificateChainAlias: String,
    ): List<String> {
        val certificateChain = retrieveCertificates(certificateHoldingId, usage, certificateChainAlias)
            ?: throw CertificatesResourceNotFoundException(
                "Please import certificate chain with usage \"${usage.publicName}\" and alias $certificateChainAlias"
            )
        return certificateChain.reader().use { reader ->
            PEMParser(reader).use {
                generateSequence { it.readObject() }
                    .filterIsInstance<X509CertificateHolder>()
                    .map { certificate ->
                        StringWriter().use { str ->
                            JcaPEMWriter(str).use { writer ->
                                writer.writeObject(certificate)
                            }
                            str.toString()
                        }
                    }
                    .toList()
            }
        }
    }

    fun createIdentityRecord(
        holdingIdentityShortHash: ShortHash,
        tlsCertificateChainAlias: String,
        useClusterLevelTlsCertificateAndKey: Boolean,
        preferredSessionKeyAndCertificate: CertificatesClient.SessionKeyAndCertificate,
        alternativeSessionKeyAndCertificates: Collection<CertificatesClient.SessionKeyAndCertificate>,
    ): Record<String, HostedIdentityEntry> {
        val nodeInfo = getNode(holdingIdentityShortHash)
        val policy = try {
            groupPolicyProvider.getGroupPolicy(nodeInfo.holdingIdentity)
        } catch (e: IllegalStateException) {
            logger.warn("Could not retrieve group policy for validating TLS trust root certificates.", e)
            null
        } ?: throw CordaRuntimeException("No group policy file found for holding identity ID [${nodeInfo.holdingIdentity.shortHash}].")
        val avroPreferredSessionKey = buildHostedIdentitySessionKey(
            preferredSessionKeyAndCertificate,
            holdingIdentityShortHash,
            nodeInfo,
            policy
        )

        val avroAlternativeSessionKeys = alternativeSessionKeyAndCertificates.map { sessionKey ->
            buildHostedIdentitySessionKey(
                sessionKey,
                holdingIdentityShortHash,
                nodeInfo,
                policy
            )
        }
        val (tlsKeyTenantId, tlsCertificateHoldingId) = if (useClusterLevelTlsCertificateAndKey) {
            P2P to null
        } else {
            holdingIdentityShortHash.value to holdingIdentityShortHash
        }
        val tlsCertificates = getAndValidateTlsCertificate(
            tlsCertificateHoldingId,
            tlsCertificateChainAlias,
            tlsKeyTenantId,
            nodeInfo,
            policy,
        )
        mtlsMgmClientCertificateKeeper.addMgmCertificateSubjectToGroupPolicy(
            nodeInfo.holdingIdentity,
            policy,
            tlsCertificates.first(),
        )

        val hostedIdentityBuilder = HostedIdentityEntry.newBuilder()
            .setHoldingIdentity(nodeInfo.holdingIdentity.toAvro())
            .setTlsCertificates(tlsCertificates)
            .setTlsTenantId(tlsKeyTenantId)
            .setPreferredSessionKeyAndCert(avroPreferredSessionKey)
            .setAlternativeSessionKeysAndCerts(avroAlternativeSessionKeys)
        return Record(
            topic = Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
            key = nodeInfo.holdingIdentity.shortHash.value,
            value = hostedIdentityBuilder.build(),
        )
    }

    private fun buildHostedIdentitySessionKey(
        sessionKeyAndCertificate: CertificatesClient.SessionKeyAndCertificate,
        sessionCertificateHoldingId: ShortHash,
        nodeInfo: VirtualNodeInfo,
        policy: GroupPolicy,
    ): HostedIdentitySessionKeyAndCert {
        val sessionCertificate = getAndValidateSessionCertificate(
            sessionCertificateHoldingId,
            sessionKeyAndCertificate.sessionCertificateChainAlias,
            nodeInfo,
            policy
        )
        return HostedIdentitySessionKeyAndCert.newBuilder()
            .setSessionPublicKey(
                getSessionKey(
                    sessionCertificateHoldingId.value,
                    sessionKeyAndCertificate.sessionKeyId,
                )
            )
            .setSessionCertificates(sessionCertificate)
            .build()
    }

    private fun getAndValidateSessionCertificate(
        sessionCertificateHoldingId: ShortHash,
        sessionCertificateChainAlias: String?,
        nodeInfo: VirtualNodeInfo,
        policy: GroupPolicy,
    ): List<String>? {
        if (policy.p2pParameters.sessionPki != NO_PKI && sessionCertificateChainAlias == null) {
            throw BadRequestException(
                "The sessionCertificateChainAlias must be specified when using a group policy with sessionPki: " +
                    policy.p2pParameters.sessionPki.name
            )
        }
        val sessionCertificate = sessionCertificateChainAlias?.run {
            val certificate = getCertificates(
                sessionCertificateHoldingId,
                CertificateUsage.P2P_SESSION,
                sessionCertificateChainAlias
            )
            validateCertificates(
                sessionCertificateHoldingId.value,
                nodeInfo.holdingIdentity,
                certificate,
                CertificateType.SessionCertificate(policy.p2pParameters)
            )
            certificate
        }
        return sessionCertificate
    }

    private fun getAndValidateTlsCertificate(
        tlsCertificateHoldingId: ShortHash?,
        tlsCertificateChainAlias: String,
        tlsKeyTenantId: String,
        nodeInfo: VirtualNodeInfo,
        policy: GroupPolicy
    ): List<String> {
        val tlsCertificates = getCertificates(
            tlsCertificateHoldingId,
            CertificateUsage.P2P_TLS,
            tlsCertificateChainAlias,
        )
        validateCertificates(
            tlsKeyTenantId,
            nodeInfo.holdingIdentity,
            tlsCertificates,
            CertificateType.TlsCertificate(policy.p2pParameters)
        )
        return tlsCertificates
    }

    private sealed class CertificateType(
        val parameterName: String,
        val trustRoots: Collection<String>?,
        val type: Type
    ) {
        data class TlsCertificate(val p2PParameters: GroupPolicy.P2PParameters) :
            CertificateType("tlsTrustRoots", p2PParameters.tlsTrustRoots, Type.TLS)
        data class SessionCertificate(val p2PParameters: GroupPolicy.P2PParameters) :
            CertificateType("sessionTrustRoots", p2PParameters.sessionTrustRoots, Type.SESSION)
        enum class Type(val label: String) {
            TLS("TLS"),
            SESSION("Session")
        }
    }

    @Suppress("ThrowsCount")
    private fun validateCertificates(
        keyTenantId: String,
        holdingIdentity: HoldingIdentity,
        certificates: List<String>,
        certificateType: CertificateType
    ) {
        val firstCertificate = certificates.firstOrNull()
            ?: throw CordaRuntimeException("No certificate")

        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(firstCertificate.byteInputStream())

        val publicKey = certificate.publicKey
        cryptoOpsClient.filterMyKeys(keyTenantId, listOf(publicKey))
            .firstOrNull()
            ?: throw CordaRuntimeException("This certificate public key is unknown to $keyTenantId")

        if (certificateType.trustRoots == null) {
            throw CordaRuntimeException("The group ${holdingIdentity.groupId} P2P parameters ${certificateType.parameterName} is null")
        }
        if (certificateType.trustRoots.isEmpty()) {
            throw CordaRuntimeException("The group ${holdingIdentity.groupId} P2P parameters ${certificateType.parameterName} is empty")
        }
        certificateType.trustRoots
            .asSequence()
            .map { rootCertificateStr ->
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(rootCertificateStr.byteInputStream())
            }.filter { rootCertificate ->
                try {
                    certificate.verify(rootCertificate.publicKey)
                    true
                } catch (e: InvalidKeyException) {
                    false
                } catch (e: SignatureException) {
                    false
                }
            }.firstOrNull() ?: throw BadRequestException(
            "The ${certificateType.type.label} certificate was not signed by the correct certificate authority"
        )
    }
}
