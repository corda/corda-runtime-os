package net.corda.membership.impl.persistence.service

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
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
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.membership.datamodel.ApprovalRulesEntityPrimaryKey
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.MembershipEntities
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.dummy.TestVirtualNodeInfoReadService
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_DECLINED
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.toMap
import net.corda.membership.lib.toSortedMap
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipPersistenceResult
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.persistence.service.MembershipPersistenceService
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.test.util.TestRandom
import net.corda.test.util.eventually
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.utilities.seconds
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.calculateHash
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.NotaryInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MembershipPersistenceTest {
    companion object {

        private const val EPOCH_KEY = "corda.epoch"
        private const val MPV_KEY = "corda.minimumPlatformVersion"
        private const val MODIFIED_TIME_KEY = "corda.modifiedTime"

        private const val RULE_ID = "rule-id"
        private const val RULE_REGEX = "rule-regex"
        private const val RULE_LABEL = "rule-label"

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

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
        lateinit var membershipQueryClient: MembershipQueryClient

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

        @InjectService(timeout = 5000)
        lateinit var allowedCertificatesReaderWriterService: AllowedCertificatesReaderWriterService

        @InjectService(timeout = 5000)
        lateinit var keyEncodingService: KeyEncodingService

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
                groupPolicy: LayeredPropertyMap,
            ) = safeCall {
                membershipPersistenceClient.persistGroupPolicy(viewOwningIdentity, groupPolicy)
            }

            override fun persistGroupParameters(
                viewOwningIdentity: HoldingIdentity,
                groupParameters: GroupParameters
            ) = safeCall {
                membershipPersistenceClient.persistGroupParameters(viewOwningIdentity, groupParameters)
            }

            override fun persistGroupParametersInitialSnapshot(
                viewOwningIdentity: HoldingIdentity
            ) = safeCall {
                membershipPersistenceClient.persistGroupParametersInitialSnapshot(viewOwningIdentity)
            }

            override fun addNotaryToGroupParameters(
                viewOwningIdentity: HoldingIdentity,
                notary: MemberInfo
            ) = safeCall {
                membershipPersistenceClient.addNotaryToGroupParameters(viewOwningIdentity, notary)
            }

            override fun persistRegistrationRequest(
                viewOwningIdentity: HoldingIdentity,
                registrationRequest: RegistrationRequest
            ) = safeCall {
                membershipPersistenceClient.persistRegistrationRequest(viewOwningIdentity, registrationRequest)
            }

            override fun setMemberAndRegistrationRequestAsApproved(
                viewOwningIdentity: HoldingIdentity,
                approvedMember: HoldingIdentity,
                registrationRequestId: String,
            ) = safeCall {
                membershipPersistenceClient.setMemberAndRegistrationRequestAsApproved(
                    viewOwningIdentity, approvedMember, registrationRequestId
                )
            }

            override fun setMemberAndRegistrationRequestAsDeclined(
                viewOwningIdentity: HoldingIdentity,
                declinedMember: HoldingIdentity,
                registrationRequestId: String
            ) = safeCall {
                membershipPersistenceClient.setMemberAndRegistrationRequestAsDeclined(
                    viewOwningIdentity, declinedMember, registrationRequestId
                )
            }

            override fun setRegistrationRequestStatus(
                viewOwningIdentity: HoldingIdentity,
                registrationId: String,
                registrationRequestStatus: RegistrationStatus,
                reason: String?,
            ) = safeCall {
                membershipPersistenceClient.setRegistrationRequestStatus(
                    viewOwningIdentity, registrationId, registrationRequestStatus, reason
                )
            }

            override fun mutualTlsAddCertificateToAllowedList(
                mgmHoldingIdentity: HoldingIdentity,
                subject: String,
            ) = safeCall {
                membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                    mgmHoldingIdentity, subject
                )
            }

            override fun mutualTlsRemoveCertificateFromAllowedList(
                mgmHoldingIdentity: HoldingIdentity,
                subject: String,
            ) = safeCall {
                membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                    mgmHoldingIdentity, subject
                )
            }

            override fun generatePreAuthToken(
                mgmHoldingIdentity: HoldingIdentity,
                preAuthTokenId: UUID,
                ownerX500Name: MemberX500Name,
                ttl: Instant?,
                remarks: String?
            )= safeCall {
                membershipPersistenceClient.generatePreAuthToken(
                    mgmHoldingIdentity, preAuthTokenId, ownerX500Name, ttl, remarks
                )
            }

            override fun consumePreAuthToken(
                mgmHoldingIdentity: HoldingIdentity,
                ownerX500Name: MemberX500Name,
                preAuthTokenId: UUID
            )= safeCall {
                membershipPersistenceClient.consumePreAuthToken(
                    mgmHoldingIdentity, ownerX500Name, preAuthTokenId
                )
            }

            override fun revokePreAuthToken(
                mgmHoldingIdentity: HoldingIdentity,
                preAuthTokenId: UUID,
                remarks: String?
            ) = safeCall {
                membershipPersistenceClient.revokePreAuthToken(mgmHoldingIdentity, preAuthTokenId, remarks)
            }

            override fun addApprovalRule(
                viewOwningIdentity: HoldingIdentity,
                ruleParams: ApprovalRuleParams
            ) = safeCall {
                membershipPersistenceClient.addApprovalRule(
                    viewOwningIdentity, ruleParams
                )
            }

            override fun deleteApprovalRule(
                viewOwningIdentity: HoldingIdentity,
                ruleId: String,
                ruleType: ApprovalRuleType
            ) = safeCall {
                membershipPersistenceClient.deleteApprovalRule(
                    viewOwningIdentity, ruleId, ruleType
                )
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
        private val viewOwningHoldingIdentity = HoldingIdentity(x500Name, groupId)
        private val holdingIdentityShortHash = viewOwningHoldingIdentity.shortHash

        private val registeringX500Name = MemberX500Name.parse("O=Bob, C=GB, L=London")
        private val registeringHoldingIdentity = HoldingIdentity(registeringX500Name, groupId)

        private val vnodeDbInfo = TestDbInfo(VirtualNodeDbType.VAULT.getConnectionName(holdingIdentityShortHash), DbSchema.VNODE)
//        private val vnodeDbInfo = TestDbInfo("vnode_vault_$holdingIdentityShortHash", DbSchema.VNODE)
        private val clusterDbInfo = TestDbInfo.createConfig()

        private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()
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
            allowedCertificatesReaderWriterService.start()

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
                CpiIdentifier("PLACEHOLDER", "PLACEHOLDER", TestRandom.secureHash()),
                vaultDmlConnectionId = connectionID,
                cryptoDmlConnectionId = connectionID,
                uniquenessDmlConnectionId = connectionID,
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
        val status = RegistrationStatus.SENT_TO_MGM

        val result = membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                RegistrationStatus.SENT_TO_MGM,
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
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(byteArrayOf()),
                    ByteBuffer.wrap(byteArrayOf()),
                    KeyValuePairList(emptyList()),
                ),
            )
        )

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.registrationId).isEqualTo(registrationId)
        assertThat(persistedEntity.holdingIdentityShortHash).isEqualTo(registeringHoldingIdentity.shortHash.value)
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
        val groupPolicy1 = layeredPropertyMapFactory.createMap(mapOf("aa" to "BBB"))
        val persisted1 = membershipPersistenceClientWrapper.persistGroupPolicy(viewOwningHoldingIdentity, groupPolicy1)
        assertThat(persisted1).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val version1 = (persisted1 as? MembershipPersistenceResult.Success<Int>)?.payload !!

        val groupPolicy2 = layeredPropertyMapFactory.createMap(mapOf("aa" to "BBB1"))
        val persisted2 = membershipPersistenceClientWrapper.persistGroupPolicy(viewOwningHoldingIdentity, groupPolicy2)
        assertThat(persisted2).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val version2 = (persisted2 as? MembershipPersistenceResult.Success<Int>)?.payload
        assertThat(version2).isEqualTo(version1 + 1)
    }

    @Test
    fun `persistGroupParametersInitialSnapshot can persist over RPC topic`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
        }
        val persisted = membershipPersistenceClientWrapper.persistGroupParametersInitialSnapshot(viewOwningHoldingIdentity)
        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.use {
            it.find(
                GroupParametersEntity::class.java,
                1
            )
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.epoch).isEqualTo(1)
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!.toMap()
            assertThat(deserialized.size).isEqualTo(3)
            assertThat(deserialized[EPOCH_KEY]).isEqualTo("1")
            assertDoesNotThrow { Instant.parse(deserialized[MODIFIED_TIME_KEY]) }
            assertThat(deserialized[MPV_KEY]).isEqualTo("5000")
        }
    }

    @Test
    fun `persistGroupParameters can persist over RPC topic`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                1,
                cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "1"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                            KeyValuePair(MPV_KEY, "5000")
                        )
                    )
                )!!
            )
            it.persist(entity)
        }
        val groupParameters = layeredPropertyMapFactory.create<TestGroupParametersImpl>(mapOf(
            EPOCH_KEY to "2",
            MPV_KEY to "5000",
            MODIFIED_TIME_KEY to clock.instant().toString()
        ))
        val persisted = membershipPersistenceClientWrapper.persistGroupParameters(viewOwningHoldingIdentity, groupParameters)
        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.use {
            it.find(
                GroupParametersEntity::class.java,
                2
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!.toMap()
            assertThat(deserialized.size).isEqualTo(3)
            assertThat(deserialized[EPOCH_KEY]).isEqualTo("2")
            assertDoesNotThrow { Instant.parse(deserialized[MODIFIED_TIME_KEY]) }
            assertThat(deserialized[MPV_KEY]).isEqualTo("5000")
        }
    }

    @Test
    fun `addNotaryToGroupParameters can persist new notary service over RPC topic`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                50,
                cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "50"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                            KeyValuePair(MPV_KEY, "5000")
                        )
                    )
                )!!
            )
            it.persist(entity)
        }

        val groupId = randomUUID().toString()
        val memberx500Name = MemberX500Name.parse("O=Notary, C=GB, L=London")
        val endpointUrl = "https://localhost:8080"
        val notaryServiceName = "O=New Service, L=London, C=GB"
        val notaryServicePlugin = "Notary Plugin"
        val notaryKey = with(KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())) {
            generateKeyPair().public
        }
        val notaryKeyHash = notaryKey.calculateHash()
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(String.format(URL_KEY, "0"), endpointUrl),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(GROUP_ID, groupId),
                KeyValuePair(PARTY_NAME, memberx500Name.toString()),
                KeyValuePair(PLATFORM_VERSION, "11"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0"),
                KeyValuePair("corda.notary.service.name", notaryServiceName),
                KeyValuePair("corda.notary.service.plugin", notaryServicePlugin),
                KeyValuePair("corda.roles.0", "notary"),
                KeyValuePair("corda.notary.keys.0.pem", keyEncodingService.encodeAsString(notaryKey)),
                KeyValuePair("corda.notary.keys.0.signature.spec", "SHA512withECDSA"),
                KeyValuePair("corda.notary.keys.0.hash", notaryKeyHash.value),
            ).sorted()
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )
        val notary = memberInfoFactory.create(memberContext.toSortedMap(), mgmContext.toSortedMap())
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, "51"),
            KeyValuePair(MPV_KEY, "5000"),
            KeyValuePair("corda.notary.service.0.name", notaryServiceName),
            KeyValuePair("corda.notary.service.0.plugin", notaryServicePlugin),
            KeyValuePair("corda.notary.service.0.keys.0", keyEncodingService.encodeAsString(notaryKey)),
        )

        val persisted = membershipPersistenceClientWrapper.addNotaryToGroupParameters(viewOwningHoldingIdentity, notary)

        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        with((persisted as? MembershipPersistenceResult.Success<KeyValuePairList>)!!.payload.items) {
            assertThat(size).isEqualTo(6)
            assertThat(containsAll(expectedGroupParameters))
        }

        val persistedEntity = vnodeEmf.use {
            it.find(
                GroupParametersEntity::class.java,
                51
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            assertThat(deserialized.items.size).isEqualTo(6)
            assertThat(deserialized.items.containsAll(expectedGroupParameters))
            assertDoesNotThrow { Instant.parse(deserialized.toMap()[MODIFIED_TIME_KEY]) }
        }
    }

    @Test
    fun `addNotaryToGroupParameters can persist notary to existing notary service over RPC topic`() {
        val groupId = randomUUID().toString()
        val memberx500Name = MemberX500Name.parse("O=Notary, C=GB, L=London")
        val endpointUrl = "http://localhost:8080"
        val notaryServiceName = "O=New Service, L=London, C=GB"
        val notaryServicePlugin = "Notary Plugin"
        val notaryKey = with(KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())) {
            generateKeyPair().public
        }
        val notaryKeyAsString = keyEncodingService.encodeAsString(notaryKey)
        val notaryKeyHash = notaryKey.calculateHash()
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(String.format(URL_KEY, "0"), endpointUrl),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(GROUP_ID, groupId),
                KeyValuePair(PARTY_NAME, memberx500Name.toString()),
                KeyValuePair(PLATFORM_VERSION, "11"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0"),
                KeyValuePair("corda.notary.service.name", notaryServiceName),
                KeyValuePair("corda.notary.service.plugin", notaryServicePlugin),
                KeyValuePair("corda.roles.0", "notary"),
                KeyValuePair("corda.notary.keys.0.pem", notaryKeyAsString),
                KeyValuePair("corda.notary.keys.0.signature.spec", "SHA512withECDSA"),
                KeyValuePair("corda.notary.keys.0.hash", notaryKeyHash.value),
            ).sorted()
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )
        val notary = memberInfoFactory.create(memberContext.toSortedMap(), mgmContext.toSortedMap())
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                100,
                cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "100"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                            KeyValuePair(MPV_KEY, "5000"),
                            KeyValuePair("corda.notary.service.0.name", notaryServiceName),
                            KeyValuePair("corda.notary.service.0.plugin", notaryServicePlugin)
                            )
                        )
                    )!!
                )
            it.persist(entity)
        }
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, "101"),
            KeyValuePair(MPV_KEY, "5000"),
            KeyValuePair("corda.notary.service.0.name", notaryServiceName),
            KeyValuePair("corda.notary.service.0.plugin", notaryServicePlugin),
            KeyValuePair("corda.notary.service.0.keys.0", notaryKeyAsString),
        )

        val persisted = membershipPersistenceClientWrapper.addNotaryToGroupParameters(viewOwningHoldingIdentity, notary)

        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        with((persisted as? MembershipPersistenceResult.Success<KeyValuePairList>)!!.payload.items) {
            assertThat(size).isEqualTo(6)
            assertThat(containsAll(expectedGroupParameters))
        }

        val persistedEntity = vnodeEmf.use {
            it.find(
                GroupParametersEntity::class.java,
                101
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            assertThat(deserialized.items.size).isEqualTo(6)
            assertThat(deserialized.items.containsAll(expectedGroupParameters))
            assertDoesNotThrow { Instant.parse(deserialized.toMap()[MODIFIED_TIME_KEY]) }
        }
    }

    @Test
    fun `addNotaryToGroupParameters can persist notary with rotated keys over RPC topic`() {
        val keyGenerator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        val groupId = randomUUID().toString()
        val memberx500Name = MemberX500Name.parse("O=Notary, C=GB, L=London")
        val endpointUrl = "http://localhost:8080"
        val notaryServiceName = "O=New Service, L=London, C=GB"
        val notaryServicePlugin = "Notary Plugin"
        val notaryKey = with(keyGenerator) {
            generateKeyPair().public
        }
        val notaryKeyAsString = keyEncodingService.encodeAsString(notaryKey)
        val notaryKeyHash = notaryKey.calculateHash()
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(String.format(URL_KEY, "0"), endpointUrl),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(GROUP_ID, groupId),
                KeyValuePair(PARTY_NAME, memberx500Name.toString()),
                KeyValuePair(PLATFORM_VERSION, "11"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0"),
                KeyValuePair("corda.notary.service.name", notaryServiceName),
                KeyValuePair("corda.notary.service.plugin", notaryServicePlugin),
                KeyValuePair("corda.roles.0", "notary"),
                KeyValuePair("corda.notary.keys.0.pem", notaryKeyAsString),
                KeyValuePair("corda.notary.keys.0.signature.spec", "SHA512withECDSA"),
                KeyValuePair("corda.notary.keys.0.hash", notaryKeyHash.value),
            ).sorted()
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )
        val notary = memberInfoFactory.create(memberContext.toSortedMap(), mgmContext.toSortedMap())
        val oldNotaryKey = with(keyGenerator) {
            keyEncodingService.encodeAsString(generateKeyPair().public)
        }
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                150,
                cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "150"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                            KeyValuePair(MPV_KEY, "5000"),
                            KeyValuePair("corda.notary.service.0.name", notaryServiceName),
                            KeyValuePair("corda.notary.service.0.plugin", notaryServicePlugin),
                            KeyValuePair("corda.notary.service.0.keys.0", oldNotaryKey)
                        )
                    )
                )!!
            )
            it.persist(entity)
        }
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, "151"),
            KeyValuePair(MPV_KEY, "5000"),
            KeyValuePair("corda.notary.service.0.name", notaryServiceName),
            KeyValuePair("corda.notary.service.0.plugin", notaryServicePlugin),
            KeyValuePair("corda.notary.service.0.keys.0", oldNotaryKey),
            KeyValuePair("corda.notary.service.0.keys.1", notaryKeyAsString),
        )

        val persisted = membershipPersistenceClientWrapper.addNotaryToGroupParameters(viewOwningHoldingIdentity, notary)

        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        with((persisted as? MembershipPersistenceResult.Success<KeyValuePairList>)!!.payload.items) {
            assertThat(size).isEqualTo(7)
            assertThat(containsAll(expectedGroupParameters))
        }

        val persistedEntity = vnodeEmf.use {
            it.find(
                GroupParametersEntity::class.java,
                151
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            assertThat(deserialized.items.size).isEqualTo(7)
            assertThat(deserialized.items.containsAll(expectedGroupParameters))
            assertDoesNotThrow { Instant.parse(deserialized.toMap()[MODIFIED_TIME_KEY]) }
        }
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
                KeyValuePair(PLATFORM_VERSION, "5000"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0"),
            ).sorted()
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
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
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId, memberx500Name.toString()
                )
            )
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.groupId).isEqualTo(groupId)
        assertThat(persistedEntity.memberX500Name).isEqualTo(memberx500Name.toString())
        assertThat(persistedEntity.serialNumber).isEqualTo(1)
        assertThat(persistedEntity.status).isEqualTo(MEMBER_STATUS_ACTIVE)

        val persistedMgmContext = persistedEntity.mgmContext.deserializeContextAsMap()
        assertThat(persistedMgmContext)
            .containsEntry(STATUS, MEMBER_STATUS_ACTIVE)
            .containsEntry(SERIAL, "1")

        val persistedMemberContext = persistedEntity.memberContext.deserializeContextAsMap()
        assertThat(persistedMemberContext)
            .containsEntry(String.format(URL_KEY, "0"), endpointUrl)
            .containsEntry(String.format(PROTOCOL_VERSION, "0"), "1")
            .containsEntry(GROUP_ID, groupId)
            .containsEntry(PARTY_NAME, memberx500Name.toString())
            .containsEntry(PLATFORM_VERSION, "5000")
            .containsEntry(SOFTWARE_VERSION, "5.0.0")
    }

    @Test
    fun `setMemberAndRegistrationRequestAsApproved update the member and registration request`() {
        // 1. Persist a member
        val memberPersistentResult = persistMember(registeringX500Name)

        assertThat(memberPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val memberEntity = vnodeEmf.use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId, registeringX500Name.toString()
                )
            )
        }
        assertThat(memberEntity.status).isEqualTo(MEMBER_STATUS_PENDING)

        // 2. Persist a request
        val registrationId = randomUUID().toString()
        val requestPersistentResult = persistRequest(registeringHoldingIdentity, registrationId)

        assertThat(requestPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val requestEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(requestEntity.status).isEqualTo(RegistrationStatus.SENT_TO_MGM.toString())

        val approveResult = membershipPersistenceClientWrapper.setMemberAndRegistrationRequestAsApproved(
            viewOwningHoldingIdentity,
            registeringHoldingIdentity,
            registrationId,
        ).getOrThrow()

        assertThat(approveResult.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        assertThat(approveResult.groupId).isEqualTo(groupId)
        assertThat(approveResult.name).isEqualTo(registeringHoldingIdentity.x500Name)
        val newMemberEntity = vnodeEmf.use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId, registeringX500Name.toString()
                )
            )
        }
        assertThat(newMemberEntity.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        val newRequestEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(newRequestEntity.status).isEqualTo(RegistrationStatus.APPROVED.toString())
    }

    @Test
    fun `setMemberAndRegistrationRequestAsDeclined updates the member and registration request`() {
        // 1. Persist a member
        val registeringX500Name = MemberX500Name.parse("O=Charlie, C=GB, L=London")
        val registeringHoldingIdentity = HoldingIdentity(registeringX500Name, groupId)
        val memberPersistentResult = persistMember(registeringX500Name)

        assertThat(memberPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val memberEntity = vnodeEmf.use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId, registeringX500Name.toString()
                )
            )
        }
        assertThat(memberEntity.status).isEqualTo(MEMBER_STATUS_PENDING)

        // 2. Persist a request
        val registrationId = randomUUID().toString()
        val requestPersistentResult = persistRequest(registeringHoldingIdentity, registrationId)

        assertThat(requestPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val requestEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(requestEntity.status).isEqualTo(RegistrationStatus.SENT_TO_MGM.toString())

        membershipPersistenceClientWrapper.setMemberAndRegistrationRequestAsDeclined(
            viewOwningHoldingIdentity,
            registeringHoldingIdentity,
            registrationId,
        ).getOrThrow()

        val newMemberEntity = vnodeEmf.use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId, registeringX500Name.toString()
                )
            )
        }
        assertThat(newMemberEntity.status).isEqualTo(MEMBER_STATUS_DECLINED)
        val newRequestEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(newRequestEntity.status).isEqualTo(RegistrationStatus.DECLINED.toString())
    }

    @Test
    fun `queryMembersSignatures returns the member signatures`() {
        membershipQueryClient.start()
        eventually {
            assertThat(membershipPersistenceClient.isRunning).isTrue
        }

        val signatures = (1..5).associate { index ->
            val registrationId = randomUUID().toString()
            val holdingId = createTestHoldingIdentity("O=Bob-$index, C=GB, L=London", groupId)
            val publicKey = ByteBuffer.wrap("pk-$index".toByteArray())
            val signature = ByteBuffer.wrap("signature-$index".toByteArray())
            val context = KeyValuePairList(
                listOf(
                    KeyValuePair(MEMBER_CONTEXT_KEY, MEMBER_CONTEXT_VALUE)
                )
            )
            val signatureContext = KeyValuePairList(
                listOf(
                    KeyValuePair("key", "value")
                )
            )
            membershipPersistenceClientWrapper.persistRegistrationRequest(
                viewOwningHoldingIdentity,
                RegistrationRequest(
                    RegistrationStatus.SENT_TO_MGM,
                    registrationId,
                    holdingId,
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            context
                        )
                    ),
                    CryptoSignatureWithKey(
                        publicKey,
                        signature,
                        signatureContext,
                    )
                )
            ).getOrThrow()
            val cryptoSignatureWithKey = CryptoSignatureWithKey(
                publicKey, signature, signatureContext
            )
            holdingId to cryptoSignatureWithKey
        }

        val results = membershipQueryClient.queryMembersSignatures(
            viewOwningHoldingIdentity,
            signatures.keys
        ).getOrThrow()

        assertThat(results).containsAllEntriesOf(signatures)
    }

    @Test
    fun `setRegistrationRequestStatus updates the registration request status`() {
        val registrationId = randomUUID().toString()
        val persistRegRequestResult = membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                RegistrationStatus.SENT_TO_MGM,
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
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(byteArrayOf()),
                    ByteBuffer.wrap(byteArrayOf()),
                    KeyValuePairList(emptyList()),
                ),
            )
        )

        assertThat(persistRegRequestResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.registrationId).isEqualTo(registrationId)
        assertThat(persistedEntity.holdingIdentityShortHash).isEqualTo(registeringHoldingIdentity.shortHash.value)
        assertThat(persistedEntity.status).isEqualTo(RegistrationStatus.SENT_TO_MGM.name)

        val updateRegRequestStatusResult = membershipPersistenceClientWrapper.setRegistrationRequestStatus(
            viewOwningHoldingIdentity,
            registrationId,
            RegistrationStatus.PENDING_AUTO_APPROVAL
        )

        assertThat(updateRegRequestStatusResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val updatedEntity = vnodeEmf.use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(updatedEntity).isNotNull
        assertThat(updatedEntity.registrationId).isEqualTo(registrationId)
        assertThat(updatedEntity.holdingIdentityShortHash).isEqualTo(registeringHoldingIdentity.shortHash.value)
        assertThat(updatedEntity.status).isEqualTo(RegistrationStatus.PENDING_AUTO_APPROVAL.name)
    }

    @Test
    fun `addApprovalRule persists the approval rule and returns the rule ID`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM ApprovalRulesEntity").executeUpdate()
        }
        val ruleDetails = membershipPersistenceClientWrapper.addApprovalRule(
            viewOwningHoldingIdentity,
            ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL)
        ).getOrThrow()

        val approvalRuleEntity = vnodeEmf.use {
            it.find(
                ApprovalRulesEntity::class.java,
                ApprovalRulesEntityPrimaryKey(
                    ruleDetails.ruleId,
                    ApprovalRuleType.STANDARD.name
                )
            )
        }
        with(approvalRuleEntity) {
            assertThat(ruleRegex).isEqualTo(RULE_REGEX)
            assertThat(ruleType).isEqualTo(ApprovalRuleType.STANDARD.name)
            assertThat(ruleLabel).isEqualTo(RULE_LABEL)
        }
    }

    @Test
    fun `deleteApprovalRule deletes the approval rule from the db`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM ApprovalRulesEntity").executeUpdate()
        }
        val testRule = ApprovalRulesEntity(RULE_ID, ApprovalRuleType.STANDARD.name, RULE_REGEX, RULE_LABEL)
        vnodeEmf.transaction {
            it.persist(testRule)
        }

        membershipPersistenceClientWrapper.deleteApprovalRule(
            viewOwningHoldingIdentity, RULE_ID, ApprovalRuleType.STANDARD
        ).getOrThrow()

        vnodeEmf.use {
            assertThat(
                it.find(
                    ApprovalRulesEntity::class.java,
                    ApprovalRulesEntityPrimaryKey(
                        RULE_ID,
                        ApprovalRuleType.STANDARD.name
                    )
                )
            ).isNull()
        }
    }

    @Test
    fun `getApprovalRules retrieves all approval rules`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM ApprovalRulesEntity").executeUpdate()
        }
        membershipQueryClient.start()
        eventually {
            assertThat(membershipPersistenceClient.isRunning).isTrue
        }
        val rule1 = ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL)
        val rule2 = ApprovalRuleDetails("rule-id-2", "rule-regex-2", "rule-label-2")
        val entities = listOf(
            ApprovalRulesEntity(rule1.ruleId, ApprovalRuleType.STANDARD.name, rule1.ruleRegex, rule1.ruleLabel),
            ApprovalRulesEntity(rule2.ruleId, ApprovalRuleType.STANDARD.name, rule2.ruleRegex, rule2.ruleLabel)
        )
        vnodeEmf.transaction { em ->
            entities.forEach { em.persist(it) }
        }

        val result = membershipQueryClient.getApprovalRules(
            viewOwningHoldingIdentity,
            ApprovalRuleType.STANDARD
        ).getOrThrow()

        assertThat(result.size).isEqualTo(2)
        assertThat(result).containsAll(listOf(rule1, rule2))
    }

    @Test
    fun `queryRegistrationRequests retrieves the expected registration requests`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM RegistrationRequestEntity").executeUpdate()
        }
        membershipQueryClient.start()
        eventually {
            assertThat(membershipPersistenceClient.isRunning).isTrue
        }
        // Persist a request pending manual approval
        val registrationId1 = randomUUID().toString()
        val requestPersistentResult = persistRequest(registeringHoldingIdentity, registrationId1, RegistrationStatus.PENDING_MANUAL_APPROVAL)
        assertThat(requestPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        // Persist a completed request
        val registrationId2 = randomUUID().toString()
        val requestPersistentResult2 = persistRequest(viewOwningHoldingIdentity, registrationId2, RegistrationStatus.DECLINED)
        assertThat(requestPersistentResult2).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        // Persist a new request
        val registrationId3 = randomUUID().toString()
        val requestPersistentResult3 = persistRequest(
            HoldingIdentity(MemberX500Name.parse("O=Charlie, C=GB, L=London"), groupId), registrationId3
        )
        assertThat(requestPersistentResult3).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val result1 = membershipQueryClient.queryRegistrationRequestsStatus(
            viewOwningHoldingIdentity,
            null,
            listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL, RegistrationStatus.APPROVED, RegistrationStatus.DECLINED)
        ).getOrThrow()
        assertThat(result1.map { it.registrationId }).containsAll(listOf(registrationId1, registrationId2))

        val result2 = membershipQueryClient.queryRegistrationRequestsStatus(
            viewOwningHoldingIdentity,
            viewOwningHoldingIdentity.x500Name,
            listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL, RegistrationStatus.APPROVED, RegistrationStatus.DECLINED)
        ).getOrThrow()
        assertThat(result2.map { it.registrationId }).containsAll(listOf(registrationId2))

        val result3 = membershipQueryClient.queryRegistrationRequestsStatus(
            viewOwningHoldingIdentity,
            null,
            listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        ).getOrThrow()
        assertThat(result3.map { it.registrationId }).containsAll(listOf(registrationId1))

        val result4 = membershipQueryClient.queryRegistrationRequestsStatus(viewOwningHoldingIdentity).getOrThrow()
        assertThat(result4.map { it.registrationId }).containsAll(listOf(registrationId1, registrationId2, registrationId3))
    }

    private fun ByteArray.deserializeContextAsMap(): Map<String, String> =
        cordaAvroDeserializer.deserialize(this)
            ?.items
            ?.associate { it.key to it.value } ?: fail("Failed to deserialize context.")

    private fun persistMember(memberName: MemberX500Name): MembershipPersistenceResult<Unit> {
        val endpointUrl = "http://localhost:8080"
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(String.format(URL_KEY, "0"), endpointUrl),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(GROUP_ID, groupId),
                KeyValuePair(PARTY_NAME, memberName.toString()),
                KeyValuePair(PLATFORM_VERSION, "5000"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0"),
            ).sorted()
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_PENDING),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )

        return membershipPersistenceClientWrapper.persistMemberInfo(
            viewOwningHoldingIdentity,
            listOf(
                memberInfoFactory.create(
                    memberContext.toSortedMap(),
                    mgmContext.toSortedMap()
                )
            )
        )
    }

    private fun persistRequest(
        member: HoldingIdentity,
        registrationId: String,
        status: RegistrationStatus = RegistrationStatus.SENT_TO_MGM,
    ): MembershipPersistenceResult<Unit> {
        return membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                status,
                registrationId,
                member,
                ByteBuffer.wrap(
                    cordaAvroSerializer.serialize(
                        KeyValuePairList(
                            listOf(
                                KeyValuePair(MEMBER_CONTEXT_KEY, MEMBER_CONTEXT_VALUE)
                            )
                        )
                    )
                ),
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(byteArrayOf()),
                    ByteBuffer.wrap(byteArrayOf()),
                    KeyValuePairList(emptyList()),
                ),
            )
        )
    }

    private class TestGroupParametersImpl(
        private val map: LayeredPropertyMap
    ) : LayeredPropertyMap by map, GroupParameters {
        override fun getEpoch() = 5
        override fun getMinimumPlatformVersion() = 5000
        override fun getModifiedTime() = clock.instant()
        override fun getNotaries(): List<NotaryInfo> = emptyList()
    }
}
