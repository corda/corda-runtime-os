package net.corda.interop.service

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messaging.api.records.Record

interface InteropMemberRegistrationService {

    //Below method is to push the dummy interops member data to MEMBER_LIST_TOPIC
    fun createDummyMemberInfo(): List<Record<String, PersistentMemberInfo>>

    fun createDummyHostedIdentity(): List<Record<String, HostedIdentityEntry>>
}