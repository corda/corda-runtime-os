package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts.Categories.LEDGER
import net.corda.crypto.core.CryptoConsts.Categories.NOTARY
import net.corda.crypto.core.CryptoConsts.Categories.SESSION_INIT
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.RegistrationStatus
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.KeyDetails
import net.corda.membership.impl.registration.KeysFactory
import net.corda.membership.impl.registration.MemberRole
import net.corda.membership.impl.registration.MemberRole.Companion.toMemberInfo
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.lib.EndpointInfoFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.ProtocolParameters.SessionKeyPolicy
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.schema.validation.MembershipSchemaValidationException
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.schema.membership.MembershipSchema.RegistrationContextSchema
import net.corda.utilities.concurrent.SecManagerForkJoinPool
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.versioning.Version
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class StaticMemberRegistrationService @Activate constructor(
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = PublisherFactory::class)
    internal val publisherFactory: PublisherFactory,
    @Reference(service = SubscriptionFactory::class)
    internal val subscriptionFactory: SubscriptionFactory,
    @Reference(service = KeyEncodingService::class)
    internal val keyEncodingService: KeyEncodingService,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient,
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory,
    @Reference(service = MembershipPersistenceClient::class)
    private val persistenceClient: MembershipPersistenceClient,
    @Reference(service = CordaAvroSerializationFactory::class)
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = MembershipSchemaValidatorFactory::class)
    val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory,
    @Reference(service = EndpointInfoFactory::class)
    private val endpointInfoFactory: EndpointInfoFactory,
    @Reference(service = PlatformInfoProvider::class)
    internal val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = GroupParametersFactory::class)
    private val groupParametersFactory: GroupParametersFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = GroupParametersWriterService::class)
    private val groupParametersWriterService: GroupParametersWriterService,
) : MemberRegistrationService {
    companion object {
        private val logger: Logger = contextLogger()
        private val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        private val endpointProtocolIdentifier = ENDPOINT_PROTOCOL.substringBefore("-")
        private const val KEY_SCHEME = "corda.key.scheme"
        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/static_network_dummy_certificate.pem")!!.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/static_network_dummy_session_key.pem")!!.readText()
    }

    // Handler for lifecycle events
    private val lifecycleHandler = RegistrationServiceLifecycleHandler(this)

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        lifecycleHandler
    )
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer { logger.error("Failed to serialize key value pair list.") }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun register(
        registrationId: UUID,
        member: HoldingIdentity,
        context: Map<String, String>
    ): MembershipRequestRegistrationResult {
        if (!isRunning || coordinator.status == LifecycleStatus.DOWN) {
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            )
        }
        try {
            membershipSchemaValidatorFactory
                .createValidator()
                .validateRegistrationContext(
                    RegistrationContextSchema.StaticMember,
                    Version(1, 0),
                    context
                )
        } catch (ex: MembershipSchemaValidationException) {
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. The registration context is invalid: " + ex.message
            )
        }
        try {
            val roles = MemberRole.extractRolesFromContext(context)
            logger.debug("Roles are: {}", roles)
            val keyScheme = context[KEY_SCHEME] ?: throw IllegalArgumentException("Key scheme must be specified.")
            val groupPolicy = groupPolicyProvider.getGroupPolicy(member)
                ?: throw CordaRuntimeException("Could not find group policy for member: [$member]")
            val staticMemberList = with(groupPolicy.protocolParameters.staticNetworkMembers) {
                requireNotNull(this) { "Could not find static member list in group policy file." }
                map { StaticMember(it, endpointInfoFactory::create) }
            }
            val (memberInfo, records) = parseMemberTemplate(
                member,
                groupPolicy,
                keyScheme,
                roles,
                staticMemberList,
            )
            (records + createHostedIdentity(member, groupPolicy)).publish()

            persistGroupParameters(memberInfo, staticMemberList)

            persistRegistrationRequest(registrationId, memberInfo)
        } catch (e: Exception) {
            logger.warn("Registration failed. Reason:", e)
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: ${e.message}"
            )
        }
        return MembershipRequestRegistrationResult(SUBMITTED)
    }

    private fun List<Record<*, *>>.publish() {
        lifecycleHandler.publisher.publish(this).forEach {
            it.get()
        }
    }

    private fun persistGroupParameters(memberInfo: MemberInfo, staticMemberList: List<StaticMember>) {
        val cache = lifecycleHandler.groupParametersCache
        val holdingIdentity = memberInfo.holdingIdentity
        val groupParametersList = cache.getOrCreateGroupParameters(holdingIdentity).run {
            memberInfo.notaryDetails?.let {
                cache.addNotary(memberInfo)
            } ?: this
        }
        val groupParameters = groupParametersFactory.create(groupParametersList)

        // Persist group parameters for this member, and publish to Kafka.
        persistenceClient.persistGroupParameters(holdingIdentity, groupParameters)
        groupParametersWriterService.put(holdingIdentity, groupParameters)

        // If this member is a notary, persist updated group parameters for other members who have a vnode set up.
        // Also publish to Kafka.
        if (memberInfo.notaryDetails != null) {
            SecManagerForkJoinPool.pool.submit {
                staticMemberList
                    .parallelStream()
                    .map { MemberX500Name.parse(it.name!!) }
                    .filter { it != memberInfo.name }
                    .map { HoldingIdentity(it, memberInfo.groupId) }
                    .filter { virtualNodeInfoReadService.get(it) != null }
                    .forEach {
                        persistenceClient.persistGroupParameters(it, groupParameters)
                        groupParametersWriterService.put(it, groupParameters)
                    }
            }
        }
    }

    private fun persistRegistrationRequest(registrationId: UUID, memberInfo: MemberInfo) {
        val memberContext = keyValuePairListSerializer.serialize(memberInfo.memberProvidedContext.toAvro())
            ?: throw IllegalArgumentException("Failed to serialize the member context for this request.")
        persistenceClient.persistRegistrationRequest(
            viewOwningIdentity = memberInfo.holdingIdentity,
            registrationRequest = RegistrationRequest(
                status = RegistrationStatus.APPROVED,
                registrationId = registrationId.toString(),
                requester = memberInfo.holdingIdentity,
                memberContext = ByteBuffer.wrap(memberContext),
                signature = CryptoSignatureWithKey(
                    ByteBuffer.wrap(byteArrayOf()),
                    ByteBuffer.wrap(byteArrayOf()),
                    KeyValuePairList(emptyList())
                )
            )
        )
    }

    /**
     * Parses the static member list template, creates the MemberInfo for the registering member and the records for the
     * kafka publisher.
     */
    @Suppress("ThrowsCount")
    private fun parseMemberTemplate(
        registeringMember: HoldingIdentity,
        groupPolicy: GroupPolicy,
        keyScheme: String,
        roles: Collection<MemberRole>,
        staticMemberList: List<StaticMember>,
    ): Pair<MemberInfo, List<Record<String, PersistentMemberInfo>>> {
        validateStaticMemberList(staticMemberList)

        val memberName = registeringMember.x500Name
        val memberId = registeringMember.shortHash.value

        val staticMemberInfo = staticMemberList.firstOrNull {
            MemberX500Name.parse(it.name!!) == memberName
        } ?: throw IllegalArgumentException("Our membership $memberName is not listed in the static member list.")

        validateStaticMemberDeclaration(staticMemberInfo)
        // single key scheme used for both session and ledger key
        val keysFactory = KeysFactory(
            cryptoOpsClient,
            keyEncodingService,
            keyScheme,
            memberId,
        )
        hsmRegistrationClient.assignSoftHSM(memberId, LEDGER)
        val ledgerKey = keysFactory.getOrGenerateKeyPair(LEDGER)

        val sessionKey = when(groupPolicy.protocolParameters.sessionKeyPolicy) {
            SessionKeyPolicy.DISTINCT -> {
                hsmRegistrationClient.assignSoftHSM(memberId, SESSION_INIT)
                keysFactory.getOrGenerateKeyPair(SESSION_INIT)
            }
            SessionKeyPolicy.COMBINED -> {
                ledgerKey
            }
        }

        val cpi = virtualNodeInfoReadService.get(registeringMember)?.cpiIdentifier
            ?: throw CordaRuntimeException("Could not find virtual node info for member ${registeringMember.shortHash}")

        val optionalContext = cpi.signerSummaryHash?.let {
            mapOf(MEMBER_CPI_SIGNER_HASH to it.toString())
        } ?: emptyMap()

        fun configureNotaryKey(): List<KeyDetails> {
            hsmRegistrationClient.assignSoftHSM(memberId, NOTARY)
            return listOf(keysFactory.getOrGenerateKeyPair(NOTARY))
        }

        @Suppress("SpreadOperator")
        val memberContext = mapOf(
            PARTY_NAME to memberName.toString(),
            PARTY_SESSION_KEY to sessionKey.pem,
            SESSION_KEY_HASH to sessionKey.hash.toString(),
            GROUP_ID to groupPolicy.groupId,
            LEDGER_KEYS_KEY.format(0) to ledgerKey.pem,
            LEDGER_KEY_HASHES_KEY.format(0) to ledgerKey.hash.toString(),
            *convertEndpoints(staticMemberInfo).toTypedArray(),
            *roles.toMemberInfo(::configureNotaryKey).toTypedArray(),
            SOFTWARE_VERSION to platformInfoProvider.localWorkerSoftwareVersion,
            PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
            MEMBER_CPI_NAME to cpi.name,
            MEMBER_CPI_VERSION to cpi.version,
            SERIAL to staticMemberInfo.serial,
        ) + optionalContext

        val memberInfo = memberInfoFactory.create(
            memberContext.toSortedMap(),
            sortedMapOf(
                STATUS to staticMemberInfo.status,
                MODIFIED_TIME to staticMemberInfo.modifiedTime,
            )
        )

        return memberInfo to staticMemberList.map {
            val owningMemberHoldingIdentity = HoldingIdentity(MemberX500Name.parse(it.name!!), groupPolicy.groupId)
            Record(
                MEMBER_LIST_TOPIC,
                "${owningMemberHoldingIdentity.shortHash}-$memberId",
                PersistentMemberInfo(
                    owningMemberHoldingIdentity.toAvro(),
                    memberInfo.memberProvidedContext.toAvro(),
                    memberInfo.mgmProvidedContext.toAvro()
                )
            )
        }
    }

    /**
     * Creates the locally hosted identity required for the P2P layer.
     */
    private fun createHostedIdentity(
        registeringMember: HoldingIdentity,
        groupPolicy: GroupPolicy
    ): Record<String, HostedIdentityEntry> {
        val memberName = registeringMember.x500Name
        val memberId = registeringMember.shortHash
        val groupId = groupPolicy.groupId

        /**
         * In the case of a static network, we do not need any TLS certificates or session initiation keys as communication will be purely
         * internal within the cluster. For this reason, we pass through a set of "dummy" certificates/keys.
         */
        val hostedIdentity = HostedIdentityEntry(
            net.corda.data.identity.HoldingIdentity(memberName.toString(), groupId),
            memberId.value,
            memberId.value,
            listOf(DUMMY_CERTIFICATE),
            DUMMY_PUBLIC_SESSION_KEY,
            null
        )

        return Record(
            P2P_HOSTED_IDENTITIES_TOPIC,
            registeringMember.shortHash.value,
            hostedIdentity
        )
    }

    private fun validateStaticMemberList(members: List<StaticMember>) {
        require(members.isNotEmpty()) { "Static member list inside the group policy file cannot be empty." }
        members.forEach {
            require(!it.name.isNullOrBlank()) { "Member's name is not provided in static member list." }
        }
    }

    private fun validateStaticMemberDeclaration(member: StaticMember) {
        require(
            member.keys.any { it.startsWith(endpointUrlIdentifier) }
        ) { "Endpoint urls are not provided." }
        require(
            member.keys.any { it.startsWith(endpointProtocolIdentifier) }
        ) { "Endpoint protocols are not provided." }
    }

    /**
     * Mapping the keys from the json format to the keys expected in the [MemberInfo].
     */
    private fun convertEndpoints(member: StaticMember): List<Pair<String, String>> {
        val endpoints = mutableListOf<EndpointInfo>()
        member.keys.filter { it.startsWith(endpointUrlIdentifier) }.size.apply {
            for (index in 1..this) {
                endpoints.add(member.getEndpoint(index))
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        for (index in endpoints.indices) {
            result.add(
                Pair(
                    String.format(URL_KEY, index),
                    endpoints[index].url
                )
            )
            result.add(
                Pair(
                    String.format(PROTOCOL_VERSION, index),
                    endpoints[index].protocolVersion.toString()
                )
            )
        }
        return result
    }
}
