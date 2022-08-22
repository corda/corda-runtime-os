package net.corda.membership.impl.synchronisation.dummy

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.nio.ByteBuffer

interface TestMembershipQueryClient : MembershipQueryClient

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [MembershipQueryClient::class, TestMembershipQueryClient::class])
class TestMembershipQueryClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestMembershipQueryClient {
    private val coordinator =
        coordinatorFactory.createCoordinator(LifecycleCoordinatorName.forComponent<MembershipQueryClient>()) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    override fun queryMemberInfo(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Collection<MemberInfo>> {
        TODO("Not yet implemented")
    }

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        queryFilter: Collection<HoldingIdentity>
    ): MembershipQueryResult<Collection<MemberInfo>> {
        TODO("Not yet implemented")
    }

    override fun queryRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String
    ): MembershipQueryResult<RegistrationRequest> {
        TODO("Not yet implemented")
    }

    override fun queryMembersSignatures(
        viewOwningIdentity: HoldingIdentity,
        holdingsIdentities: Collection<HoldingIdentity>
    ): MembershipQueryResult<Map<HoldingIdentity, CryptoSignatureWithKey>> {
        return MembershipQueryResult.Success(
            holdingsIdentities.associateWith {
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(viewOwningIdentity.toAvro().x500Name.toByteArray()),
                    ByteBuffer.wrap(viewOwningIdentity.toAvro().x500Name.toByteArray()),
                    KeyValuePairList(
                        listOf(
                            KeyValuePair("name", it.x500Name.toString())
                        )
                    )
                )
            }
        )
    }

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<LayeredPropertyMap> {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        TestCryptoOpsClientImpl.logger.info("TestCryptoOpsClient starting.")
        coordinator.start()
    }

    override fun stop() {
        TestCryptoOpsClientImpl.logger.info("TestCryptoOpsClient starting.")
        coordinator.stop()
    }
}