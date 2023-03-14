package net.corda.membership.impl.persistence.client

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.request.command.AddPreAuthToken
import net.corda.data.membership.db.request.command.ConsumePreAuthToken
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.data.membership.db.request.command.MutualTlsRemoveFromAllowedCertificates
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.request.command.PersistGroupParametersInitialSnapshot
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.db.response.command.DeleteApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.data.membership.db.response.command.RevokePreAuthTokenResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.layeredpropertymap.toAvro
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceOperation
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.utilities.Either
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [MembershipPersistenceClient::class])
class MembershipPersistenceClientImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherFactory: PublisherFactory,
    configurationReadService: ConfigurationReadService,
    private val memberInfoFactory: MemberInfoFactory,
    private val groupParametersFactory: GroupParametersFactory,
    clock: Clock,
) : MembershipPersistenceClient, AbstractPersistenceClient(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
    publisherFactory,
    configurationReadService,
    clock,
) {
    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = MemberInfoFactory::class)
        memberInfoFactory: MemberInfoFactory,
        @Reference(service = GroupParametersFactory::class)
        groupParametersFactory: GroupParametersFactory,
    ) : this(
        coordinatorFactory,
        publisherFactory,
        configurationReadService,
        memberInfoFactory,
        groupParametersFactory,
        UTCClock(),
    )

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val groupName = "membership.db.persistence.client.group"
    override val clientName = "membership.db.persistence.client"

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<MemberInfo>
    ): MembershipPersistenceOperation<Unit> {
        logger.info("Persisting ${memberInfos.size} member info(s).")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistMemberInfo(
                memberInfos.map {
                    PersistentMemberInfo(
                        avroViewOwningIdentity,
                        it.memberProvidedContext.toAvro(),
                        it.mgmProvidedContext.toAvro(),
                    )
                }

            )
        )
        return request.operation(::nullToUnitConvertor)
    }

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
        version: Long,
    ): MembershipPersistenceOperation<Unit> {
        logger.info("Persisting group policy.")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistGroupPolicy(groupPolicy.toAvro(), version),
        )

        return request.operation(::nullToUnitConvertor)
    }

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: GroupParameters
    ): MembershipPersistenceOperation<GroupParameters> {
        logger.info("Persisting group parameters.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistGroupParameters(groupParameters.toAvro())
        )

        return request.operation { payload ->
            dataToResultConvertor<PersistGroupParametersResponse, GroupParameters>(payload) {
                groupParametersFactory.create(it.groupParameters)
            }
        }
    }

    override fun persistGroupParametersInitialSnapshot(
        viewOwningIdentity: HoldingIdentity
    ): MembershipPersistenceOperation<KeyValuePairList> {
        logger.info("Persisting initial snapshot of group parameters.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistGroupParametersInitialSnapshot()
        )

        return request.operation { payload ->
            dataToResultConvertor<PersistGroupParametersResponse, KeyValuePairList>(payload) {
                it.groupParameters
            }
        }
    }

    override fun addNotaryToGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        notary: MemberInfo
    ): MembershipPersistenceOperation<KeyValuePairList> {
        logger.info("Adding notary to persisted group parameters.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            AddNotaryToGroupParameters(
                PersistentMemberInfo(
                    viewOwningIdentity.toAvro(),
                    notary.memberProvidedContext.toAvro(),
                    notary.mgmProvidedContext.toAvro()
                )
            )
        )
        return request.operation { payload ->
            dataToResultConvertor<PersistGroupParametersResponse, KeyValuePairList>(payload) {
                it.groupParameters
            }
        }
    }

    override fun persistRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationRequest: RegistrationRequest
    ): MembershipPersistenceOperation<Unit> {
        logger.info("Persisting the member registration request.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistRegistrationRequest(
                registrationRequest.status,
                registrationRequest.requester.toAvro(),
                with(registrationRequest) {
                    MembershipRegistrationRequest(
                        registrationId,
                        memberContext,
                        signature,
                        isPending
                    )
                }
            )
        )
        return request.operation(::nullToUnitConvertor)
    }

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceOperation<MemberInfo> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            UpdateMemberAndRegistrationRequestToApproved(
                approvedMember.toAvro(),
                registrationRequestId
            )
        )

        return request.operation { payload ->
            dataToResultConvertor<UpdateMemberAndRegistrationRequestResponse, MemberInfo>(payload) {
                memberInfoFactory.create(it.memberInfo)
            }
        }
    }

    override fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String?,
    ): MembershipPersistenceOperation<Unit> {
        logger.info("Updating the status of a registration request with ID '$registrationId'.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            UpdateRegistrationRequestStatus(registrationId, registrationRequestStatus, reason)
        )
        return request.operation(::nullToUnitConvertor)
    }

    override fun mutualTlsAddCertificateToAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceOperation<Unit> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            MutualTlsAddToAllowedCertificates(
                subject
            )
        )

        return request.operation(::nullToUnitConvertor)
    }

    override fun mutualTlsRemoveCertificateFromAllowedList(
        mgmHoldingIdentity: HoldingIdentity,
        subject: String,
    ): MembershipPersistenceOperation<Unit> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            MutualTlsRemoveFromAllowedCertificates(
                subject
            )
        )

        return request.operation(::nullToUnitConvertor)
    }

    override fun generatePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        ownerX500Name: MemberX500Name,
        ttl: Instant?,
        remarks: String?
    ): MembershipPersistenceOperation<Unit> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            AddPreAuthToken(preAuthTokenId.toString(), ownerX500Name.toString(), ttl, remarks)
        )

        return request.operation(::nullToUnitConvertor)
    }

    override fun consumePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name,
        preAuthTokenId: UUID
    ): MembershipPersistenceOperation<Unit> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            ConsumePreAuthToken(preAuthTokenId.toString(), ownerX500Name.toString())
        )

        return request.operation(::nullToUnitConvertor)
    }

    override fun revokePreAuthToken(
        mgmHoldingIdentity: HoldingIdentity,
        preAuthTokenId: UUID,
        remarks: String?
    ): MembershipPersistenceOperation<PreAuthToken> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            RevokePreAuthToken(preAuthTokenId.toString(), remarks)
        )

        return request.operation { payload ->
            dataToResultConvertor<RevokePreAuthTokenResponse, PreAuthToken>(payload) {
                it.preAuthToken
            }
        }
    }

    override fun addApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleParams: ApprovalRuleParams
    ): MembershipPersistenceOperation<ApprovalRuleDetails> {
        val ruleId = UUID.randomUUID().toString()
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistApprovalRule(ruleId, ruleParams.ruleRegex, ruleParams.ruleType, ruleParams.ruleLabel)
        )

        return request.operation { payload ->
            dataToResultConvertor<PersistApprovalRuleResponse, ApprovalRuleDetails>(payload) {
                it.persistedRule
            }
        }
    }

    override fun deleteApprovalRule(
        viewOwningIdentity: HoldingIdentity,
        ruleId: String,
        ruleType: ApprovalRuleType
    ): MembershipPersistenceOperation<Unit> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            DeleteApprovalRule(ruleId, ruleType)
        )

        return request.operation { payload ->
            dataToResultConvertor<DeleteApprovalRuleResponse, Unit>(payload) {
            }
        }
    }

    private fun nullToUnitConvertor(payload: Any?): Either<Unit, String> {
        return when (payload) {
            null -> Either.Left(Unit)
            is PersistenceFailedResponse -> Either.Right(payload.errorMessage)
            else -> Either.Right("Unexpected response: $payload")
        }
    }

    private inline fun <reified T, S> dataToResultConvertor(payload: Any?, toResult: (T) -> S): Either<S, String> {
        return when (payload) {
            is T -> Either.Left(toResult(payload))
            is PersistenceFailedResponse -> Either.Right(payload.errorMessage)
            else -> Either.Right("Unexpected response: $payload")
        }
    }
}
