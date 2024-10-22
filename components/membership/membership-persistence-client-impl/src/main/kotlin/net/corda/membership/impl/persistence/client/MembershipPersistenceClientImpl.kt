package net.corda.membership.impl.persistence.client

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.command.ActivateMember
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
import net.corda.data.membership.db.request.command.PersistHostedIdentity
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.db.request.command.SessionKeyAndCertificate
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.request.command.UpdateGroupParameters
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.db.request.command.UpdateStaticNetworkInfo
import net.corda.data.membership.db.response.command.ActivateMemberResponse
import net.corda.data.membership.db.response.command.DeleteApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.data.membership.db.response.command.PersistHostedIdentityResponse
import net.corda.data.membership.db.response.command.RevokePreAuthTokenResponse
import net.corda.data.membership.db.response.command.SuspendMemberResponse
import net.corda.data.membership.db.response.query.StaticNetworkInfoQueryResponse
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.layeredpropertymap.avro.toAvro
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.SignedGroupParameters
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
import net.corda.v5.crypto.SignatureSpec
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

@Suppress("LongParameterList", "TooManyFunctions")
@Component(service = [MembershipPersistenceClient::class])
class MembershipPersistenceClientImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherFactory: PublisherFactory,
    configurationReadService: ConfigurationReadService,
    private val memberInfoFactory: MemberInfoFactory,
    private val groupParametersFactory: GroupParametersFactory,
    private val keyEncodingService: KeyEncodingService,
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
        @Reference(service = KeyEncodingService::class)
        keyEncodingService: KeyEncodingService,
    ) : this(
        coordinatorFactory,
        publisherFactory,
        configurationReadService,
        memberInfoFactory,
        groupParametersFactory,
        keyEncodingService,
        UTCClock(),
    )

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val groupName = "membership.db.persistence.client.group"
    override val clientName = "membership.db.persistence.client"

    override fun persistMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        memberInfos: Collection<SelfSignedMemberInfo>,
    ): MembershipPersistenceOperation<Unit> {
        logger.info("Persisting ${memberInfos.size} member info(s).")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistMemberInfo(
                // first is deprecated data, which will be removed in future versions
                null,
                createSignedMemberList(avroViewOwningIdentity, memberInfos),
            )
        )
        return request.operation(::nullToUnitConvertor)
    }

    private fun createSignedMemberList(
        viewOwningIdentity: net.corda.data.identity.HoldingIdentity,
        memberInfos: Collection<SelfSignedMemberInfo>
    ) = memberInfos.map {
        memberInfoFactory.createPersistentMemberInfo(
            viewOwningIdentity,
            it.memberContextBytes,
            it.mgmContextBytes,
            it.memberSignature,
            it.memberSignatureSpec,
        )
    }

    override fun persistGroupPolicy(
        viewOwningIdentity: HoldingIdentity,
        groupPolicy: LayeredPropertyMap,
        version: Long,
    ): MembershipPersistenceOperation<Unit> {
        logger.info("Persisting group policy for member $viewOwningIdentity. Version=$version")
        val avroViewOwningIdentity = viewOwningIdentity.toAvro()
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(avroViewOwningIdentity),
            PersistGroupPolicy(groupPolicy.toAvro(), version),
        )

        return request.operation(::nullToUnitConvertor)
    }

    override fun persistGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        groupParameters: InternalGroupParameters,
    ): MembershipPersistenceOperation<InternalGroupParameters> {
        logger.info("Persisting group parameters for member $viewOwningIdentity. Hash=${groupParameters.hash}")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistGroupParameters(
                AvroGroupParameters(
                    ByteBuffer.wrap(groupParameters.groupParameters),
                    (groupParameters as? SignedGroupParameters)?.mgmSignature?.toAvro(),
                    (groupParameters as? SignedGroupParameters)?.mgmSignatureSpec?.toAvro()
                )
            )
        )

        return request.operation { payload ->
            dataToResultConvertor<PersistGroupParametersResponse, InternalGroupParameters>(payload) { response ->
                groupParametersFactory.create(response.groupParameters)
            }
        }
    }

    override fun persistGroupParametersInitialSnapshot(
        viewOwningIdentity: HoldingIdentity
    ): MembershipPersistenceOperation<InternalGroupParameters> {
        logger.info("Persisting initial snapshot of group parameters.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            PersistGroupParametersInitialSnapshot()
        )

        return request.operation { payload ->
            dataToResultConvertor<PersistGroupParametersResponse, InternalGroupParameters>(payload) { response ->
                groupParametersFactory.create(response.groupParameters)
            }
        }
    }

    override fun addNotaryToGroupParameters(
        notary: PersistentMemberInfo,
    ): MembershipPersistenceOperation<InternalGroupParameters> {
        logger.info("Adding notary to persisted group parameters.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(notary.viewOwningMember),
            AddNotaryToGroupParameters(
                notary
            )
        )
        return request.operation { payload ->
            dataToResultConvertor<PersistGroupParametersResponse, InternalGroupParameters>(payload) { response ->
                groupParametersFactory.create(response.groupParameters)
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
            with(registrationRequest) {
                PersistRegistrationRequest(
                    status,
                    requester.toAvro(),
                    MembershipRegistrationRequest(
                        registrationId,
                        memberContext,
                        registrationContext,
                        serial,
                    )
                )
            }
        )
        return request.operation(::nullToUnitConvertor)
    }

    override fun setMemberAndRegistrationRequestAsApproved(
        viewOwningIdentity: HoldingIdentity,
        approvedMember: HoldingIdentity,
        registrationRequestId: String,
    ): MembershipPersistenceOperation<PersistentMemberInfo> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            UpdateMemberAndRegistrationRequestToApproved(
                approvedMember.toAvro(),
                registrationRequestId
            )
        )

        return request.operation { payload ->
            dataToResultConvertor<UpdateMemberAndRegistrationRequestResponse, PersistentMemberInfo>(payload) {
                it.memberInfo
            }
        }
    }

    override fun setRegistrationRequestStatus(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
        registrationRequestStatus: RegistrationStatus,
        reason: String?,
    ): MembershipPersistenceOperation<Unit> {
        logger.info(
            "Updating the status of a registration request with" +
                " ID '$registrationId' to status $registrationRequestStatus."
        )
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

    override fun suspendMember(
        viewOwningIdentity: HoldingIdentity,
        memberX500Name: MemberX500Name,
        serialNumber: Long?,
        reason: String?
    ): MembershipPersistenceOperation<Pair<PersistentMemberInfo, InternalGroupParameters?>> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            SuspendMember(memberX500Name.toString(), serialNumber, reason)
        )

        return request.operation { payload ->
            dataToResultConvertor<SuspendMemberResponse, Pair<PersistentMemberInfo, InternalGroupParameters?>>(payload) {
                it.memberInfo to it.groupParameters?.let { groupParameters -> groupParametersFactory.create(groupParameters) }
            }
        }
    }

    override fun activateMember(
        viewOwningIdentity: HoldingIdentity,
        memberX500Name: MemberX500Name,
        serialNumber: Long?,
        reason: String?
    ): MembershipPersistenceOperation<Pair<PersistentMemberInfo, InternalGroupParameters?>> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            ActivateMember(memberX500Name.toString(), serialNumber, reason)
        )

        return request.operation { payload ->
            dataToResultConvertor<ActivateMemberResponse, Pair<PersistentMemberInfo, InternalGroupParameters?>>(payload) {
                it.memberInfo to it.groupParameters?.let { groupParameters -> groupParametersFactory.create(groupParameters) }
            }
        }
    }

    override fun updateStaticNetworkInfo(
        info: StaticNetworkInfo
    ): MembershipPersistenceOperation<StaticNetworkInfo> {
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(),
            UpdateStaticNetworkInfo(info)
        )

        return request.operation { payload ->
            dataToResultConvertor<StaticNetworkInfoQueryResponse, StaticNetworkInfo>(payload) {
                it.info
            }
        }
    }

    override fun updateGroupParameters(
        viewOwningIdentity: HoldingIdentity,
        newGroupParameters: Map<String, String>
    ): MembershipPersistenceOperation<InternalGroupParameters> {
        logger.info("Updating group parameters for group '${viewOwningIdentity.groupId}'.")
        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            UpdateGroupParameters(newGroupParameters)
        )

        return request.operation { payload ->
            dataToResultConvertor<PersistGroupParametersResponse, InternalGroupParameters>(payload) { response ->
                groupParametersFactory.create(response.groupParameters)
            }
        }
    }

    override fun persistHostedIdentity(
        holdingIdentity: HoldingIdentity,
        p2pTlsCertificateChainAlias: String,
        useClusterLevelTlsCertificateAndKey: Boolean,
        preferredSessionKeyAndCertificate: SessionKeyAndCertificate,
        alternateSessionKeyAndCertificates: List<SessionKeyAndCertificate>
    ): MembershipPersistenceOperation<Int> {
        logger.info("Persisting locally-hosted identity for ${holdingIdentity.shortHash}.")

        val request = MembershipPersistenceRequest(
            buildMembershipRequestContext(holdingIdentity.toAvro()),
            PersistHostedIdentity(
                p2pTlsCertificateChainAlias,
                useClusterLevelTlsCertificateAndKey,
                listOf(preferredSessionKeyAndCertificate) + alternateSessionKeyAndCertificates
            )
        )

        return request.operation { payload ->
            dataToResultConvertor<PersistHostedIdentityResponse, Int>(payload) {
                it.version
            }
        }
    }

    private fun DigitalSignatureWithKey.toAvro() =
        CryptoSignatureWithKey.newBuilder()
            .setBytes(ByteBuffer.wrap(bytes))
            .setPublicKey(ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(by)))
            .build()

    private fun SignatureSpec.toAvro() = CryptoSignatureSpec(signatureName, null, null)

    private fun nullToUnitConvertor(payload: Any?): Either<Unit, String> {
        return when (payload) {
            null -> Either.Left(Unit)
            else -> Either.Right("Unexpected response: $payload")
        }
    }

    private inline fun <reified T, S> dataToResultConvertor(payload: Any?, toResult: (T) -> S): Either<S, String> {
        return when (payload) {
            is T -> Either.Left(toResult(payload))
            else -> Either.Right("Unexpected response: $payload")
        }
    }
}
