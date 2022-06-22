package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.layeredpropertymap.toWire
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.impl.MGMContextImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.membership.impl.MemberInfoExtension
import net.corda.membership.impl.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.impl.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.impl.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.impl.MemberInfoImpl
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID
import java.util.concurrent.TimeUnit

@Suppress("LongParameterList")
@Component(service = [MemberRegistrationService::class])
class MGMRegistrationService @Activate constructor(
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
) : MemberRegistrationService {
    private companion object {
        val logger: Logger = contextLogger()
        const val errorMessageTemplate = "No %s was provided."

        const val PUBLICATION_TIMEOUT_SECONDS = 30L
        const val SESSION_KEY_ID = "$PARTY_SESSION_KEY.id"
        const val ECDH_KEY_ID = "$ECDH_KEY.id"
        const val REGISTRATION_PROTOCOL = "corda.group.protocol.registration"
        const val SYNCHRONISATION_PROTOCOL = "corda.group.protocol.synchronisation"
        const val P2P_MODE = "corda.group.protocol.p2p.mode"
        const val SESSION_KEY_POLICY = "corda.group.key.session.policy"
        const val PKI_SESSION = "corda.group.pki.session"
        const val PKI_TLS = "corda.group.pki.tls"
        const val TRUSTSTORE_SESSION = "corda.group.truststore.session.%s"
        const val TRUSTSTORE_TLS = "corda.group.truststore.tls.%s"

        val keyIdList = listOf(SESSION_KEY_ID, ECDH_KEY_ID)
        val errorMessageMap = errorMessageTemplate.run {
            mapOf(
                SESSION_KEY_ID to format("session key"),
                ECDH_KEY_ID to format("ECDH key"),
                REGISTRATION_PROTOCOL to format("registration protocol"),
                SYNCHRONISATION_PROTOCOL to format("synchronisation protocol"),
                P2P_MODE to format("P2P mode"),
                SESSION_KEY_POLICY to format("session key policy"),
                PKI_SESSION to format("session PKI property"),
                PKI_TLS to format("TLS PKI property"),
            )
        }
    }

    // Handler for lifecycle events
    private val lifecycleHandler = MGMRegistrationServiceLifecycleHandler(this)

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        lifecycleHandler
    )

    private val clock = UTCClock()

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("MGMRegistrationService started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("MGMRegistrationService stopped.")
        coordinator.stop()
    }

    override fun register(
        member: HoldingIdentity,
        context: Map<String, String>
    ): MembershipRequestRegistrationResult {
        if (!isRunning || coordinator.status == LifecycleStatus.DOWN) {
            return MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                "Registration failed. Reason: MGMRegistrationService is not running/down."
            )
        }
        try {
            validateContext(context)
            val sessionKey = getPemKeyFromId(context[SESSION_KEY_ID]!!, member.id)
            val ecdhKey = getPemKeyFromId(context[ECDH_KEY_ID]!!, member.id)
            val now = clock.instant().toString()
            val mgmInfo =  MemberInfoImpl(
                memberProvidedContext = layeredPropertyMapFactory.create<MemberContextImpl>(
                    (context.filter { !keyIdList.contains(it.key) } + mapOf(
                        GROUP_ID to UUID.randomUUID().toString(),
                        PARTY_NAME to member.x500Name,
                        PARTY_SESSION_KEY to sessionKey,
                        ECDH_KEY to ecdhKey,
                        PLATFORM_VERSION to "5000",
                        SOFTWARE_VERSION to "5.0.0",
                        SERIAL to "1",
                    )).toSortedMap()
                ),
                mgmProvidedContext = layeredPropertyMapFactory.create<MGMContextImpl>(
                    sortedMapOf(
                        MemberInfoExtension.CREATED_TIME to now,
                        MemberInfoExtension.MODIFIED_TIME to now,
                        MemberInfoExtension.STATUS to MemberInfoExtension.MEMBER_STATUS_ACTIVE,
                        MemberInfoExtension.IS_MGM to "true"
                    )
                )
            )
            val mgmRecord = Record(
                Schemas.Membership.MEMBER_LIST_TOPIC,
                "${member.id}-${member.id}",
                PersistentMemberInfo(
                    member.toAvro(),
                    mgmInfo.memberProvidedContext.toWire(),
                    mgmInfo.mgmProvidedContext.toWire()
                )
            )
            lifecycleHandler.publisher.publish(listOf(mgmRecord)).first().get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    e.printStackTrace(pw)
                    logger.warn("Registration failed. Reason: $sw")
                }
            }
            return MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                "Registration failed. Reason: ${e.message}"
            )
        }
        return MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED)
    }

    private fun validateContext(context: Map<String, String>) {
        for (key in errorMessageMap.keys) {
            context[key] ?: throw IllegalArgumentException(errorMessageMap[key])
        }
        require(
            context.keys.any { URL_KEY.format("[0-9]+").toRegex().matches(it) }
        ) { "No endpoint URL was provided." }
        require(
            context.keys.any { PROTOCOL_VERSION.format("[0-9]+").toRegex().matches(it) }
        ) { "No endpoint protocol was provided." }
        require(
            context.keys.any { TRUSTSTORE_SESSION.format("[0-9]+").toRegex().matches(it) }
        ) { "No session truststore was provided." }
        require(
            context.keys.any { TRUSTSTORE_TLS.format("[0-9]+").toRegex().matches(it) }
        ) { "No TLS truststore was provided." }
    }

    private fun getPemKeyFromId(keyId: String, tenantId: String): String {
        return with(cryptoOpsClient) {
            lookup(tenantId, listOf(keyId)).firstOrNull()?.let {
                val key = keyEncodingService.decodePublicKey(it.publicKey.array())
                keyEncodingService.encodeAsString(key)
            } ?: throw IllegalArgumentException("No key found for tenant: $tenantId under ID: $keyId.")
        }
    }
}
