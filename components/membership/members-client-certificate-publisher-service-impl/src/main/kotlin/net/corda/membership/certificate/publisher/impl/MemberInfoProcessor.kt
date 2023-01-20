package net.corda.membership.certificate.publisher.impl

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.mtls.ClientCertificateSubject
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.TLS_CERTIFICATE_SUBJECT
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.Companion.P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT

internal class MemberInfoProcessor : DurableProcessor<String, PersistentMemberInfo> {
    override val keyClass = String::class.java
    override val valueClass = PersistentMemberInfo::class.java

    override fun onNext(events: List<Record<String, PersistentMemberInfo>>): List<Record<*, *>> {
        return events.map {
            it.toRecord()
        }
    }

    private fun Record<String, PersistentMemberInfo>.toRecord(): Record<String, ClientCertificateSubject> {
        val value = this.value?.toClientCertificateSubject()
        val key = this.key
        return Record(
            P2P_MTLS_MEMBER_CLIENT_CERTIFICATE_SUBJECT,
            key,
            value,
        )
    }

    private fun PersistentMemberInfo.isActive(): Boolean {
        val status = this.mgmContext.items.firstOrNull {
            it.key == STATUS
        } ?: return false
        return status.value == MEMBER_STATUS_ACTIVE
    }

    private fun PersistentMemberInfo.toClientCertificateSubject(): ClientCertificateSubject? {
        if (!this.isActive()) {
            return null
        }
        val subject = this.memberContext.items.firstOrNull {
            it.key == TLS_CERTIFICATE_SUBJECT
        }?.value ?: return null
        return ClientCertificateSubject(subject)
    }
}
