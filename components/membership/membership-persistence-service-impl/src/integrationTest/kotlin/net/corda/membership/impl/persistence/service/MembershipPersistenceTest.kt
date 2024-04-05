package net.corda.membership.impl.persistence.service

import com.typesafe.config.ConfigFactory
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecs.RSA_SHA256
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.fullIdHash
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.StaticNetworkInfo
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.v2.RegistrationStatus
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
import net.corda.libs.packaging.hash
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
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.MembershipEntities
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.impl.persistence.service.dummy.TestVirtualNodeInfoReadService
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MPV_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_KEYS_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_PEM
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_KEY_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.holdingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequest
import net.corda.membership.lib.toMap
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
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
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
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.membership.NotaryInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
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
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
class MembershipPersistenceTest {
    companion object {
        private const val RULE_ID = "rule-id"
        private const val RULE_REGEX = "rule-regex"
        private const val RULE_LABEL = "rule-label"

        private const val REGISTRATION_SERIAL = 0L
        private const val ENDPOINT_URL = "http://localhost:8080"

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val BOOT_CONFIG_STRING = """
            $INSTANCE_ID = 1
            $BUS_TYPE = INMEMORY
            $BOOT_MAX_ALLOWED_MSG_SIZE = 1000000
        """
        private const val MEMBER_CONTEXT_KEY = "key"
        private const val MEMBER_CONTEXT_VALUE = "value"
        private const val REGISTRATION_CONTEXT_KEY = "key"
        private const val REGISTRATION_CONTEXT_VALUE = "value"
        private const val messagingConf = """
            componentVersion="5.1"
            maxAllowedMessageSize = 1000000
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
         * Wrapper class which allows the client to wait until the underlying DB message bus has been set
         * up correctly with partitions required.
         * Without this the client often tries to send RPC requests before the service has set up the kafka
         * topics required
         * for the DB message bus.
         */
        val membershipPersistenceClientWrapper = object : MembershipPersistenceClient {
            override fun persistMemberInfo(
                viewOwningIdentity: HoldingIdentity,
                memberInfos: Collection<SelfSignedMemberInfo>
            ) = safeCall {
                membershipPersistenceClient.persistMemberInfo(viewOwningIdentity, memberInfos)
            }

            override fun persistGroupPolicy(
                viewOwningIdentity: HoldingIdentity,
                groupPolicy: LayeredPropertyMap,
                version: Long
            ) = safeCall {
                membershipPersistenceClient.persistGroupPolicy(viewOwningIdentity, groupPolicy, version)
            }

            override fun persistGroupParameters(
                viewOwningIdentity: HoldingIdentity,
                groupParameters: InternalGroupParameters
            ) = safeCall {
                membershipPersistenceClient.persistGroupParameters(viewOwningIdentity, groupParameters)
            }

            override fun persistGroupParametersInitialSnapshot(
                viewOwningIdentity: HoldingIdentity
            ) = safeCall {
                membershipPersistenceClient.persistGroupParametersInitialSnapshot(viewOwningIdentity)
            }

            override fun addNotaryToGroupParameters(
                notary: PersistentMemberInfo
            ) = safeCall {
                membershipPersistenceClient.addNotaryToGroupParameters(notary)
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
                    viewOwningIdentity,
                    approvedMember,
                    registrationRequestId
                )
            }

            override fun setRegistrationRequestStatus(
                viewOwningIdentity: HoldingIdentity,
                registrationId: String,
                registrationRequestStatus: RegistrationStatus,
                reason: String?,
            ) = safeCall {
                membershipPersistenceClient.setRegistrationRequestStatus(
                    viewOwningIdentity,
                    registrationId,
                    registrationRequestStatus,
                    reason
                )
            }

            override fun mutualTlsAddCertificateToAllowedList(
                mgmHoldingIdentity: HoldingIdentity,
                subject: String,
            ) = safeCall {
                membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                    mgmHoldingIdentity,
                    subject
                )
            }

            override fun mutualTlsRemoveCertificateFromAllowedList(
                mgmHoldingIdentity: HoldingIdentity,
                subject: String,
            ) = safeCall {
                membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                    mgmHoldingIdentity,
                    subject
                )
            }

            override fun generatePreAuthToken(
                mgmHoldingIdentity: HoldingIdentity,
                preAuthTokenId: UUID,
                ownerX500Name: MemberX500Name,
                ttl: Instant?,
                remarks: String?
            ) = safeCall {
                membershipPersistenceClient.generatePreAuthToken(
                    mgmHoldingIdentity,
                    preAuthTokenId,
                    ownerX500Name,
                    ttl,
                    remarks
                )
            }

            override fun consumePreAuthToken(
                mgmHoldingIdentity: HoldingIdentity,
                ownerX500Name: MemberX500Name,
                preAuthTokenId: UUID
            ) = safeCall {
                membershipPersistenceClient.consumePreAuthToken(
                    mgmHoldingIdentity,
                    ownerX500Name,
                    preAuthTokenId
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
                    viewOwningIdentity,
                    ruleParams
                )
            }

            override fun deleteApprovalRule(
                viewOwningIdentity: HoldingIdentity,
                ruleId: String,
                ruleType: ApprovalRuleType
            ) = safeCall {
                membershipPersistenceClient.deleteApprovalRule(
                    viewOwningIdentity,
                    ruleId,
                    ruleType
                )
            }

            override fun suspendMember(
                viewOwningIdentity: HoldingIdentity,
                memberX500Name: MemberX500Name,
                serialNumber: Long?,
                reason: String?
            ) = safeCall {
                membershipPersistenceClient.suspendMember(
                    viewOwningIdentity,
                    memberX500Name,
                    serialNumber,
                    reason
                )
            }

            override fun activateMember(
                viewOwningIdentity: HoldingIdentity,
                memberX500Name: MemberX500Name,
                serialNumber: Long?,
                reason: String?
            ) = safeCall {
                membershipPersistenceClient.activateMember(
                    viewOwningIdentity,
                    memberX500Name,
                    serialNumber,
                    reason
                )
            }

            override fun updateStaticNetworkInfo(
                info: StaticNetworkInfo
            ) = safeCall {
                membershipPersistenceClient.updateStaticNetworkInfo(info)
            }

            override fun updateGroupParameters(
                viewOwningIdentity: HoldingIdentity,
                newGroupParameters: Map<String, String>
            ) = safeCall {
                membershipPersistenceClient.updateGroupParameters(viewOwningIdentity, newGroupParameters)
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
        private val signatureKey = "pk".toByteArray()
        private val signatureContent = "signature".toByteArray()
        private val signatureSpec = CryptoSignatureSpec("", null, null)

        private val registeringX500Name = MemberX500Name.parse("O=Bob, C=GB, L=London")
        private val registeringHoldingIdentity = HoldingIdentity(registeringX500Name, groupId)

        private val vnodeDbInfo = TestDbInfo(VirtualNodeDbType.VAULT.getConnectionName(holdingIdentityShortHash), DbSchema.VNODE)
        private val clusterDbInfo = TestDbInfo.createConfig()

        private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()
        private val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(BOOT_CONFIG_STRING))
        private val dbConfig = smartConfigFactory.create(clusterDbInfo.config)

        private lateinit var vnodeEmf: EntityManagerFactory
        private lateinit var clusterEmf: EntityManagerFactory
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
            vnodeEmf = dbInstaller.setupDatabase(vnodeDbInfo, "vnode-vault", MembershipEntities.vnodeClasses)
            clusterEmf = dbInstaller.setupClusterDatabase(
                clusterDbInfo, "config",
                MembershipEntities.clusterClasses + ConfigurationEntities.classes
            )

            entitiesRegistry.register(CordaDb.Vault.persistenceUnitName, MembershipEntities.vnodeClasses)
            entitiesRegistry.register(CordaDb.CordaCluster.persistenceUnitName, MembershipEntities.clusterClasses)

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
            eventually(15.seconds) {
                assertTrue(isRunning)
            }
        }
    }

    @Test
    fun `registration requests can persist over RPC topic`() {
        val registrationId = randomUUID().toString()
        val status = RegistrationStatus.RECEIVED_BY_MGM

        val result = membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                RegistrationStatus.RECEIVED_BY_MGM,
                registrationId,
                registeringHoldingIdentity,
                SignedData(
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
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                SignedData(
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(REGISTRATION_CONTEXT_KEY, REGISTRATION_CONTEXT_VALUE)
                                )
                            )
                        )
                    ),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                REGISTRATION_SERIAL,
            )
        ).execute()

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.registrationId).isEqualTo(registrationId)
        assertThat(persistedEntity.holdingIdentityShortHash).isEqualTo(registeringHoldingIdentity.shortHash.value)
        assertThat(persistedEntity.status).isEqualTo(status.toString())

        val persistedMemberContext = persistedEntity.memberContext.deserializeContextAsMap()
        with(persistedMemberContext.entries) {
            assertThat(size).isEqualTo(1)
            assertThat(first().key).isEqualTo(MEMBER_CONTEXT_KEY)
            assertThat(first().value).isEqualTo(MEMBER_CONTEXT_VALUE)
        }

        val persistedRegistrationContext = persistedEntity.registrationContext.deserializeContextAsMap()
        with(persistedRegistrationContext.entries) {
            assertThat(size).isEqualTo(1)
            assertThat(first().key).isEqualTo(REGISTRATION_CONTEXT_KEY)
            assertThat(first().value).isEqualTo(REGISTRATION_CONTEXT_VALUE)
        }
    }

    @Test
    fun `serial information can be persisted when requests are processed in unordered manner`() {
        val registrationId = randomUUID().toString()
        val status = RegistrationStatus.PENDING_MEMBER_VERIFICATION

        val statusPersistence = membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                RegistrationStatus.NEW,
                registrationId,
                registeringHoldingIdentity,
                SignedData(
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
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                SignedData(
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(REGISTRATION_CONTEXT_KEY, REGISTRATION_CONTEXT_VALUE)
                                )
                            )
                        )
                    ),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                null,
            )
        ).execute()

        assertThat(statusPersistence).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        assertThat(membershipPersistenceClientWrapper.setRegistrationRequestStatus(
            viewOwningHoldingIdentity,
            registrationId,
            status,
        ).execute()).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.registrationId).isEqualTo(registrationId)
        assertThat(persistedEntity.holdingIdentityShortHash).isEqualTo(registeringHoldingIdentity.shortHash.value)
        assertThat(persistedEntity.status).isEqualTo(status.toString())
        assertThat(persistedEntity.serial).isNull()

        val persistedMemberContext = persistedEntity.memberContext.deserializeContextAsMap()
        with(persistedMemberContext.entries) {
            assertThat(size).isEqualTo(1)
            assertThat(first().key).isEqualTo(MEMBER_CONTEXT_KEY)
            assertThat(first().value).isEqualTo(MEMBER_CONTEXT_VALUE)
        }

        val persistedRegistrationContext = persistedEntity.registrationContext.deserializeContextAsMap()
        with(persistedRegistrationContext.entries) {
            assertThat(size).isEqualTo(1)
            assertThat(first().key).isEqualTo(REGISTRATION_CONTEXT_KEY)
            assertThat(first().value).isEqualTo(REGISTRATION_CONTEXT_VALUE)
        }

        val serialAndStatusPersistence = membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                RegistrationStatus.SENT_TO_MGM,
                registrationId,
                registeringHoldingIdentity,
                SignedData(
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(MEMBER_CONTEXT_KEY, MEMBER_CONTEXT_VALUE),
                                    KeyValuePair("test", "value"),
                                )
                            )
                        )
                    ),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                SignedData(
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(REGISTRATION_CONTEXT_KEY, REGISTRATION_CONTEXT_VALUE)
                                )
                            )
                        )
                    ),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                2L,
            )
        ).execute()

        assertThat(serialAndStatusPersistence).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val persistedEntity2 = vnodeEmf.createEntityManager().use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity2.serial).isEqualTo(2L)
        assertThat(persistedEntity2.status).isEqualTo(RegistrationStatus.PENDING_MEMBER_VERIFICATION.toString())

        val persistedMemberContext2 = persistedEntity2.memberContext.deserializeContextAsMap()
        with(persistedMemberContext2.entries) {
            assertThat(size).isEqualTo(1)
            assertThat(first().key).isEqualTo(MEMBER_CONTEXT_KEY)
            assertThat(first().value).isEqualTo(MEMBER_CONTEXT_VALUE)
        }
    }

    @Test
    fun `persistGroupPolicy can persist over RPC topic`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM ${GroupPolicyEntity::class.java.simpleName}").executeUpdate()
        }
        val groupPolicy1 = layeredPropertyMapFactory.createMap(mapOf("aa" to "BBB"))
        val persisted1 = membershipPersistenceClientWrapper.persistGroupPolicy(viewOwningHoldingIdentity, groupPolicy1, 1)
            .execute()
        assertThat(persisted1).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val groupPolicy2 = layeredPropertyMapFactory.createMap(mapOf("aa" to "BBB1"))
        val persisted2 = membershipPersistenceClientWrapper.persistGroupPolicy(viewOwningHoldingIdentity, groupPolicy2, 2)
            .execute()
        assertThat(persisted2).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(GroupPolicyEntity::class.java, 1L)
        }
        assertThat(cordaAvroDeserializer.deserialize(persistedEntity.properties)!!.toMap()).isEqualTo(
            groupPolicy1.entries.associate { it.key to it.value }
        )
        val secondPersistedEntity = vnodeEmf.createEntityManager().use {
            it.find(GroupPolicyEntity::class.java, 2L)
        }
        assertThat(cordaAvroDeserializer.deserialize(secondPersistedEntity.properties)!!.toMap()).isEqualTo(
            groupPolicy2.entries.associate { it.key to it.value }
        )
    }

    @Test
    fun `persistGroupParametersInitialSnapshot can persist over RPC topic`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
        }
        val persisted =
            membershipPersistenceClientWrapper.persistGroupParametersInitialSnapshot(viewOwningHoldingIdentity)
                .execute()
        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                1
            )
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.epoch).isEqualTo(1)
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!.toMap()
            assertThat(deserialized.size).isEqualTo(2)
            assertThat(deserialized[EPOCH_KEY]).isEqualTo("1")
            assertDoesNotThrow { Instant.parse(deserialized[MODIFIED_TIME_KEY]) }
        }
    }

    @Test
    fun `persistGroupParameters can persist over RPC topic`() {
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        val pubKey = generator.genKeyPair().public

        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                epoch = 1,
                parameters = cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "1"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                        )
                    )
                )!!,
                signaturePublicKey = keyEncodingService.encodeAsByteArray(pubKey),
                signatureContent = byteArrayOf(1),
                signatureSpec = RSA_SHA256.signatureName
            )
            it.persist(entity)
        }
        val params = mapOf(
            EPOCH_KEY to "2",
            MODIFIED_TIME_KEY to clock.instant().toString()
        )
        val groupParameters = layeredPropertyMapFactory.create<TestGroupParametersImpl>(params)
            .apply {
                serialisedParams = cordaAvroSerializer.serialize(
                    KeyValuePairList(params.map { KeyValuePair(it.key, it.value) })
                )
                publicKey = pubKey
            }
        val persisted = membershipPersistenceClientWrapper
            .persistGroupParameters(viewOwningHoldingIdentity, groupParameters)
            .execute()
        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                2
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!.toMap()
            assertThat(deserialized.size).isEqualTo(2)
            assertThat(deserialized[EPOCH_KEY]).isEqualTo("2")
            assertDoesNotThrow { Instant.parse(deserialized[MODIFIED_TIME_KEY]) }
        }
    }

    @Test
    fun `addNotaryToGroupParameters can persist new notary service over RPC topic`() {
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                epoch = 50,
                parameters = cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "50"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                        )
                    )
                )!!,
                signaturePublicKey = keyEncodingService.encodeAsByteArray(generator.genKeyPair().public),
                signatureContent = byteArrayOf(1),
                signatureSpec = RSA_SHA256.signatureName
            )
            it.persist(entity)
        }

        val groupId = randomUUID().toString()
        val memberx500Name = MemberX500Name.parse("O=Notary, C=GB, L=London")
        val endpointUrl = "https://localhost:8080"
        val notaryServiceName = "O=New Service, L=London, C=GB"
        val notaryServicePlugin = "Notary Plugin"
        val notaryBackchainRequired = true
        val notaryKey = generator.generateKeyPair().public
        val memberContext = notaryMemberContext(memberx500Name, groupId, endpointUrl, notaryServiceName, notaryServicePlugin, notaryKey)
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )
        val notaryInfo = memberInfoFactory.createSelfSignedMemberInfo(
            cordaAvroSerializer.serialize(memberContext)!!,
            cordaAvroSerializer.serialize(mgmContext)!!,
            CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf(1)), ByteBuffer.wrap(byteArrayOf(1))),
            CryptoSignatureSpec(RSA_SHA256.signatureName, null, null),
        )
        val notary = memberInfoFactory
            .createPersistentMemberInfo(
                viewOwningHoldingIdentity.toAvro(),
                notaryInfo,
            )
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, "51"),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServiceName),
            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY, 0), notaryBackchainRequired.toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryServicePlugin),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), keyEncodingService.encodeAsString(notaryKey)),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
        )

        val persisted = membershipPersistenceClientWrapper.addNotaryToGroupParameters(notary)
            .execute()

        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        with((persisted as? MembershipPersistenceResult.Success<InternalGroupParameters>)!!.payload.entries) {
            assertThat(this).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(this.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters.associate { it.key to it.value }.entries)
        }

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                51
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            val deserializedList = deserialized.items
            assertThat(deserializedList).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(deserializedList.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters)
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
        val notaryBackchainRequired = true
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        val notaryKey = generator.generateKeyPair().public
        val notaryKeyAsString = keyEncodingService.encodeAsString(notaryKey)
        val memberContext = notaryMemberContext(
            memberx500Name,
            groupId,
            endpointUrl,
            notaryServiceName,
            notaryServicePlugin,
            notaryKey,
            listOf("1", "2")
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )
        val notaryInfo = memberInfoFactory.createSelfSignedMemberInfo(
            cordaAvroSerializer.serialize(memberContext)!!,
            cordaAvroSerializer.serialize(mgmContext)!!,
            CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf(1)), ByteBuffer.wrap(byteArrayOf(1))),
            CryptoSignatureSpec(RSA_SHA256.signatureName, null, null),
        )
        val notary = memberInfoFactory
            .createPersistentMemberInfo(
                viewOwningHoldingIdentity.toAvro(),
                notaryInfo,
            )
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                epoch = 100,
                parameters = cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "100"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServiceName),
                            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY, 0), notaryBackchainRequired.toString()),
                            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryServicePlugin),
                            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
                        )
                    )
                )!!,
                signaturePublicKey = keyEncodingService.encodeAsByteArray(generator.genKeyPair().public),
                signatureContent = byteArrayOf(1),
                signatureSpec = RSA_SHA256.signatureName
            )
            it.persist(entity)
        }
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, "101"),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServiceName),
            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY, 0), notaryBackchainRequired.toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryServicePlugin),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), notaryKeyAsString),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 1), "2"),
        )

        val persisted = membershipPersistenceClientWrapper.addNotaryToGroupParameters(notary)
            .execute()

        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        with((persisted as? MembershipPersistenceResult.Success<InternalGroupParameters>)!!.payload.entries) {
            assertThat(this).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(this.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters.associate { it.key to it.value }.entries)
        }

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                101
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            val deserializedList = deserialized.items
            assertThat(deserializedList).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(deserializedList.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters)
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
        val notaryBackchainRequired = true
        val notaryKey = with(keyGenerator) {
            generateKeyPair().public
        }
        val notaryKeyAsString = keyEncodingService.encodeAsString(notaryKey)
        val memberContext = notaryMemberContext(memberx500Name, groupId, endpointUrl, notaryServiceName, notaryServicePlugin, notaryKey)
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "2"),
                KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
            ).sorted()
        )
        val notaryInfo = memberInfoFactory.createSelfSignedMemberInfo(
            cordaAvroSerializer.serialize(memberContext)!!,
            cordaAvroSerializer.serialize(mgmContext)!!,
            CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf(1)), ByteBuffer.wrap(byteArrayOf(1))),
            CryptoSignatureSpec(RSA_SHA256.signatureName, null, null),
        )
        val notary = memberInfoFactory
            .createPersistentMemberInfo(
                viewOwningHoldingIdentity.toAvro(),
                notaryInfo,
            )
        val oldNotaryKey = keyGenerator.genKeyPair().public
        val oldNotaryKeyAsString = keyEncodingService.encodeAsString(oldNotaryKey)
        val oldNotaryMemberContext = KeyValuePairList(
            (
                memberContext.items.filterNot {
                    it.key.startsWith("corda.notary.keys")
                } + listOf(
                    KeyValuePair(String.format(NOTARY_KEY_PEM, 0), oldNotaryKeyAsString),
                    KeyValuePair(String.format(NOTARY_KEY_SPEC, 0), "SHA512withECDSA"),
                    KeyValuePair(String.format(NOTARY_KEY_HASH, 0), oldNotaryKey.fullIdHash().toString()),
                )
                ).sorted()
        )
        val oldNotaryMgmContext = KeyValuePairList(
            (
                memberContext.items.filterNot {
                    it.key == SERIAL
                } + listOf(
                    KeyValuePair(SERIAL, "1"),
                )
                ).sorted()
        )
        val oldNotaryEntity = MemberInfoEntity(
            notaryInfo.groupId,
            notaryInfo.name.toString(),
            false,
            notaryInfo.status,
            notaryInfo.modifiedTime!!,
            cordaAvroSerializer.serialize(oldNotaryMemberContext)!!,
            keyEncodingService.encodeAsByteArray(oldNotaryKey),
            byteArrayOf(1),
            RSA_SHA256.signatureName,
            cordaAvroSerializer.serialize(oldNotaryMgmContext)!!,
            1L,
            isDeleted = false
        )
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                epoch = 150,
                parameters = cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "150"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServiceName),
                            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY, 0), notaryBackchainRequired.toString()),
                            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryServicePlugin),
                            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
                            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), oldNotaryKeyAsString)
                        )
                    )
                )!!,
                signaturePublicKey = keyEncodingService.encodeAsByteArray(keyGenerator.genKeyPair().public),
                signatureContent = byteArrayOf(1),
                signatureSpec = RSA_SHA256.signatureName
            )
            it.persist(entity)
            it.persist(oldNotaryEntity)
        }
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, "151"),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServiceName),
            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY, 0), notaryBackchainRequired.toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryServicePlugin),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), oldNotaryKeyAsString),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 1), notaryKeyAsString),
        )

        val persisted = membershipPersistenceClientWrapper.addNotaryToGroupParameters(notary)
            .execute()

        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        with((persisted as? MembershipPersistenceResult.Success<InternalGroupParameters>)!!.payload.entries) {
            assertThat(this).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(this.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters.associate { it.key to it.value }.entries)
        }

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                151
            )
        }
        assertThat(persistedEntity).isNotNull
        with(persistedEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            val deserializedList = deserialized.items
            assertThat(deserializedList).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(deserializedList.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters)
            assertDoesNotThrow { Instant.parse(deserialized.toMap()[MODIFIED_TIME_KEY]) }
        }
    }

    @Test
    fun `member infos can persist over RPC topic`() {
        val result = persistMember(x500Name, MEMBER_STATUS_ACTIVE)

        assertThat(result).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId,
                    x500Name.toString(),
                    false
                )
            )
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.groupId).isEqualTo(groupId)
        assertThat(persistedEntity.memberX500Name).isEqualTo(x500Name.toString())
        assertThat(persistedEntity.serialNumber).isEqualTo(1)
        assertThat(persistedEntity.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        assertThat(persistedEntity.memberSignatureKey).isEqualTo(signatureKey)
        assertThat(persistedEntity.memberSignatureContent).isEqualTo(signatureContent)
        assertThat(persistedEntity.memberSignatureSpec).isEqualTo(signatureSpec.signatureName)

        val persistedMgmContext = persistedEntity.mgmContext.deserializeContextAsMap()
        assertThat(persistedMgmContext)
            .containsEntry(STATUS, MEMBER_STATUS_ACTIVE)
            .containsEntry(SERIAL, "1")

        val persistedMemberContext = persistedEntity.memberContext.deserializeContextAsMap()
        assertThat(persistedMemberContext)
            .containsEntry(String.format(URL_KEY, "0"), ENDPOINT_URL)
            .containsEntry(String.format(PROTOCOL_VERSION, "0"), "1")
            .containsEntry(GROUP_ID, groupId)
            .containsEntry(PARTY_NAME, x500Name.toString())
            .containsEntry(PLATFORM_VERSION, "5000")
            .containsEntry(SOFTWARE_VERSION, "5.0.0")
    }

    @Test
    fun `setMemberAndRegistrationRequestAsApproved update the member and registration request`() {
        // 1. Persist a member
        val memberPersistentResult = persistMember(registeringX500Name, MEMBER_STATUS_PENDING)

        assertThat(memberPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        val memberEntity = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId,
                    registeringX500Name.toString(),
                    true
                )
            )
        }
        assertThat(memberEntity.status).isEqualTo(MEMBER_STATUS_PENDING)

        // 2. Persist a request
        val registrationId = randomUUID().toString()
        persistRequest(registeringHoldingIdentity, registrationId, RegistrationStatus.NEW)
        val requestPersistentResult = persistRequest(registeringHoldingIdentity, registrationId)

        assertThat(requestPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val requestEntity = vnodeEmf.createEntityManager().use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(requestEntity.status).isEqualTo(RegistrationStatus.SENT_TO_MGM.toString())

        val approveResult = membershipPersistenceClientWrapper.setMemberAndRegistrationRequestAsApproved(
            viewOwningHoldingIdentity,
            registeringHoldingIdentity,
            registrationId,
        ).getOrThrow()

        val approveResultMemberInfo = memberInfoFactory.createMemberInfo(approveResult)

        assertThat(approveResultMemberInfo.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        assertThat(approveResultMemberInfo.groupId).isEqualTo(groupId)
        assertThat(approveResultMemberInfo.name).isEqualTo(registeringHoldingIdentity.x500Name)
        val newMemberEntity = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(
                    groupId,
                    registeringX500Name.toString(),
                    false
                )
            )
        }
        assertThat(newMemberEntity.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        val newRequestEntity = vnodeEmf.createEntityManager().use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(newRequestEntity.status).isEqualTo(RegistrationStatus.APPROVED.toString())
    }

    @Test
    fun `queryMemberInfo returns the member based on status and name`() {
        membershipQueryClient.start()
        eventually {
            assertThat(membershipPersistenceClient.isRunning).isTrue
        }

        val bobId = createTestHoldingIdentity("O=Bob, C=GB, L=London", groupId)
        val charlieId = createTestHoldingIdentity("O=Charlie, C=GB, L=London", groupId)
        val publicKey = "pk".toByteArray()
        val signature = "signature".toByteArray()
        val signatureSpec = CryptoSignatureSpec("spec", null, null)
        persistMember(bobId.x500Name, MEMBER_STATUS_PENDING, publicKey, signature, signatureSpec)
        persistMember(bobId.x500Name, MEMBER_STATUS_ACTIVE, publicKey, signature, signatureSpec)
        persistMember(charlieId.x500Name, MEMBER_STATUS_ACTIVE, publicKey, signature, signatureSpec)

        val result = membershipQueryClient.queryMemberInfo(
            viewOwningHoldingIdentity,
            listOf(bobId),
            listOf(MEMBER_STATUS_ACTIVE)
        ).getOrThrow()
        assertThat(result).hasSize(1)
        with(result.first()) {
            assertThat(name).isEqualTo(bobId.x500Name)
            assertTrue(isActive)
        }
    }

    @Test
    fun `queryMemberInfo returns the member signatures`() {
        membershipQueryClient.start()
        eventually {
            assertThat(membershipPersistenceClient.isRunning).isTrue
        }

        val memberAndRegistrationId = mutableMapOf<HoldingIdentity, String>()

        val signatures = (1..5).associate { index ->
            val registrationId = randomUUID().toString()
            val holdingId = createTestHoldingIdentity("O=Bob-$index, C=GB, L=London", groupId)
            memberAndRegistrationId[holdingId] = registrationId
            val publicKey = "pk-$index".toByteArray()
            val signature = "signature-$index".toByteArray()
            val signatureSpec = CryptoSignatureSpec("spec-$index", null, null)
            persistMember(holdingId.x500Name, MEMBER_STATUS_PENDING, publicKey, signature, signatureSpec)

            val cryptoSignatureWithKey = CryptoSignatureWithKey(
                ByteBuffer.wrap(publicKey),
                ByteBuffer.wrap(signature)
            )

            holdingId to (
                cryptoSignatureWithKey to signatureSpec
                )
        }

        val results = membershipQueryClient.queryMemberInfo(
            viewOwningHoldingIdentity,
            signatures.keys
        ).getOrThrow().associate {
            it.holdingIdentity to Pair(it.memberSignature, it.memberSignatureSpec)
        }
        assertThat(results).containsAllEntriesOf(signatures)
    }

    @Test
    fun `setRegistrationRequestStatus updates the registration request status`() {
        val registrationId = randomUUID().toString()
        val persistRegRequestResult = membershipPersistenceClientWrapper.persistRegistrationRequest(
            viewOwningHoldingIdentity,
            RegistrationRequest(
                RegistrationStatus.NEW,
                registrationId,
                registeringHoldingIdentity,
                SignedData(
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
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null),
                ),
                SignedData(
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(REGISTRATION_CONTEXT_KEY, REGISTRATION_CONTEXT_VALUE)
                                )
                            )
                        )
                    ),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null),
                ),
                REGISTRATION_SERIAL,
            )
        ).execute()

        assertThat(persistRegRequestResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val persistedEntity = vnodeEmf.createEntityManager().use {
            it.find(RegistrationRequestEntity::class.java, registrationId)
        }
        assertThat(persistedEntity).isNotNull
        assertThat(persistedEntity.registrationId).isEqualTo(registrationId)
        assertThat(persistedEntity.holdingIdentityShortHash).isEqualTo(registeringHoldingIdentity.shortHash.value)
        assertThat(persistedEntity.status).isEqualTo(RegistrationStatus.NEW.name)

        val updateRegRequestStatusResult = membershipPersistenceClientWrapper.setRegistrationRequestStatus(
            viewOwningHoldingIdentity,
            registrationId,
            RegistrationStatus.PENDING_AUTO_APPROVAL
        ).execute()

        assertThat(updateRegRequestStatusResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val updatedEntity = vnodeEmf.createEntityManager().use {
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

        val approvalRuleEntity = vnodeEmf.createEntityManager().use {
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
            viewOwningHoldingIdentity,
            RULE_ID,
            ApprovalRuleType.STANDARD
        ).getOrThrow()

        vnodeEmf.createEntityManager().use {
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
        persistRequest(registeringHoldingIdentity, registrationId1, RegistrationStatus.NEW)
        val requestPersistentResult =
            persistRequest(registeringHoldingIdentity, registrationId1, RegistrationStatus.PENDING_MANUAL_APPROVAL)
        assertThat(requestPersistentResult).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        // Persist a completed request
        val registrationId2 = randomUUID().toString()
        persistRequest(viewOwningHoldingIdentity, registrationId2, RegistrationStatus.RECEIVED_BY_MGM)
        val requestPersistentResult2 =
            persistRequest(viewOwningHoldingIdentity, registrationId2, RegistrationStatus.DECLINED)
        assertThat(requestPersistentResult2).isInstanceOf(MembershipPersistenceResult.Success::class.java)
        // Persist a new request and change its status to SENT_TO_MGM
        val registrationId3 = randomUUID().toString()
        val holdingId3 = HoldingIdentity(MemberX500Name.parse("O=Charlie, C=GB, L=London"), groupId)
        persistRequest(holdingId3, registrationId3, RegistrationStatus.NEW)
        val requestPersistentResult3 = persistRequest(
            holdingId3,
            registrationId3
        )
        assertThat(requestPersistentResult3).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val result1 = membershipQueryClient.queryRegistrationRequests(
            viewOwningHoldingIdentity,
            null,
            listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL, RegistrationStatus.APPROVED, RegistrationStatus.DECLINED)
        ).getOrThrow()
        assertThat(result1.map { it.registrationId }).containsAll(listOf(registrationId1, registrationId2))

        val result2 = membershipQueryClient.queryRegistrationRequests(
            viewOwningHoldingIdentity,
            viewOwningHoldingIdentity.x500Name,
            listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL, RegistrationStatus.APPROVED, RegistrationStatus.DECLINED)
        ).getOrThrow()
        assertThat(result2.map { it.registrationId }).containsAll(listOf(registrationId2))

        val result3 = membershipQueryClient.queryRegistrationRequests(
            viewOwningHoldingIdentity,
            null,
            listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
        ).getOrThrow()
        assertThat(result3.map { it.registrationId }).containsAll(listOf(registrationId1))

        val result4 = membershipQueryClient.queryRegistrationRequests(viewOwningHoldingIdentity).getOrThrow()
        assertThat(result4.map { it.registrationId }).containsAll(listOf(registrationId1, registrationId2, registrationId3))
    }

    @Test
    @Suppress("ForEachOnRange")
    fun `queryRegistrationRequest retrieves the oldest queued registration request`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM RegistrationRequestEntity").executeUpdate()
        }
        membershipQueryClient.start()
        eventually {
            assertThat(membershipPersistenceClient.isRunning).isTrue
        }
        // Persist a request pending manual approval
        val registrationId = randomUUID().toString()
        persistRequest(registeringHoldingIdentity, registrationId, RegistrationStatus.PENDING_MANUAL_APPROVAL)
        // Persist 3 requests with NEW (queue 3 requests)
        val queuedRegistrationIds = mutableListOf<String>()
        (1..3).forEach {
            val id = randomUUID().toString()
            queuedRegistrationIds.add(id)
            persistRequest(registeringHoldingIdentity, id, RegistrationStatus.NEW)
        }

        val result = membershipQueryClient.queryRegistrationRequests(
            viewOwningHoldingIdentity,
            registeringHoldingIdentity.x500Name,
            listOf(RegistrationStatus.NEW),
            1
        ).getOrThrow()
        assertThat(result.singleOrNull()?.registrationId).isEqualTo(queuedRegistrationIds.first())
    }

    @Test
    fun `queryRegistrationRequest returns empty list when there were no queued requests`() {
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM RegistrationRequestEntity").executeUpdate()
        }
        membershipQueryClient.start()
        eventually {
            assertThat(membershipPersistenceClient.isRunning).isTrue
        }
        // Persist a request pending manual approval
        val registrationId = randomUUID().toString()
        persistRequest(registeringHoldingIdentity, registrationId, RegistrationStatus.PENDING_MANUAL_APPROVAL)

        val result = membershipQueryClient.queryRegistrationRequests(
            viewOwningHoldingIdentity,
            registeringHoldingIdentity.x500Name,
            listOf(RegistrationStatus.NEW),
            1
        ).getOrThrow()
        assertThat(result).isEmpty()
    }

    @Test
    fun `suspendMember can persist suspended member info over RPC topic`() {
        val member1 = MemberX500Name.parse("O=Suspend1, C=GB, L=London")
        val memberPersistenceResult1 = persistMember(member1, MEMBER_STATUS_ACTIVE)
        assertThat(memberPersistenceResult1).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val suspended1 = membershipPersistenceClientWrapper.suspendMember(
            viewOwningHoldingIdentity,
            member1,
            1,
            "test-reason"
        ).getOrThrow().first

        val persistedEntity1 = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(viewOwningHoldingIdentity.groupId, member1.toString(), false)
            )
        }
        assertThat(persistedEntity1).isNotNull
        assertThat(persistedEntity1.status).isEqualTo(MEMBER_STATUS_SUSPENDED)
        assertThat(persistedEntity1.serialNumber).isEqualTo(2L)
        val suspended1MemberInfo = memberInfoFactory.createMemberInfo(suspended1)
        with(suspended1MemberInfo.mgmProvidedContext) {
            assertThat(this[STATUS]).isEqualTo(MEMBER_STATUS_SUSPENDED)
            assertThat(this[SERIAL]).isEqualTo("2")
        }

        val member2 = MemberX500Name.parse("O=Suspend2, C=GB, L=London")
        val memberPersistenceResult2 = persistMember(member2, MEMBER_STATUS_ACTIVE)
        assertThat(memberPersistenceResult2).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        // Test without specifying serial number
        val suspended2 = membershipPersistenceClientWrapper.suspendMember(
            viewOwningHoldingIdentity,
            member2,
            null,
            "test-reason"
        ).getOrThrow().first

        val persistedEntity2 = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(viewOwningHoldingIdentity.groupId, member2.toString(), false)
            )
        }
        assertThat(persistedEntity2).isNotNull
        assertThat(persistedEntity2.status).isEqualTo(MEMBER_STATUS_SUSPENDED)
        assertThat(persistedEntity2.serialNumber).isEqualTo(2L)
        val suspended2MemberInfo = memberInfoFactory.createMemberInfo(suspended2)
        with(suspended2MemberInfo.mgmProvidedContext) {
            assertThat(this[STATUS]).isEqualTo(MEMBER_STATUS_SUSPENDED)
            assertThat(this[SERIAL]).isEqualTo("2")
        }
    }

    @Test
    fun `suspendMember can persist suspended notary and update the group parameters info over RPC topic`() {
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                epoch = 50,
                parameters = cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "50"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                        )
                    )
                )!!,
                signaturePublicKey = keyEncodingService.encodeAsByteArray(generator.genKeyPair().public),
                signatureContent = byteArrayOf(1),
                signatureSpec = RSA_SHA256.signatureName
            )
            it.persist(entity)
        }

        val memberX500Name = MemberX500Name.parse("O=Notary, C=GB, L=London")
        val memberContext = notaryMemberContext(
            memberX500Name,
            groupId,
            endpointUrl = "https://localhost:8080",
            notaryServiceName = "O=New Service, L=London, C=GB",
            notaryServicePlugin = "Notary Plugin",
            generator.generateKeyPair().public
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_ACTIVE),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )
        val notaryInfo = memberInfoFactory.createSelfSignedMemberInfo(
            cordaAvroSerializer.serialize(memberContext)!!,
            cordaAvroSerializer.serialize(mgmContext)!!,
            CryptoSignatureWithKey(ByteBuffer.wrap(byteArrayOf(1)), ByteBuffer.wrap(byteArrayOf(1))),
            CryptoSignatureSpec(RSA_SHA256.signatureName, null, null),
        )
        val notary = memberInfoFactory
            .createPersistentMemberInfo(
                viewOwningHoldingIdentity.toAvro(),
                notaryInfo,
            )
        val persisted = membershipPersistenceClientWrapper.addNotaryToGroupParameters(notary)
            .execute()
        assertThat(persisted).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val memberPersistenceResult1 = membershipPersistenceClientWrapper.persistMemberInfo(
            viewOwningHoldingIdentity,
            listOf(
                memberInfoFactory.createSelfSignedMemberInfo(
                    cordaAvroSerializer.serialize(memberContext)!!,
                    cordaAvroSerializer.serialize(mgmContext)!!,
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(signatureKey),
                        ByteBuffer.wrap(signatureContent)
                    ),
                    signatureSpec,
                )
            )
        ).execute()
        assertThat(memberPersistenceResult1).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val (updatedPersistentMemberInfo, groupParameters) = membershipPersistenceClientWrapper.suspendMember(
            viewOwningHoldingIdentity,
            memberX500Name,
            1,
            "test-reason"
        ).getOrThrow()

        // As the Member was Suspended we expect the notary to be removed from the group parameters
        val updatedEpoch = 52
        val expectedGroupParameters = listOf(KeyValuePair(EPOCH_KEY, updatedEpoch.toString()))
        assertThat(groupParameters!!.entries.filterNot { it.key == MODIFIED_TIME_KEY })
            .containsExactlyInAnyOrderElementsOf(expectedGroupParameters.associate { it.key to it.value }.entries)

        val persistedMemberInfoEntity = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(viewOwningHoldingIdentity.groupId, memberX500Name.toString(), false)
            )
        }
        assertThat(persistedMemberInfoEntity).isNotNull
        assertThat(persistedMemberInfoEntity.status).isEqualTo(MEMBER_STATUS_SUSPENDED)
        assertThat(persistedMemberInfoEntity.serialNumber).isEqualTo(2L)
        val updatedMemberInfo = memberInfoFactory.createMemberInfo(updatedPersistentMemberInfo)
        assertThat(updatedMemberInfo.mgmProvidedContext.entries.associate { it.key to it.value })
            .containsAllEntriesOf(mapOf(STATUS to MEMBER_STATUS_SUSPENDED, SERIAL to "2"))

        val persistedGroupParametersEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                updatedEpoch
            )
        }
        assertThat(persistedGroupParametersEntity).isNotNull
        with(persistedGroupParametersEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            val deserializedList = deserialized.items
            assertThat(deserializedList).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(deserializedList.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters)
            assertDoesNotThrow { Instant.parse(deserialized.toMap()[MODIFIED_TIME_KEY]) }
        }
    }

    @Test
    fun `activateMember can persist activated member info over RPC topic`() {
        val member1 = MemberX500Name.parse("O=Activate1, C=GB, L=London")
        val memberPersistenceResult1 = persistMember(member1, MEMBER_STATUS_SUSPENDED)
        assertThat(memberPersistenceResult1).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val suspended1 = membershipPersistenceClientWrapper.activateMember(
            viewOwningHoldingIdentity,
            member1,
            1L,
            "test-reason"
        ).getOrThrow().first

        val persistedEntity1 = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(viewOwningHoldingIdentity.groupId, member1.toString(), false)
            )
        }
        assertThat(persistedEntity1).isNotNull
        assertThat(persistedEntity1.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        assertThat(persistedEntity1.serialNumber).isEqualTo(2L)
        val suspended1MemberInfo = memberInfoFactory.createMemberInfo(suspended1)
        with(suspended1MemberInfo.mgmProvidedContext) {
            assertThat(this[STATUS]).isEqualTo(MEMBER_STATUS_ACTIVE)
            assertThat(this[SERIAL]).isEqualTo("2")
        }

        // Test without specifying serial number
        val member2 = MemberX500Name.parse("O=Activate2, C=GB, L=London")
        val memberPersistenceResult2 = persistMember(member2, MEMBER_STATUS_SUSPENDED)
        assertThat(memberPersistenceResult2).isInstanceOf(MembershipPersistenceResult.Success::class.java)

        val suspended2 = membershipPersistenceClientWrapper.activateMember(
            viewOwningHoldingIdentity,
            member2,
            1L,
            "test-reason"
        ).getOrThrow().first

        val persistedEntity2 = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(viewOwningHoldingIdentity.groupId, member2.toString(), false)
            )
        }
        assertThat(persistedEntity2).isNotNull
        assertThat(persistedEntity2.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        assertThat(persistedEntity2.serialNumber).isEqualTo(2L)
        val suspended2MemberInfo = memberInfoFactory.createMemberInfo(suspended2)
        with(suspended2MemberInfo.mgmProvidedContext) {
            assertThat(this[STATUS]).isEqualTo(MEMBER_STATUS_ACTIVE)
            assertThat(this[SERIAL]).isEqualTo("2")
        }
    }

    @Test
    fun `activateMember can persist re-activated notary and update the group parameters info over RPC topic`() {
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                epoch = 50,
                parameters = cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "50"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                        )
                    )
                )!!,
                signaturePublicKey = keyEncodingService.encodeAsByteArray(generator.genKeyPair().public),
                signatureContent = byteArrayOf(1),
                signatureSpec = RSA_SHA256.signatureName
            )
            it.persist(entity)
        }

        val memberX500Name = MemberX500Name.parse("O=Notary, C=GB, L=London")
        val notaryServiceName = "O=New Service, L=London, C=GB"
        val notaryPlugin = "Notary Plugin"
        val notaryBackchainRequired = true
        val notaryPublicKey = generator.generateKeyPair().public
        val notaryVersions = listOf("1")
        val memberContext = notaryMemberContext(
            memberX500Name,
            groupId,
            endpointUrl = "https://localhost:8080",
            notaryServiceName = notaryServiceName,
            notaryServicePlugin = notaryPlugin,
            notaryPublicKey,
            notaryVersions
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, MEMBER_STATUS_SUSPENDED),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )

        val memberPersistenceResult1 = membershipPersistenceClientWrapper.persistMemberInfo(
            viewOwningHoldingIdentity,
            listOf(
                memberInfoFactory.createSelfSignedMemberInfo(
                    cordaAvroSerializer.serialize(memberContext)!!,
                    cordaAvroSerializer.serialize(mgmContext)!!,
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(signatureKey),
                        ByteBuffer.wrap(signatureContent)
                    ),
                    signatureSpec,
                )
            )
        ).execute()
        memberPersistenceResult1.getOrThrow()

        val (updatedPersistentMemberInfo, groupParameters) = membershipPersistenceClientWrapper.activateMember(
            viewOwningHoldingIdentity,
            memberX500Name,
            1,
            "test-reason"
        ).getOrThrow()

        val updatedEpoch = 51
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, updatedEpoch.toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServiceName),
            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY, 0), notaryBackchainRequired.toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryPlugin),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), keyEncodingService.encodeAsString(notaryPublicKey)),
        ) + notaryVersions.mapIndexed { i, version -> KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, i), version) }
        assertThat(groupParameters!!.entries.filterNot { it.key == MODIFIED_TIME_KEY })
            .containsExactlyInAnyOrderElementsOf(expectedGroupParameters.associate { it.key to it.value }.entries)

        val persistedMemberInfoEntity = vnodeEmf.createEntityManager().use {
            it.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(viewOwningHoldingIdentity.groupId, memberX500Name.toString(), false)
            )
        }
        assertThat(persistedMemberInfoEntity.status).isEqualTo(MEMBER_STATUS_ACTIVE)
        assertThat(persistedMemberInfoEntity.serialNumber).isEqualTo(2L)
        val updatedMemberInfo = memberInfoFactory.createMemberInfo(updatedPersistentMemberInfo)
        assertThat(updatedMemberInfo.mgmProvidedContext.entries.associate { it.key to it.value })
            .containsAllEntriesOf(mapOf(STATUS to MEMBER_STATUS_ACTIVE, SERIAL to "2"))

        val persistedGroupParametersEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                updatedEpoch
            )
        }
        assertThat(persistedGroupParametersEntity).isNotNull
        with(persistedGroupParametersEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            val deserializedList = deserialized.items
            assertThat(deserializedList).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(deserializedList.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters)
            assertDoesNotThrow { Instant.parse(deserialized.toMap()[MODIFIED_TIME_KEY]) }
        }
    }

    @Test
    fun `can persist static network info to cluster DB`() {
        val groupId = UUID(0, 1).toString()
        val groupParameters = KeyValuePairList(listOf(KeyValuePair("key", "value")))
        val pubKey = "pubKey".toByteArray()
        val privateKey = "privateKey".toByteArray()
        val initialVersion = clusterEmf.createEntityManager().transaction {
            val serializedParams = cordaAvroSerializer.serialize(groupParameters)
            assertThat(serializedParams).isNotNull
            it.persist(
                StaticNetworkInfoEntity(
                    groupId,
                    pubKey,
                    privateKey,
                    serializedParams!!
                )
            )
            it.find(StaticNetworkInfoEntity::class.java, groupId).version
        }
        assertThat(initialVersion).isEqualTo(1)

        val newStaticNetworkInfo = StaticNetworkInfo(
            groupId,
            KeyValuePairList(listOf(KeyValuePair("newKey", "newValue")) + groupParameters.items),
            ByteBuffer.wrap(pubKey),
            ByteBuffer.wrap(privateKey),
            initialVersion
        )
        val result = assertDoesNotThrow {
            membershipPersistenceClientWrapper.updateStaticNetworkInfo(newStaticNetworkInfo).getOrThrow()
        }

        // Assert returned value is as expected
        assertThat(result.groupId).isEqualTo(groupId)
        assertThat(result.groupParameters.items)
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(
                listOf(KeyValuePair("newKey", "newValue"), KeyValuePair("key", "value"))
            )
        assertThat(result.mgmPublicSigningKey.array()).isEqualTo(pubKey)
        assertThat(result.mgmPrivateSigningKey.array()).isEqualTo(privateKey)
        assertThat(result.version).isEqualTo(initialVersion + 1)

        // Assert persisted value is as expected
        clusterEmf.createEntityManager().transaction {
            val persisted = it.find(StaticNetworkInfoEntity::class.java, groupId)
            assertThat(persisted.groupId).isEqualTo(groupId)
            assertThat(persisted.groupParameters.deserializeContextAsMap())
                .hasSize(2)
                .containsExactlyInAnyOrderEntriesOf(
                    mapOf("newKey" to "newValue", "key" to "value")
                )
            assertThat(persisted.mgmPublicKey).isEqualTo(pubKey)
            assertThat(persisted.mgmPrivateKey).isEqualTo(privateKey)
            assertThat(persisted.version).isEqualTo(initialVersion + 1)
        }
    }

    @Test
    fun `can persist updated group parameters`() {
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider())
        val notaryPublicKey = generator.generateKeyPair().public
        val notaryParameters = listOf(
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), "O=New Service, L=London, C=GB"),
            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED_KEY, 0), "true"),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), "Notary Plugin"),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), keyEncodingService.encodeAsString(notaryPublicKey))
        ) + listOf("1").mapIndexed {
                i, version ->
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, i), version)
        }

        vnodeEmf.transaction {
            it.createQuery("DELETE FROM GroupParametersEntity").executeUpdate()
            val entity = GroupParametersEntity(
                epoch = 50,
                parameters = cordaAvroSerializer.serialize(
                    KeyValuePairList(
                        listOf(
                            KeyValuePair(EPOCH_KEY, "50"),
                            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
                        ) + notaryParameters
                    )
                )!!,
                signaturePublicKey = keyEncodingService.encodeAsByteArray(generator.genKeyPair().public),
                signatureContent = byteArrayOf(1),
                signatureSpec = RSA_SHA256.signatureName
            )
            it.persist(entity)
        }

        val updatedGroupParameters = membershipPersistenceClientWrapper.updateGroupParameters(
            viewOwningHoldingIdentity,
            mapOf(MPV_KEY to "50000", "ext.key" to "value")
        ).getOrThrow()

        val updatedEpoch = 51
        val expectedGroupParameters = listOf(
            KeyValuePair(EPOCH_KEY, updatedEpoch.toString()),
            KeyValuePair(MPV_KEY, "50000"),
            KeyValuePair("ext.key", "value")
        ) + notaryParameters
        assertThat(updatedGroupParameters.entries.filterNot { it.key == MODIFIED_TIME_KEY })
            .containsExactlyInAnyOrderElementsOf(expectedGroupParameters.associate { it.key to it.value }.entries)

        val persistedGroupParametersEntity = vnodeEmf.createEntityManager().use {
            it.find(
                GroupParametersEntity::class.java,
                updatedEpoch
            )
        }
        assertThat(persistedGroupParametersEntity).isNotNull
        with(persistedGroupParametersEntity.parameters) {
            val deserialized = cordaAvroDeserializer.deserialize(this)!!
            val deserializedList = deserialized.items
            assertThat(deserializedList).anyMatch { it.key == MODIFIED_TIME_KEY }
            assertThat(deserializedList.filterNot { it.key == MODIFIED_TIME_KEY })
                .containsExactlyInAnyOrderElementsOf(expectedGroupParameters)
            assertDoesNotThrow { Instant.parse(deserialized.toMap()[MODIFIED_TIME_KEY]) }
        }
    }

    private fun ByteArray.deserializeContextAsMap(): Map<String, String> =
        cordaAvroDeserializer.deserialize(this)
            ?.items
            ?.associate { it.key to it.value } ?: fail("Failed to deserialize context.")

    @Suppress("LongParameterList")
    private fun notaryMemberContext(
        memberX500Name: MemberX500Name,
        groupId: String,
        endpointUrl: String,
        notaryServiceName: String,
        notaryServicePlugin: String,
        notaryKey: PublicKey,
        notaryProtocolVersions: List<String> = listOf("1")
    ): KeyValuePairList {
        val notaryKeyHash = notaryKey.fullIdHash()
        return KeyValuePairList(
            (
                listOf(
                    KeyValuePair(String.format(URL_KEY, "0"), endpointUrl),
                    KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                    KeyValuePair(GROUP_ID, groupId),
                    KeyValuePair(PARTY_NAME, memberX500Name.toString()),
                    KeyValuePair(PLATFORM_VERSION, "11"),
                    KeyValuePair(SOFTWARE_VERSION, "5.0.0"),
                    KeyValuePair(NOTARY_SERVICE_NAME, notaryServiceName),
                    KeyValuePair(NOTARY_SERVICE_PROTOCOL, notaryServicePlugin),
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, 0), "1"),
                    KeyValuePair("${ROLES_PREFIX}.0", "notary"),
                    KeyValuePair(String.format(NOTARY_KEY_PEM, 0), keyEncodingService.encodeAsString(notaryKey)),
                    KeyValuePair(String.format(NOTARY_KEY_SPEC, 0), "SHA512withECDSA"),
                    KeyValuePair(String.format(NOTARY_KEY_HASH, 0), notaryKeyHash.toString()),
                ) + notaryProtocolVersions.mapIndexed { i, version ->
                    KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS, i), version)
                }
                ).sorted()
        )
    }

    private fun persistMember(
        memberName: MemberX500Name,
        memberStatus: String,
        memberSignatureKey: ByteArray = signatureKey,
        memberSignatureContent: ByteArray = signatureContent,
        memberSignatureSpec: CryptoSignatureSpec = signatureSpec,
    ): MembershipPersistenceResult<Unit> {
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair(String.format(URL_KEY, "0"), ENDPOINT_URL),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(GROUP_ID, groupId),
                KeyValuePair(PARTY_NAME, memberName.toString()),
                KeyValuePair(PLATFORM_VERSION, "5000"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0"),
            ).sorted()
        )
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, memberStatus),
                KeyValuePair(SERIAL, "1"),
            ).sorted()
        )

        return membershipPersistenceClientWrapper.persistMemberInfo(
            viewOwningHoldingIdentity,
            listOf(
                memberInfoFactory.createSelfSignedMemberInfo(
                    cordaAvroSerializer.serialize(memberContext)!!,
                    cordaAvroSerializer.serialize(mgmContext)!!,
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(memberSignatureKey),
                        ByteBuffer.wrap(memberSignatureContent)
                    ),
                    memberSignatureSpec,
                )
            )
        ).execute()
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
                SignedData(
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
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                SignedData(
                    ByteBuffer.wrap(
                        cordaAvroSerializer.serialize(
                            KeyValuePairList(
                                listOf(
                                    KeyValuePair(REGISTRATION_CONTEXT_KEY, REGISTRATION_CONTEXT_VALUE)
                                )
                            )
                        )
                    ),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap(byteArrayOf()),
                        ByteBuffer.wrap(byteArrayOf())
                    ),
                    CryptoSignatureSpec("", null, null)
                ),
                REGISTRATION_SERIAL,
            )
        ).execute()
    }

    private class TestGroupParametersImpl(
        private val map: LayeredPropertyMap
    ) : LayeredPropertyMap by map, SignedGroupParameters {

        var serialisedParams: ByteArray? = null
        var publicKey: PublicKey? = null

        override fun getEpoch() = 5
        override val mgmSignature: DigitalSignatureWithKey
            get() = DigitalSignatureWithKey(
                publicKey
                    ?: throw UnsupportedOperationException("Serialized parameters must be set in the test function"),
                byteArrayOf(1)
            )
        override val mgmSignatureSpec: SignatureSpec
            get() = RSA_SHA256

        override val groupParameters: ByteArray
            get() = serialisedParams
                ?: throw UnsupportedOperationException("Serialized parameters must be set in the test function")
        override val hash: SecureHash
            get() = groupParameters.hash()

        override fun getModifiedTime() = clock.instant()
        override fun getNotaries(): List<NotaryInfo> = emptyList()
    }
}
