package net.corda.membership.impl.persistence.client

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.PersistGroupPolicyRequest
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.response.query.PersistGroupPolicyResponse
import net.corda.data.membership.db.response.query.QueryFailedResponse
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.layeredpropertymap.toAvro
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.GroupPolicyProperties
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [MembershipPersistenceClient::class])
class MembershipPersistenceClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
) : MembershipPersistenceClient, AbstractPersistenceClient(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
    publisherFactory,
    configurationReadService
) {

    private companion object {
        val logger = contextLogger()
    }

    override val groupName = "membership.db.persistence.client.group"
    override val clientName = "membership.db.persistence.client"

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): MembershipPersistenceResult<Unit> {
        logger.info("Persisting ${memberInfos.size} member info(s).")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistMemberInfo(
                memberInfos.map {
                    PersistentMemberInfo(
                        avroViewOwningIdentity,
                        it.memberProvidedContext.toAvro(),
                        it.mgmProvidedContext.toAvro()
                    )
                }

            )
        ).execute()
        return when (val failedResponse = result.payload as? QueryFailedResponse) {
            null -> MembershipPersistenceResult.Success()
            else -> MembershipPersistenceResult.Failure(failedResponse.errorMessage)
        }
    }

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: GroupPolicyProperties,
    ): MembershipPersistenceResult<Int> {
        logger.info("Persisting group policy.")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistGroupPolicyRequest(groupPolicy.toAvro())
        ).execute()
        return when (val response = result.payload) {
            is PersistGroupPolicyResponse -> MembershipPersistenceResult.Success(response.version)
            is QueryFailedResponse -> MembershipPersistenceResult.Failure(response.errorMessage)
            else -> MembershipPersistenceResult.Failure("Unexpected response: $response")
        }
    }

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): MembershipPersistenceResult<Unit> {
        logger.info("Persisting the member registration request.")
        val result = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistRegistrationRequest(
                RegistrationStatus.NEW,
                registrationRequest.requester.toAvro(),
                with(registrationRequest) {
                    MembershipRegistrationRequest(
                        registrationId,
                        memberContext,
                        CryptoSignatureWithKey(
                            publicKey,
                            signature,
                            KeyValuePairList(emptyList())
                        )
                    )
                }
            )
        ).execute()
        return when (val failedResponse = result.payload as? QueryFailedResponse) {
            null -> MembershipPersistenceResult.Success()
            else -> MembershipPersistenceResult.Failure(failedResponse.errorMessage)
        }
    }
}
