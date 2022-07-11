package net.corda.membership.impl.registration.dynamic.mgm

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.toAvro
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.CREATED_TIME
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.ECDH_KEY
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.registration.MemberRegistrationService
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.MembershipRequestRegistrationResult
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
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
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = MemberInfoFactory::class)
    val memberInfoFactory: MemberInfoFactory,
    @Reference(service = MembershipPersistenceClient::class)
    val membershipPersistenceClient: MembershipPersistenceClient,
) : MemberRegistrationService {
    /**
     * Private interface used for implementation swapping in response to lifecycle events.
     */
    private interface InnerRegistrationService : AutoCloseable {
        fun register(member: HoldingIdentity, context: Map<String, String>): MembershipRequestRegistrationResult
    }

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
        const val PLATFORM_VERSION_CONST = "5000"
        const val SOFTWARE_VERSION_CONST = "5.0.0"
        const val SERIAL_CONST = "1"

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
    // for watching the config changes
    private var configHandle: AutoCloseable? = null
    // for checking the components' health
    private var componentHandle: RegistrationHandle? = null

    private var _publisher: Publisher? = null

    /**
     * Publisher for Kafka messaging. Recreated after every [MESSAGING_CONFIG] change.
     */
    private val publisher: Publisher
        get() = _publisher ?: throw IllegalArgumentException("Publisher is not initialized.")

    // Component lifecycle coordinator
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)

    private val clock = UTCClock()

    private var impl: InnerRegistrationService = InactiveImpl

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

    private fun activate(coordinator: LifecycleCoordinator) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun deactivate(coordinator: LifecycleCoordinator) {
        coordinator.updateStatus(LifecycleStatus.DOWN)
        impl.close()
        impl = InactiveImpl
    }

    override fun register(
        member: HoldingIdentity,
        context: Map<String, String>
    ): MembershipRequestRegistrationResult = impl.register(member, context)

    private object InactiveImpl : InnerRegistrationService {
        override fun register(
            member: HoldingIdentity,
            context: Map<String, String>
        ): MembershipRequestRegistrationResult =
            MembershipRequestRegistrationResult(
                MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                "Registration failed. Reason: MGMRegistrationService is not running."
            )

        override fun close() = Unit
    }

    private inner class ActiveImpl : InnerRegistrationService {
        override fun register(
            member: HoldingIdentity,
            context: Map<String, String>
        ): MembershipRequestRegistrationResult {
            try {
                validateContext(context)
                val sessionKey = getPemKeyFromId(context[SESSION_KEY_ID]!!, member.id)
                val ecdhKey = getPemKeyFromId(context[ECDH_KEY_ID]!!, member.id)
                val now = clock.instant().toString()
                val mgmInfo = memberInfoFactory.create(
                    memberContext = (context.filter { !keyIdList.contains(it.key) } + mapOf(
                        GROUP_ID to member.groupId,
                        PARTY_NAME to member.x500Name,
                        PARTY_SESSION_KEY to sessionKey,
                        ECDH_KEY to ecdhKey,
                        // temporarily hardcoded
                        PLATFORM_VERSION to PLATFORM_VERSION_CONST,
                        SOFTWARE_VERSION to SOFTWARE_VERSION_CONST,
                        SERIAL to SERIAL_CONST,
                    )).toSortedMap(),
                    mgmContext = sortedMapOf(
                        CREATED_TIME to now,
                        MODIFIED_TIME to now,
                        STATUS to MEMBER_STATUS_ACTIVE,
                        IS_MGM to "true"
                    )
                )

                val persistenceResult = membershipPersistenceClient.persistMemberInfo(member, listOf(mgmInfo))
                if (persistenceResult is MembershipPersistenceResult.Failure) {
                    return MembershipRequestRegistrationResult(
                        MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                        "Registration failed, persistence error. Reason: ${persistenceResult.errorMsg}"
                    )
                }

                val mgmRecord = Record(
                    Schemas.Membership.MEMBER_LIST_TOPIC,
                    "${member.id}-${member.id}",
                    PersistentMemberInfo(
                        member.toAvro(),
                        mgmInfo.memberProvidedContext.toAvro(),
                        mgmInfo.mgmProvidedContext.toAvro()
                    )
                )
                publisher.publish(listOf(mgmRecord)).first().get(PUBLICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn("Registration failed.", e)
                return MembershipRequestRegistrationResult(
                    MembershipRequestRegistrationOutcome.NOT_SUBMITTED,
                    "Registration failed. Reason: ${e.message}"
                )
            }
            return MembershipRequestRegistrationResult(MembershipRequestRegistrationOutcome.SUBMITTED)
        }

        override fun close() {
            publisher.close()
        }

        private fun validateContext(context: Map<String, String>) {
            for (key in errorMessageMap.keys) {
                context[key] ?: throw IllegalArgumentException(errorMessageMap[key])
            }
            context.keys.filter { URL_KEY.format("[0-9]+").toRegex().matches(it) }.apply {
                require(isNotEmpty()) { "No endpoint URL was provided." }
                require(isOrdered(this, 2)) { "Provided endpoint URLs are incorrectly numbered." }
            }
            context.keys.filter { PROTOCOL_VERSION.format("[0-9]+").toRegex().matches(it) }.apply {
                require(isNotEmpty()) { "No endpoint protocol was provided." }
                require(isOrdered(this, 2)) { "Provided endpoint protocols are incorrectly numbered." }
            }
            context.keys.filter { TRUSTSTORE_SESSION.format("[0-9]+").toRegex().matches(it) }.apply {
                require(isNotEmpty()) { "No session trust store was provided." }
                require(isOrdered(this, 4)) { "Provided session trust stores are incorrectly numbered." }
            }
            context.keys.filter { TRUSTSTORE_TLS.format("[0-9]+").toRegex().matches(it) }.apply {
                require(isNotEmpty()) { "No TLS trust store was provided." }
                require(isOrdered(this, 4)) { "Provided TLS trust stores are incorrectly numbered." }
            }
        }

        /**
         * Checks if [keys] are numbered correctly (0, 1, ..., n).
         *
         * @param keys List of property keys to validate.
         * @param position Position of numbering in each of the provided [keys]. For example, [position] is 2 in
         * "corda.endpoints.0.connectionURL".
         */
        private fun isOrdered(keys: List<String>, position: Int): Boolean =
            keys.map { it.split(".")[position].toInt() }
                .sorted()
                .run {
                    indices.forEach { index ->
                        if (this[index] != index) return false
                    }
                    true
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

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when(event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent(coordinator)
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling start event.")
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
            )
        )
    }

    private fun handleStopEvent(coordinator: LifecycleCoordinator) {
        logger.info("Handling stop event.")
        deactivate(coordinator)
        componentHandle?.close()
        componentHandle = null
        configHandle?.close()
        configHandle = null
        _publisher?.close()
        _publisher = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        logger.info("Handling registration changed event.")
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                )
            }
            else -> {
                deactivate(coordinator)
                configHandle?.close()
            }
        }
    }

    // re-creates the publisher with the new config, sets the lifecycle status to UP when the publisher is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        logger.info("Handling config changed event.")
        _publisher?.close()
        _publisher = publisherFactory.createPublisher(
            PublisherConfig("mgm-registration-service"),
            event.config.getConfig(MESSAGING_CONFIG)
        )
        _publisher?.start()
        activate(coordinator)
    }
}
