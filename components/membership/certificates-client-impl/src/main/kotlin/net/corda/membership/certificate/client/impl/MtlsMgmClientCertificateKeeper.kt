package net.corda.membership.certificate.client.impl

import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType.MUTUAL
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PropertyKeys.MGM_CLIENT_CERTIFICATE_SUBJECT
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal class MtlsMgmClientCertificateKeeper(
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val membershipPersistenceClient : MembershipPersistenceClient,
    private val membershipQueryClient : MembershipQueryClient,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    private val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509"),
) {
    fun addMgmCertificateSubjectToGroupPolicy(
        holdingIdentity: HoldingIdentity,
        groupPolicy: GroupPolicy,
        pemTlsCertificates: String,
    ) {
        if(groupPolicy.p2pParameters.tlsType != MUTUAL) {
            return
        }
        val groupReader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)
        val member = groupReader.lookup(holdingIdentity.x500Name) ?: return
        if (!member.isMgm) {
            return
        }
        val subject = pemTlsCertificates.byteInputStream().use {
            certificateFactory.generateCertificates(it)
        }.filterIsInstance<X509Certificate>()
            .firstOrNull()?.subjectX500Principal ?:
        throw CordaRuntimeException("Can not extract TLS certificate subject.")
        val normalizedSubject = MemberX500Name.parse(subject.toString()).toString()
        val (persistedGroupPolicy, version) = membershipQueryClient
            .queryGroupPolicy(holdingIdentity)
            .getOrThrow()

        val newGroupPolicy = persistedGroupPolicy.entries.associate { it.key to it.value } +
                (MGM_CLIENT_CERTIFICATE_SUBJECT to normalizedSubject)

        membershipPersistenceClient.persistGroupPolicy(
            holdingIdentity,
            layeredPropertyMapFactory.createMap(newGroupPolicy),
            version + 1
        ).getOrThrow()
    }
}