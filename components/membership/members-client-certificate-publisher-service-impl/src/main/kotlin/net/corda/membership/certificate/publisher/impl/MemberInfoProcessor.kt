package net.corda.membership.certificate.publisher.impl

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.mtls.MemberAllowedCertificateSubject
import net.corda.membership.lib.MemberInfoExtension.Companion.tlsCertificateSubject
import net.corda.membership.lib.MemberInfoFactory
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT_TOPIC

internal class MemberInfoProcessor(
    private val memberInfoFactory: MemberInfoFactory,
) : DurableProcessor<String, PersistentMemberInfo> {
    override val keyClass = String::class.java
    override val valueClass = PersistentMemberInfo::class.java

    override fun onNext(events: List<Record<String, PersistentMemberInfo>>): List<Record<*, *>> {
        return events.map {
            it.toRecord()
        }
    }

    private fun Record<String, PersistentMemberInfo>.toRecord(): Record<String, MemberAllowedCertificateSubject> {
        val value = this.value?.toClientCertificateSubject()
        val key = this.key
        return Record(
            P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT_TOPIC,
            key,
            value,
        )
    }

    private fun PersistentMemberInfo.toClientCertificateSubject(): MemberAllowedCertificateSubject? {
        val memberInfo = memberInfoFactory.createMemberInfo(this)
        if (!memberInfo.isActive) {
            return null
        }
        val subject = memberInfo.tlsCertificateSubject ?: return null
        return MemberAllowedCertificateSubject(subject)
    }
}
