package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.event.MembershipEvent
import net.corda.data.membership.event.registration.MgmOnboarded
import net.corda.layeredpropertymap.toAvro
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.EVENT_TOPIC
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toAvro
import java.util.concurrent.TimeUnit

internal class MGMRegistrationOutputPublisher(
    private val publisherFactory: () -> Publisher
) {

    private companion object {
        const val PUBLICATION_TIMEOUT_SECONDS = 30L
    }

    fun publish(
        mgmInfo: MemberInfo
    ) {
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

        try {
            publisherFactory
                .invoke()
                .publish(listOf(mgmRecord, eventRecord))
                .forEach {
                    it.get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
        } catch (ex: CordaMessageAPIFatalException) {
            throw MGMRegistrationOutputPublisherException(ex.message ?: "Unknown failure.", ex)
        }
    }
}

internal class MGMRegistrationOutputPublisherException(
    val reason: String,
    ex: Throwable
) : CordaRuntimeException(reason, ex)