package net.corda.membership.impl.persistence.client

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.query.MutualTlsListAllowedCertificates
import net.corda.data.membership.db.request.query.QueryApprovalRules
import net.corda.data.membership.db.request.query.QueryGroupPolicy
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.data.membership.db.request.query.QueryRegistrationRequests
import net.corda.data.membership.db.request.query.QueryStaticNetworkInfo
import net.corda.data.membership.db.response.query.ApprovalRulesQueryResponse
import net.corda.data.membership.db.response.query.GroupPolicyQueryResponse
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.data.membership.db.response.query.MutualTlsListAllowedCertificatesResponse
import net.corda.data.membership.db.response.query.PreAuthTokenQueryResponse
import net.corda.data.membership.db.response.query.RegistrationRequestQueryResponse
import net.corda.data.membership.db.response.query.RegistrationRequestsQueryResponse
import net.corda.data.membership.db.response.query.StaticNetworkInfoQueryResponse
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.toMap
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.client.MembershipQueryResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.utilities.Either
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [MembershipQueryClient::class])
class MembershipQueryClientImpl(
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherFactory: PublisherFactory,
    configurationReadService: ConfigurationReadService,
    private val memberInfoFactory: MemberInfoFactory,
    clock: Clock,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : MembershipQueryClient, AbstractPersistenceClient(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
    publisherFactory,
    configurationReadService,
    clock,
) {

    @Activate constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = ConfigurationReadService::class)
        configurationReadService: ConfigurationReadService,
        @Reference(service = MemberInfoFactory::class)
        memberInfoFactory: MemberInfoFactory,
        @Reference(service = LayeredPropertyMapFactory::class)
        layeredPropertyMapFactory: LayeredPropertyMapFactory,
    ) : this(coordinatorFactory, publisherFactory, configurationReadService, memberInfoFactory, UTCClock(), layeredPropertyMapFactory)

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val groupName = "membership.db.query.client.group"
    override val clientName = "membership.db.query.client"

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        statusFilter: List<String>
    ): MembershipQueryResult<Collection<SelfSignedMemberInfo>> {
        logger.info("Querying for all member infos visible from holding identity [${viewOwningIdentity.shortHash}].")
        return queryMemberInfo(viewOwningIdentity, emptyList(), statusFilter)
    }

    override fun queryMemberInfo(
        viewOwningIdentity: HoldingIdentity,
        holdingIdentityFilter: Collection<HoldingIdentity>,
        statusFilter: List<String>,
    ): MembershipQueryResult<Collection<SelfSignedMemberInfo>> {
        if (holdingIdentityFilter.isNotEmpty()) {
            logger.info("Querying for member infos represented by ${holdingIdentityFilter.size} holding identities")
        }
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            QueryMemberInfo(holdingIdentityFilter.map { it.toAvro() }, statusFilter),
        ).execute("query member info") { payload: MemberInfoQueryResponse ->
            logger.info("Found ${payload.members.size} results.")
            payload.members.map {
                memberInfoFactory.createSelfSignedMemberInfo(
                    it.signedMemberContext.data.array(),
                    it.serializedMgmContext.array(),
                    it.signedMemberContext.signature,
                    it.signedMemberContext.signatureSpec,
                )
            }
        }
    }

    override fun queryRegistrationRequest(
        viewOwningIdentity: HoldingIdentity,
        registrationId: String,
    ): MembershipQueryResult<RegistrationRequestDetails?> {
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            QueryRegistrationRequest(registrationId),
        ).execute("retrieve registration request") { payload: RegistrationRequestQueryResponse ->
            payload.registrationRequest
        }
    }

    override fun queryRegistrationRequests(
        viewOwningIdentity: HoldingIdentity,
        requestSubjectX500Name: MemberX500Name?,
        statuses: List<RegistrationStatus>,
        limit: Int?,
    ): MembershipQueryResult<List<RegistrationRequestDetails>> {
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            QueryRegistrationRequests(requestSubjectX500Name?.toString(), statuses, limit),
        ).execute("retrieve registration requests") { payload: RegistrationRequestsQueryResponse ->
            payload.registrationRequests
        }
    }

    override fun queryGroupPolicy(viewOwningIdentity: HoldingIdentity): MembershipQueryResult<Pair<LayeredPropertyMap, Long>> {
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            QueryGroupPolicy(),
        ).execute("retrieve group policy") { payload: GroupPolicyQueryResponse ->
            layeredPropertyMapFactory.createMap(payload.properties.toMap()) to payload.version
        }
    }

    override fun mutualTlsListAllowedCertificates(
        mgmHoldingIdentity: HoldingIdentity,
    ): MembershipQueryResult<Collection<String>> {
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            MutualTlsListAllowedCertificates(),
        ).execute("retrieve mutual TLS allowed certificates") { payload: MutualTlsListAllowedCertificatesResponse ->
            payload.subjects
        }
    }

    override fun queryPreAuthTokens(
        mgmHoldingIdentity: HoldingIdentity,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean,
    ): MembershipQueryResult<List<PreAuthToken>> {
        val statuses = if (viewInactive) {
            PreAuthTokenStatus.values().toList()
        } else {
            listOf(PreAuthTokenStatus.AVAILABLE)
        }
        val ownerX500NameString = ownerX500Name?.let { ownerX500Name.toString() }
        val preAuthTokenIdString = preAuthTokenId?.let { preAuthTokenId.toString() }
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(mgmHoldingIdentity.toAvro()),
            QueryPreAuthToken(ownerX500NameString, preAuthTokenIdString, statuses),
        ).execute("query for pre auth tokens") { payload: PreAuthTokenQueryResponse ->
            payload.tokens
        }
    }

    override fun getApprovalRules(
        viewOwningIdentity: HoldingIdentity,
        ruleType: ApprovalRuleType,
    ): MembershipQueryResult<Collection<ApprovalRuleDetails>> {
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(viewOwningIdentity.toAvro()),
            QueryApprovalRules(ruleType),
        ).execute("retrieve approval rules") { payload: ApprovalRulesQueryResponse ->
            payload.rules
        }
    }

    override fun queryStaticNetworkInfo(
        groupId: String,
    ): MembershipQueryResult<StaticNetworkInfo> {
        return MembershipPersistenceRequest(
            buildMembershipRequestContext(),
            QueryStaticNetworkInfo(groupId),
        ).execute("retrieve static network") { payload: StaticNetworkInfoQueryResponse ->
            payload.info
        }
    }

    private inline fun <reified T, E> MembershipPersistenceRequest.execute(
        operationName: String,
        crossinline convert: (T) -> E,
    ): MembershipQueryResult<E> {
        val result = this.operation {
            if (it is T) {
                Either.Left(it)
            } else {
                Either.Right(
                    "Query returned unexpected payload. Got ${it?.javaClass} while waiting for ${T::class.java}",
                )
            }
        }.send()
        return when (result) {
            is Either.Left -> MembershipQueryResult.Success(convert(result.a))
            is Either.Right -> {
                val err = "Failed to $operationName: ${result.b}."
                logger.warn(err)
                MembershipQueryResult.Failure(err)
            }
        }
    }
}
