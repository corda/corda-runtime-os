package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.HSMContext.NOT_FAIL_IF_ASSOCIATION_EXISTS
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.toWire
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.GroupPolicy
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.MemberInfoExtension
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_PROTOCOL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.ENDPOINT_URL
import net.corda.membership.impl.registration.staticnetwork.StaticMemberTemplateExtension.Companion.staticMembers
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.NOT_SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationOutcome.SUBMITTED
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.p2p.test.HostedIdentityEntry
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_HOSTED_IDENTITIES_TOPIC
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.io.PrintWriter
import java.io.StringWriter
import java.security.PublicKey

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class StaticMemberRegistrationService @Activate constructor(
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient
) : MemberRegistrationService {
    companion object {
        private val logger: Logger = contextLogger()
        private val endpointUrlIdentifier = ENDPOINT_URL.substringBefore("-")
        private val endpointProtocolIdentifier = ENDPOINT_PROTOCOL.substringBefore("-")

        private val DUMMY_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBKjCB0qADAgECAgEDMAoGCCqGSM49BAMCMBAxDjAMBgNVBAYTBVVLIENOMB4X
            DTIyMDYxMzEwMDIwM1oXDTIyMDcxMzEwMDIwM1owEDEOMAwGA1UEBhMFVUsgQ04w
            WTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASe7Nywwu2zF8x+C6754R9nEkQR8d4W
            qogGkYq6Sv69QuuN1YX3mK/Kw64U/marhwyksLvCC3iZs6hn8+p/DgiLox0wGzAZ
            BgNVHREBAf8EDzANggtleGFtcGxlLmNvbTAKBggqhkjOPQQDAgNHADBEAiAletny
            LjfTXpntpsuM3QUKoqViPyT8OraIXx+x79BqnQIgTejO2fShMBuVF1ininmwYcY7
            nOEL3FPCmO4TaDct7E0=
            -----END CERTIFICATE-----
        """.trimIndent()
        private val DUMMY_PUBLIC_SESSION_KEY = """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4SBc982Pox28yb29m9EpdcUOU9ei
            +N5ihAvKeWRt6Mew2k5TGSiJeDao30siZQ/1lF2nES98/QAE3CTceXh//w==
            -----END PUBLIC KEY-----
        """.trimIndent()
    }

    // Handler for lifecycle events
    private val lifecycleHandler = RegistrationServiceLifecycleHandler(this)

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        lifecycleHandler
    )

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

    override fun register(member: HoldingIdentity): MembershipRequestRegistrationResult {
        if (!isRunning || coordinator.status == LifecycleStatus.DOWN) {
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: StaticMemberRegistrationService is not running/down."
            )
        }
        try {
            val groupPolicy = groupPolicyProvider.getGroupPolicy(member)
            val membershipUpdates = lifecycleHandler.publisher.publish(parseMemberTemplate(member, groupPolicy))
            membershipUpdates.forEach { it.get() }
            val hostedIdentityUpdates = lifecycleHandler.publisher.publish(listOf(createHostedIdentity(member, groupPolicy)))
            hostedIdentityUpdates.forEach { it.get() }
        } catch (e: Exception) {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    e.printStackTrace(pw)
                    logger.warn("Registration failed. Reason: $sw")
                }
            }
            return MembershipRequestRegistrationResult(
                NOT_SUBMITTED,
                "Registration failed. Reason: ${e.message}"
            )
        }
        return MembershipRequestRegistrationResult(SUBMITTED)
    }

    /**
     * Parses the static member list template, creates the MemberInfo for the registering member and the records for the
     * kafka publisher.
     */
    @Suppress("MaxLineLength")
    private fun parseMemberTemplate(registeringMember: HoldingIdentity, groupPolicy: GroupPolicy): List<Record<String, PersistentMemberInfo>> {
        val records = mutableListOf<Record<String, PersistentMemberInfo>>()

        val groupId = groupPolicy.groupId

        val staticMemberList = groupPolicy.staticMembers
        validateStaticMemberList(staticMemberList)

        val memberName = registeringMember.x500Name
        val memberId = registeringMember.id

        assignSoftHsm(memberId)

        val staticMemberInfo = staticMemberList.firstOrNull {
            MemberX500Name.parse(it.name!!) == MemberX500Name.parse(memberName)
        } ?: throw IllegalArgumentException("Our membership " + memberName + " is not listed in the static member list.")

        validateStaticMemberDeclaration(staticMemberInfo)
        // single key used as both session and ledger key
        val memberKey = getOrGenerateKeyPair(memberId)
        val encodedMemberKey = keyEncodingService.encodeAsString(memberKey)

        @Suppress("SpreadOperator")
        val memberProvidedContext = layeredPropertyMapFactory.create<MemberContextImpl>(
            sortedMapOf(
                PARTY_NAME to memberName,
                PARTY_SESSION_KEY to encodedMemberKey,
                GROUP_ID to groupId,
                *generateLedgerKeys(encodedMemberKey).toTypedArray(),
                *generateLedgerKeyHashes(memberKey).toTypedArray(),
                *convertEndpoints(staticMemberInfo).toTypedArray(),
                SOFTWARE_VERSION to staticMemberInfo.softwareVersion,
                PLATFORM_VERSION to staticMemberInfo.platformVersion,
                SERIAL to staticMemberInfo.serial,
            )
        )

        val mgmProvidedContext = layeredPropertyMapFactory.create<MGMContextImpl>(
            sortedMapOf(
                STATUS to staticMemberInfo.status,
                MODIFIED_TIME to staticMemberInfo.modifiedTime,
            )
        )

        staticMemberList.forEach {
            val owningMemberName = MemberX500Name.parse(it.name!!).toString()
            val owningMemberHoldingIdentity = HoldingIdentity(owningMemberName, groupId)
            records.add(
                Record(
                    MEMBER_LIST_TOPIC,
                    "${owningMemberHoldingIdentity.id}-$memberId",
                    PersistentMemberInfo(owningMemberHoldingIdentity.toAvro(), memberProvidedContext.toWire(), mgmProvidedContext.toWire())
                )
            )
        }

        return records
    }

    /**
     * Creates the locally hosted identity required for the P2P layer.
     */
    private fun createHostedIdentity(registeringMember: HoldingIdentity, groupPolicy: GroupPolicy): Record<String, HostedIdentityEntry> {
        val memberName = registeringMember.x500Name
        val memberId = registeringMember.id
        val groupId = groupPolicy.groupId

        /**
         * In the case of a static network, we do not need any TLS certificates or session initiation keys as communication will be purely
         * internal within the cluster. For this reason, we pass through a set of "dummy" certificates/keys.
         */
        val hostedIdentity = HostedIdentityEntry(
            net.corda.data.identity.HoldingIdentity(memberName, groupId),
            memberId,
            memberId,
            listOf(DUMMY_CERTIFICATE),
            DUMMY_PUBLIC_SESSION_KEY
        )

        return Record(
            P2P_HOSTED_IDENTITIES_TOPIC,
            "$memberName-$groupId",
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
            if(hsmRegistrationClient.findHSM(memberId, it) == null) {
                hsmRegistrationClient.assignSoftHSM(memberId, it, mapOf(NOT_FAIL_IF_ASSOCIATION_EXISTS to "YES"))
            }
        }
    }

    private fun getOrGenerateKeyPair(tenantId: String): PublicKey {
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
                scheme = ECDSA_SECP256R1_CODE_NAME // @Charlie - you will have to have a way of specifying that now
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
                    String.format(MemberInfoExtension.URL_KEY, index),
                    endpoints[index].url
                )
            )
            result.add(
                Pair(
                    String.format(MemberInfoExtension.PROTOCOL_VERSION, index),
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
                MemberInfoExtension.LEDGER_KEYS_KEY,
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
                MemberInfoExtension.LEDGER_KEY_HASHES_KEY,
                index
            ) to hash.toString()
        }
    }
}