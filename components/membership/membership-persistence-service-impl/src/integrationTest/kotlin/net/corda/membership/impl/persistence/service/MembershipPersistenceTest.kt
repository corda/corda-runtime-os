package net.corda.membership.impl.persistence.service

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.MembershipEntities
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.impl.persistence.service.dummy.TestVirtualNodeInfoReadService
import net.corda.membership.lib.impl.toSortedMap
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.service.MembershipPersistenceService
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.use
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.eventually
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.v5.membership.GroupPolicyProperties
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID.randomUUID
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MembershipPersistenceTest {
    companion object {

        private val logger = contextLogger()

        private const val BOOT_CONFIG_STRING = """
            $INSTANCE_ID = 1
            $BUS_TYPE = INMEMORY
        """
        private const val MEMBER_CONTEXT_KEY = "key"
        private const val MEMBER_CONTEXT_VALUE = "value"
        private const val messagingConf = """
            componentVersion="5.1"
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
            }
        """
        private val schemaVersion = ConfigurationSchemaVersion(1, 0)

        @InjectService(timeout = 5000)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var memberInfoFactory: MemberInfoFactory

        @InjectService(timeout = 5000)
        lateinit var membershipPersistenceService: MembershipPersistenceService

        @InjectService(timeout = 5000)
        lateinit var membershipPersistenceClient: MembershipPersistenceClient

        @InjectService(timeout = 5000)
        lateinit var configurationReadService: ConfigurationReadService

        @InjectService(timeout = 5000)
        lateinit var dbConnectionManager: DbConnectionManager

        @InjectService(timeout = 5000)
        lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000)
        lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

        @InjectService(timeout = 5000)
        lateinit var virtualNodeReadService: TestVirtualNodeInfoReadService

        @InjectService(timeout = 5000)
        lateinit var layeredPropertyMapFactory: LayeredPropertyMapFactory

        /**
         * Wrapper class which allows the client to wait until the underlying DB message bus has been set up correctly with partitions required.
         * Without this the client often tries to send RPC requests before the service has set up the kafka topics required
         * for the DB message bus.
         */
        val membershipPersistenceClientWrapper = object : MembershipPersistenceClient {
            override fun persistMemberInfo(
                viewOwningIdentity: HoldingIdentity,
                memberInfos: Collection<MemberInfo>
            ) = safeCall {
                membershipPersistenceClient.persistMemberInfo(viewOwningIdentity, memberInfos)
            }

            override fun persistGroupPolicy(
                viewOwningIdentity: HoldingIdentity,
                groupPolicy: GroupPolicyProperties,
            ) = safeCall {
                membershipPersistenceClient.persistGroupPolicy(viewOwningIdentity, groupPolicy)
            }

            override fun persistRegistrationRequest(
                viewOwningIdentity: HoldingIdentity,
                registrationRequest: RegistrationRequest
            ) = safeCall {
                membershipPersistenceClient.persistRegistrationRequest(viewOwningIdentity, registrationRequest)
            }

            fun <T> safeCall(func: () -> T): T {
                return eventually {
                    assertDoesNotThrow {
                        func()
                    }
                }
            }

            override val isRunning get() = membershipPersistenceClient.isRunning
            override fun start() = membershipPersistenceClient.start()
            override fun stop() = membershipPersistenceClient.stop()

        }

        private val clock = TestClock(Instant.ofEpochSecond(0))

        private val groupId = randomUUID().toString()
        private val x500Name = MemberX500Name.parse("O=Alice, C=GB, L=London")
        private val viewOwningHoldingIdentity = HoldingIdentity(x500Name.toString(), groupId)
        private val holdingIdentityId: String = viewOwningHoldingIdentity.id

        private val registeringX500Name = MemberX500Name.parse("O=Bob, C=GB, L=London")
        private val registeringHoldingIdentity = HoldingIdentity(registeringX500Name.toString(), groupId)

        private val vnodeDbInfo = TestDbInfo("vnode_vault_$holdingIdentityId", DbSchema.VNODE)
        private val clusterDbInfo = TestDbInfo.createConfig()

        private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())
        private val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))
        private val dbConfig = smartConfigFactory.create(clusterDbInfo.config)

        private lateinit var vnodeEmf: EntityManagerFactory
        private lateinit var cordaAvroSerializer: CordaAvroSerializer<KeyValuePairList>
        private lateinit var cordaAvroDeserializer: CordaAvroDeserializer<KeyValuePairList>


        @JvmStatic
        @BeforeAll
        fun setUp() {
            val coordinator = lifecycleCoordinatorFactory.createCoordinator<MembershipPersistenceTest> { e, c ->
                when (e) {
                    is StartEvent -> {
                        logger.info("Starting test coordinator")
                        c.followStatusChangesByName(
                            setOf(
                                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                                LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
                                LifecycleCoordinatorName.forComponent<MembershipPersistenceService>(),
                            )
                        )
                    }
                    is RegistrationStatusChangeEvent -> {
                        logger.info("Test coordinator is ${e.status}")
                        c.updateStatus(e.status)
                    }
                    else -> {
                        logger.info("Received and ignored event $e.")
                    }
                }
            }
            coordinator.start()
            cordaAvroSerializer = cordaAvroSerializationFactory.createAvroSerializer { }
            cordaAvroDeserializer =
                cordaAvroSerializationFactory.createAvroDeserializer({ }, KeyValuePairList::class.java)
            val dbInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
            vnodeEmf = dbInstaller.setupDatabase(vnodeDbInfo, "vnode-vault", MembershipEntities.classes)
            dbInstaller.setupClusterDatabase(clusterDbInfo, "config", ConfigurationEntities.classes).close()

            entitiesRegistry.register(CordaDb.Vault.persistenceUnitName, MembershipEntities.classes)

            setupConfig()
            dbConnectionManager.startAndWait()
            dbConnectionManager.bootstrap(dbConfig)
            virtualNodeReadService.startAndWait()

            membershipPersistenceService.startAndWait()
            membershipPersistenceClientWrapper.startAndWait()

            eventually {
                logger.info("Waiting for required services to start...")
                assertEquals(LifecycleStatus.UP, coordinator.status)
                logger.info("Required services started.")
            }
            val connectionID = dbConnectionManager.putConnection(
                name = vnodeDbInfo.name,
                privilege = DbPrivilege.DML,
                config = vnodeDbInfo.config,
                description = null,
                updateActor = "sa"
            )
            val vnodeInfo = VirtualNodeInfo(
                viewOwningHoldingIdentity,
                CpiIdentifier("PLACEHOLDER", "PLACEHOLDER", null),
                vaultDmlConnectionId = connectionID,
                cryptoDmlConnectionId = connectionID,
                timestamp = clock.instant()
            )
            virtualNodeReadService.putVNodeInfo(vnodeInfo)
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            if (Companion::vnodeEmf.isInitialized) {
                vnodeEmf.close()
            }
            configurationReadService.stop()
            dbConnectionManager.stop()
            membershipPersistenceService.stop()
            membershipPersistenceClientWrapper.stop()
            virtualNodeReadService.stop()
        }

        private fun setupConfig() {
            val publisher = publisherFactory.createPublisher(PublisherConfig("clientId"), bootConfig)
            publisher.publish(
                listOf(
                    Record(
                        Schemas.Config.CONFIG_TOPIC,
                        ConfigKeys.MESSAGING_CONFIG,
                        Configuration(messagingConf, messagingConf, 0, schemaVersion)
                    )
                )
            )
            configurationReadService.startAndWait()
            configurationReadService.bootstrapConfig(bootConfig)
        }

        private fun Lifecycle.startAndWait() {
            start()
            eventually(5.seconds) {
                assertTrue(isRunning)
            }
        }
    }

    @Test
    fun `registration requests can persist over RPC topic`() {
        val registrationId = randomUUID().toString()
        val status = RegistrationStatus.NEW

        val result = membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                registrationId,
                registeringHoldingIdentity,
                ByteBuffer.wrap(
                    cordaAvroSerializer.serialize(
                        KeyValuePairList(
                            listOf(
                                KeyValuePair(MEMBER_CONTEXT_KEY, MEMBER_CONTEXT_VALUE)
                            )
                        )
                    )
                ),
                ByteBuffer.wrap(byteArrayOf()),
                ByteBuffer.wrap(byteArrayOf())
            )
        )

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.registrationId).isEqualTo(registrationId)
        assertThat(persistedEntity.holdingIdentityId).isEqualTo(registeringHoldingIdentity.id)
        assertThat(persistedEntity.status).isEqualTo(status.toString())

        val persistedMemberContext = persistedEntity.context.deserializeContextAsMap()
        with(persistedMemberContext.entries) {
            assertThat(size).isEqualTo(1)
            assertThat(first().key).isEqualTo(MEMBER_CONTEXT_KEY)
            assertThat(first().value).isEqualTo(MEMBER_CONTEXT_VALUE)
        }
    }
    @Test
    fun `persistGroupPolicy will increase the version every persistance`() {
        val groupPolicy1 = object : LayeredPropertyMap by layeredPropertyMapFactory.createMap(mapOf("aa" to "BBB")), GroupPolicyProperties {}
        val persisted1 = membershipPersistenceClientWrapper.persistGroupPolicy(viewOwningHoldingIdentity, groupPolicy1)
        assertThat(persisted1).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val version1 = (persisted1 as? MembershipPersistenceResult.Success<Int>)?.payload !!

        val groupPolicy2 = object : LayeredPropertyMap by layeredPropertyMapFactory.createMap(mapOf("aa" to "BBB1")), GroupPolicyProperties {}
        val persisted2 = membershipPersistenceClientWrapper.persistGroupPolicy(viewOwningHoldingIdentity, groupPolicy2)
        assertThat(persisted2).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val version2 = (persisted2 as? MembershipPersistenceResult.Success<Int>)?.payload
        assertThat(version2).isEqualTo(version1 + 1)
    }

    @Test
    fun `member infos can persist over RPC topic`() {
        val groupId = randomUUID().toString()
        val memberx500Name = MemberX500Name.parse("O=Alice, C=GB, L=London")
        val endpointUrl = "http://localhost:8080"
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(String.format(URL_KEY, "0"), endpointUrl),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(GROUP_ID, groupId),
                KeyValuePair(PARTY_NAME, memberx500Name.toString()),
                KeyValuePair(PLATFORM_VERSION, "11"),
                KeyValuePair(SERIAL, "1"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0")
            )
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE)
            )
        )

        val result = membershipPersistenceClientWrapper.persistMemberInfo(
            viewOwningHoldingIdentity,
            listOf(
                memberInfoFactory.create(
                    memberContext.toSortedMap(),
                    mgmContext.toSortedMap()
                )
            )
        )

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.use {
            it.find(
                MemberInfoEntity::class.java, MemberInfoEntityPrimaryKey(
                    groupId, memberx500Name.toString()
                )
            )
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.groupId).isEqualTo(groupId)
        assertThat(persistedEntity.memberX500Name).isEqualTo(memberx500Name.toString())
        assertThat(persistedEntity.serialNumber).isEqualTo(1)
        assertThat(persistedEntity.status).isEqualTo(MEMBER_STATUS_ACTIVE)

        fun contextIsEqual(actual: String?, expected: String) = assertThat(actual).isEqualTo(expected)

        val persistedMgmContext = persistedEntity.mgmContext.deserializeContextAsMap()
        contextIsEqual(persistedMgmContext[STATUS], MEMBER_STATUS_ACTIVE)

        val persistedMemberContext = persistedEntity.memberContext.deserializeContextAsMap()
        assertThat(persistedMemberContext)
            .containsEntry(String.format(URL_KEY, "0"), endpointUrl)
            .containsEntry(String.format(PROTOCOL_VERSION, "0"), "1")
            .containsEntry(GROUP_ID, groupId)
            .containsEntry(PARTY_NAME, memberx500Name.toString())
            .containsEntry(PLATFORM_VERSION, "11")
            .containsEntry(SERIAL, "1")
            .containsEntry(SOFTWARE_VERSION, "5.0.0")
    }

    private fun ByteArray.deserializeContextAsMap(): Map<String, String> =
        cordaAvroDeserializer.deserialize(this)
            ?.items
            ?.associate { it.key to it.value } ?: fail("Failed to deserialize context.")

}