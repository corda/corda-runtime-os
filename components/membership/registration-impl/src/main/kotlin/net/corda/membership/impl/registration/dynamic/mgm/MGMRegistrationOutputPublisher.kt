package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.event.MembershipEvent
import net.corda.data.membership.event.registration.MgmOnboarded
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.EVENT_TOPIC
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toAvro

internal class MGMRegistrationOutputPublisher {

    fun createRecords(
        mgmInfo: MemberInfo
    ): Collection<Record<*, *>> {
        val holdingIdentity = mgmInfo.holdingIdentity
        val holdingIdentityShortHash = holdingIdentity.shortHash.value
        val avroHoldingIdentity = holdingIdentity.toAvro()
        val mgmRecord = Record(
            MEMBER_LIST_TOPIC,
            "$holdingIdentityShortHash-$holdingIdentityShortHash",
            PersistentMemberInfo(
                avroHoldingIdentity,
                mgmInfo.memberProvidedContext.toAvro(),
                mgmInfo.mgmProvidedContext.toAvro()
            )
        )

        val eventRecord = Record(
            EVENT_TOPIC,
            holdingIdentityShortHash,
            MembershipEvent(
                MgmOnboarded(avroHoldingIdentity)
            )
        )

        return listOf(mgmRecord, eventRecord)
    }
}

internal class MGMRegistrationOutputPublisherException(
    val reason: String,
    ex: Throwable
) : CordaRuntimeException(reason, ex)