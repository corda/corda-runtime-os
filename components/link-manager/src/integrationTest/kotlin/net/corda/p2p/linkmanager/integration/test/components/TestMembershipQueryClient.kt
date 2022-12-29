package net.corda.p2p.linkmanager.integration.test.components

import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity

internal class TestMembershipQueryClient(
    coordinatorFactory: LifecycleCoordinatorFactory,
): MembershipQueryClient, TestLifeCycle(
    coordinatorFactory,
    MembershipQueryClient::class
) {
    override fun queryMemberInfo(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Collection<MemberInfo>> {
        throw UnsupportedOperationException()
    }

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        queryFilter: Collection<HoldingIdentity>,
    ): MembershipQueryResult<Collection<MemberInfo>> {
        throw UnsupportedOperationException()
    }

    override fun queryRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
    ): MembershipQueryResult<RegistrationRequestStatus?> {
        throw UnsupportedOperationException()
    }

    override fun queryRegistrationRequestsStatus(
        viewOwningIdentity: HoldingIdentity
    ): MembershipQueryResult<List<RegistrationRequestStatus>> {
        throw UnsupportedOperationException()
    }

    override fun queryMembersSignatures(
        viewOwningIdentity: HoldingIdentity,
        holdingsIdentities: Collection<HoldingIdentity>,
    ): MembershipQueryResult<Map<HoldingIdentity, CryptoSignatureWithKey>> {
        throw UnsupportedOperationException()
    }

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<LayeredPropertyMap> {
        throw UnsupportedOperationException()
    }
}