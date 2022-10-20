package net.corda.membership.certificate.client.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.SessionPkiMode.NO_PKI
import net.corda.messaging.api.records.Record
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter
import java.security.InvalidKeyException
import java.security.cert.CertificateFactory

internal class HostedIdentityEntryFactory(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val cryptoOpsClient: CryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val groupPolicyProvider: GroupPolicyProvider,
    private val retrieveCertificates: (String, String) -> String?,
) {
    private companion object {
        val logger = contextLogger()
    }

    private fun getNode(holdingIdentityShortHash: ShortHash): VirtualNodeInfo {
        return virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
            ?: throw CertificatesResourceNotFoundException("No node with ID $holdingIdentityShortHash")
    }

    private fun getKey(
        tenantId: String,
        sessionKeyId: String?,
    ): String {
        val sessionKey = if (sessionKeyId != null) {
            cryptoOpsClient.lookup(
                tenantId = tenantId,
                ids = listOf(sessionKeyId)
            )
        } else {
            cryptoOpsClient.lookup(
                tenantId = tenantId,
                0,
                1,
                CryptoKeyOrderBy.NONE,
                mapOf(CryptoConsts.SigningKeyFilters.CATEGORY_FILTER to CryptoConsts.Categories.SESSION_INIT,),
            )
        }.firstOrNull()
            ?: throw CertificatesResourceNotFoundException("Can not find session key for $tenantId")

        val sessionPublicKey = keyEncodingService.decodePublicKey(sessionKey.publicKey.array())
        return keyEncodingService.encodeAsString(sessionPublicKey)
    }

    private fun getCertificates(
        actualTenantId: String,
        certificateChainAlias: String,
    ): List<String> {
        val certificateChain = retrieveCertificates(actualTenantId, certificateChainAlias)
            ?: throw CertificatesResourceNotFoundException(
                "Please import certificate chain into $actualTenantId with alias $certificateChainAlias"
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
    @Suppress("LongParameterList")
    fun createIdentityRecord(
        holdingIdentityShortHash: ShortHash,
        tlsCertificateChainAlias: String,
        tlsTenantId: String?,
        sessionKeyTenantId: String?,
        sessionKeyId: String?,
        sessionCertificateChainAlias: String?
    ): Record<String, HostedIdentityEntry> {
        val nodeInfo = getNode(holdingIdentityShortHash)
        val actualSessionKeyTenantId = sessionKeyTenantId ?: holdingIdentityShortHash.toString()
        val sessionPublicKey = getKey(actualSessionKeyTenantId, sessionKeyId)
        val actualTlsTenantId = tlsTenantId ?: holdingIdentityShortHash.toString()
        val tlsCertificates = getCertificates(
            actualTlsTenantId,
            tlsCertificateChainAlias,
        )

        val policy = try {
            groupPolicyProvider.getGroupPolicy(nodeInfo.holdingIdentity)
        } catch (e: IllegalStateException) {
            logger.warn("Could not retrieve group policy for validating TLS trust root certificates.", e)
            null
        } ?: throw CordaRuntimeException("No group policy file found for holding identity ID [${nodeInfo.holdingIdentity.shortHash}].")
        validateCertificates(
            actualTlsTenantId,
            nodeInfo.holdingIdentity,
            tlsCertificates,
            CertificateType.TlsCertificate(policy.p2pParameters)
        )
        if (policy.p2pParameters.sessionPki != NO_PKI && sessionCertificateChainAlias == null) {
            throw CordaRuntimeException("The sessionCertificateChainAlias must be specified when using a group policy with sessionPki: " +
                    policy.p2pParameters.sessionPki.name
            )
        }
        val sessionCertificate = sessionCertificateChainAlias?.let {
            getCertificates(actualSessionKeyTenantId, sessionCertificateChainAlias)
        }
        sessionCertificate?.let {
            validateCertificates(
                actualSessionKeyTenantId,
                nodeInfo.holdingIdentity,
                it,
                CertificateType.SessionCertificate(policy.p2pParameters)
            )
        }

        val hostedIdentityBuilder = HostedIdentityEntry.newBuilder()
            .setHoldingIdentity(nodeInfo.holdingIdentity.toAvro())
            .setSessionKeyTenantId(actualSessionKeyTenantId)
            .setSessionPublicKey(sessionPublicKey)
            .setTlsCertificates(tlsCertificates)
            .setTlsTenantId(actualTlsTenantId)
        sessionCertificate?.let { hostedIdentityBuilder.setSessionCertificates(it) }
        return Record(
            topic = Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
            key = nodeInfo.holdingIdentity.shortHash.value,
            value = hostedIdentityBuilder.build(),
        )
    }

    private sealed class CertificateType(val parameterName: String, val trustRoots: Collection<String>?) {
        data class TlsCertificate(val p2PParameters: GroupPolicy.P2PParameters):
            CertificateType("tlsTrustRoots", p2PParameters.tlsTrustRoots)
        data class SessionCertificate(val p2PParameters: GroupPolicy.P2PParameters):
            CertificateType("sessionTrustRoots", p2PParameters.sessionTrustRoots)
    }

    @Suppress("ThrowsCount")
    private fun validateCertificates(
        tenantId: String,
        holdingIdentity: HoldingIdentity,
        certificates: List<String>,
        certificateType: CertificateType
    ) {
        val firstCertificate = certificates.firstOrNull()
            ?: throw CordaRuntimeException("No certificate")

        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(firstCertificate.byteInputStream())

        val publicKey = certificate.publicKey
        cryptoOpsClient.filterMyKeys(tenantId, listOf(publicKey))
            .firstOrNull()
            ?: throw CordaRuntimeException("This certificate public key is unknown to $tenantId")

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
                }
            }.firstOrNull()
            ?: throw CordaRuntimeException(
                "The ${CertificateType::class.java.simpleName} was not signed by the correct certificate authority"
            )
    }
}
