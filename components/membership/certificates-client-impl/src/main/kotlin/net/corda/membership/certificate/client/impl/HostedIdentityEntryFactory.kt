package net.corda.membership.certificate.client.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException
import net.corda.messaging.api.records.Record
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.StringWriter
import java.security.cert.CertificateFactory

internal class HostedIdentityEntryFactory(
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val cryptoOpsClient: CryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val retrieveCertificates: (String, String) -> String?
) {

    private fun getNode(holdingIdentityId: String): VirtualNodeInfo {
        return virtualNodeInfoReadService.getById(holdingIdentityId)
            ?: throw CertificatesResourceNotFoundException("No node with ID $holdingIdentityId")
    }

    private fun getKey(
        holdingIdentityId: String,
        sessionKeyId: String?,
    ): String {
        val sessionKey = if (sessionKeyId != null) {
            cryptoOpsClient.lookup(
                tenantId = holdingIdentityId,
                ids = listOf(sessionKeyId)
            )
        } else {
            cryptoOpsClient.lookup(
                tenantId = holdingIdentityId,
                0,
                1,
                CryptoKeyOrderBy.NONE,
                mapOf(CryptoConsts.SigningKeyFilters.CATEGORY_FILTER to CryptoConsts.Categories.SESSION_INIT,),
            )
        }.firstOrNull()
            ?: throw CertificatesResourceNotFoundException("Can not find session key for $holdingIdentityId")

        val sessionPublicKey = keyEncodingService.decodePublicKey(sessionKey.publicKey.array())
        return keyEncodingService.encodeAsString(sessionPublicKey)
    }

    private fun getCertificates(
        actualTlsTenantId: String,
        certificateChainAlias: String,
    ): List<String> {
        val certificateChain = retrieveCertificates(actualTlsTenantId, certificateChainAlias)
            ?: throw CertificatesResourceNotFoundException(
                "Please import certificate chain into $actualTlsTenantId with alias $certificateChainAlias"
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
        holdingIdentityId: String,
        certificateChainAlias: String,
        tlsTenantId: String?,
        sessionKeyId: String?,
    ): Record<String, HostedIdentityEntry> {

        val nodeInfo = getNode(holdingIdentityId)
        val sessionPublicKey = getKey(holdingIdentityId, sessionKeyId)
        val actualTlsTenantId = tlsTenantId ?: holdingIdentityId
        val tlsCertificates = getCertificates(
            actualTlsTenantId, certificateChainAlias,
        )
        validateCertificates(actualTlsTenantId, tlsCertificates)

        val hostedIdentityEntry = HostedIdentityEntry.newBuilder()
            .setHoldingIdentity(nodeInfo.holdingIdentity.toAvro())
            .setSessionKeyTenantId(holdingIdentityId)
            .setSessionPublicKey(sessionPublicKey)
            .setTlsCertificates(tlsCertificates)
            .setTlsTenantId(actualTlsTenantId)
            .build()
        return Record(
            topic = Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
            key = "${nodeInfo.holdingIdentity.x500Name}-${nodeInfo.holdingIdentity.groupId}",
            value = hostedIdentityEntry,
        )
    }

    @Suppress("ThrowsCount", "ForbiddenComment")
    private fun validateCertificates(tenantId: String, tlsCertificates: List<String>) {
        val firstCertificate = tlsCertificates.firstOrNull()
            ?: throw CordaRuntimeException("No certificate")

        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(firstCertificate.byteInputStream())

        val publicKey = certificate.publicKey
        cryptoOpsClient.filterMyKeys(tenantId, listOf(publicKey))
            .firstOrNull()
            ?: throw CordaRuntimeException("This certificate public key is unknown to $tenantId")

        // TODO: verify certificate with the root certificate public key
    }
}
