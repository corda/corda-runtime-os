package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.RegistrationStatus
import net.corda.layeredpropertymap.toAvro
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
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
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.grouppolicy.GroupPolicy
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.HostedIdentityEntry
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.UUID

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class StaticMemberRegistrationService @Activate constructor(
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = PublisherFactory::class)
    internal val publisherFactory: PublisherFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
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
) : MemberRegistrationService {
    companion object {
        private val logger: Logger = contextLogger()
        private val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        private val endpointProtocolIdentifier = ENDPOINT_PROTOCOL.substringBefore("-")
        private const val KEY_SCHEME = "corda.key.scheme"
        private val DUMMY_CERTIFICATE = this::class.java.getResource("/static_network_dummy_certificate.pem")!!.readText()
        private val DUMMY_PUBLIC_SESSION_KEY = this::class.java.getResource("/static_network_dummy_session_key.pem")!!.readText()
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
        logger.info("StaticMemberRegistrationService started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("StaticMemberRegistrationService stopped.")
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
            val keyScheme = context[KEY_SCHEME] ?: throw IllegalArgumentException("Key scheme must be specified.")
            val groupPolicy = groupPolicyProvider.getGroupPolicy(member)
                ?: throw CordaRuntimeException("Could not find group policy for member: [$member]")
            val (memberInfo, records) = parseMemberTemplate(member, groupPolicy, keyScheme)
            records.publish()
            listOf(createHostedIdentity(member, groupPolicy)).publish()
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
                publicKey = ByteBuffer.wrap(byteArrayOf()),
                signature = ByteBuffer.wrap(byteArrayOf()),
            )
        )
    }

    /**
     * Parses the static member list template, creates the MemberInfo for the registering member and the records for the
     * kafka publisher.
     */
    @Suppress("MaxLineLength")
    private fun parseMemberTemplate(
        registeringMember: HoldingIdentity,
        groupPolicy: GroupPolicy,
        keyScheme: String,
    ): Pair<MemberInfo, List<Record<String, PersistentMemberInfo>>> {
        val groupId = groupPolicy.groupId

        val staticMemberMaps = groupPolicy.protocolParameters.staticNetworkMembers
            ?: throw IllegalArgumentException("Could not find static member list in group policy file.")
        val staticMemberList = staticMemberMaps.map { StaticMember(it) }
        validateStaticMemberList(staticMemberList)

        val memberName = registeringMember.x500Name
        val memberId = registeringMember.shortHash.value

        assignSoftHsm(memberId)

        val staticMemberInfo = staticMemberList.firstOrNull {
            MemberX500Name.parse(it.name!!) == memberName
        } ?: throw IllegalArgumentException("Our membership $memberName is not listed in the static member list.")

        validateStaticMemberDeclaration(staticMemberInfo)
        // single key used as both session and ledger key
        val memberKey = getOrGenerateKeyPair(memberId, keyScheme)
        val encodedMemberKey = keyEncodingService.encodeAsString(memberKey)

        @Suppress("SpreadOperator")
        val memberInfo = memberInfoFactory.create(
            sortedMapOf(
                PARTY_NAME to memberName.toString(),
                PARTY_SESSION_KEY to encodedMemberKey,
                GROUP_ID to groupId,
                *generateLedgerKeys(encodedMemberKey).toTypedArray(),
                *generateLedgerKeyHashes(memberKey).toTypedArray(),
                *convertEndpoints(staticMemberInfo).toTypedArray(),
                SESSION_KEY_HASH to memberKey.calculateHash().toString(),
                SOFTWARE_VERSION to staticMemberInfo.softwareVersion,
                PLATFORM_VERSION to staticMemberInfo.platformVersion,
                SERIAL to staticMemberInfo.serial,
            ),
            sortedMapOf(
                STATUS to staticMemberInfo.status,
                MODIFIED_TIME to staticMemberInfo.modifiedTime,
            )
        )

        return memberInfo to staticMemberList.map {
            val owningMemberName = MemberX500Name.parse(it.name!!).toString()
            val owningMemberHoldingIdentity = HoldingIdentity(MemberX500Name.parse(owningMemberName), groupId)
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
            DUMMY_PUBLIC_SESSION_KEY
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
     * Assigns soft HSM to the registering member.
     */
    private fun assignSoftHsm(memberId: String) {
        CryptoConsts.Categories.all.forEach {
            if (hsmRegistrationClient.findHSM(memberId, it) == null) {
                hsmRegistrationClient.assignSoftHSM(memberId, it)
            }
        }
    }

    private fun getOrGenerateKeyPair(tenantId: String, scheme: String): PublicKey {
        return with(cryptoOpsClient) {
            lookup(
                tenantId = tenantId,
                skip = 0,
                take = 10,
                orderBy = CryptoKeyOrderBy.NONE,
                filter = mapOf(
                    ALIAS_FILTER to tenantId,
                )
            ).firstOrNull()?.let {
                keyEncodingService.decodePublicKey(it.publicKey.array())
            } ?: generateKeyPair(
                tenantId = tenantId,
                category = CryptoConsts.Categories.LEDGER,
                alias = tenantId,
                scheme = scheme
            )
        }
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

    /**
     * Only going to contain the common session and ledger key for passing the checks on the MemberInfo creation side.
     * For the static network we don't need the rotated keys.
     */
    private fun generateLedgerKeys(
        memberKey: String
    ): List<Pair<String, String>> {
        val ledgerKeys = listOf(memberKey)
        return ledgerKeys.mapIndexed { index, ledgerKey ->
            String.format(
                LEDGER_KEYS_KEY,
                index
            ) to ledgerKey
        }
    }

    /**
     * Only going to contain hash of the common session and ledger key for passing the checks on the MemberInfo creation side.
     * For the static network we don't need hashes of the rotated keys.
     */
    private fun generateLedgerKeyHashes(
        memberKey: PublicKey
    ): List<Pair<String, String>> {
        val ledgerKeys = listOf(memberKey)
        return ledgerKeys.mapIndexed { index, ledgerKey ->
            val hash = ledgerKey.calculateHash()
            String.format(
                LEDGER_KEY_HASHES_KEY,
                index
            ) to hash.toString()
        }
    }
}
