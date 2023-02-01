package net.corda.processor.member

import com.typesafe.config.ConfigRenderOptions
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.libs.configuration.datamodel.ConfigurationEntities
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.membership.certificate.publisher.MembersClientCertificatePublisher
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.RegistrationProxy
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceHoldingIdentity
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertLookupSize
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertSecondGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobHoldingIdentity
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.charlieName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.charlieX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicyFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.groupId
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookUpBySessionKey
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookup
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookupFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.makeBootstrapConfig
import net.corda.processor.member.MemberProcessorTestUtils.Companion.makeMembershipConfig
import net.corda.processor.member.MemberProcessorTestUtils.Companion.makeMessagingConfig
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishMembershipConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.makeCryptoConfig
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishDefaultCryptoConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishGatewayConfig
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishMessagingConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishRawGroupPolicyData
import net.corda.processor.member.MemberProcessorTestUtils.Companion.register
import net.corda.processor.member.MemberProcessorTestUtils.Companion.sampleGroupPolicy1
import net.corda.processor.member.MemberProcessorTestUtils.Companion.sampleGroupPolicy2
import net.corda.processor.member.MemberProcessorTestUtils.Companion.startAndWait
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.member.MemberProcessor
import net.corda.schema.configuration.BootConfig.BOOT_DB_PARAMS
import net.corda.test.util.eventually
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.util.seconds
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory

@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberProcessorIntegrationTest {
    companion object {
        const val CLIENT_ID = "member-processor-integration-test"

        @InjectService(timeout = 5000L)
        lateinit var groupPolicyProvider: GroupPolicyProvider

        @InjectService(timeout = 5000L)
        lateinit var registrationProxy: RegistrationProxy

        @InjectService(timeout = 5000L)
        lateinit var virtualNodeInfoReader: VirtualNodeInfoReadService

        @InjectService(timeout = 5000L)
        lateinit var cpiInfoReader: CpiInfoReadService

        @InjectService(timeout = 5000L)
        lateinit var publisherFactory: PublisherFactory

        @InjectService(timeout = 5000L)
        lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

        @InjectService(timeout = 5000L)
        lateinit var membersClientCertificatePublisher: MembersClientCertificatePublisher

        @InjectService(timeout = 5000L)
        lateinit var memberProcessor: MemberProcessor

        @InjectService(timeout = 5000L)
        lateinit var cryptoProcessor: CryptoProcessor

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        @InjectService(timeout = 5000L)
        lateinit var coordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000L)
        lateinit var lifecycleRegistry: LifecycleRegistry

        @InjectService(timeout = 5000L)
        lateinit var hsmRegistrationClient: HSMRegistrationClient

        lateinit var publisher: Publisher

        private val invalidHoldingIdentity = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)

        private val aliceVNodeId = createTestHoldingIdentity(aliceName, groupId).shortHash

        private val bobVNodeId = createTestHoldingIdentity(bobName, groupId).shortHash

        private val charlieVNodeId = createTestHoldingIdentity(charlieName, groupId).shortHash

        private lateinit var testDependencies: TestDependenciesTracker

        private val clusterDb = TestDbInfo.createConfig()

        private val cryptoDb = TestDbInfo(
            name = CordaDb.Crypto.persistenceUnitName,
            schemaName = DbSchema.CRYPTO
        )

        private val aliceVNodeDb = TestDbInfo(
            name = VirtualNodeDbType.CRYPTO.getConnectionName(aliceVNodeId),
            schemaName = "vnode_crypto_alice"
        )

        private val bobVNodeDb = TestDbInfo(
            name = VirtualNodeDbType.CRYPTO.getConnectionName(bobVNodeId),
            schemaName = "vnode_crypto_bob"
        )

        private val charlieVNodeDb = TestDbInfo(
            name = VirtualNodeDbType.CRYPTO.getConnectionName(charlieVNodeId),
            schemaName = "vnode_crypto_charlie"
        )

        private val boostrapConfig = makeBootstrapConfig(
            mapOf(
                BOOT_DB_PARAMS to clusterDb.config
            )
        )

        private val membershipConfig = makeMembershipConfig()
        private val messagingConfig = makeMessagingConfig()
        private val cryptoConfig = makeCryptoConfig()

        private lateinit var connectionIds: Map<String, UUID>

        @JvmStatic
        @BeforeAll
        fun setUp() {
            // Creating this publisher first (using the messagingConfig) will ensure we're forcing
            // the in-memory message bus. Otherwise we may attempt to use a real database for the test
            // and that can cause message bus conflicts when the tests are run in parallel.
            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), messagingConfig)

            setupDatabases()

            // Set basic bootstrap config
            cryptoProcessor.start(boostrapConfig)
            memberProcessor.start(boostrapConfig)
            membershipGroupReaderProvider.start()
            hsmRegistrationClient.start()
            membersClientCertificatePublisher.start()
            testDependencies = TestDependenciesTracker(
                LifecycleCoordinatorName.forComponent<MemberProcessorIntegrationTest>(),
                coordinatorFactory,
                lifecycleRegistry,
                setOf(
                    LifecycleCoordinatorName.forComponent<MemberProcessor>(),
                    LifecycleCoordinatorName.forComponent<CryptoProcessor>(),
                    LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                    LifecycleCoordinatorName.forComponent<MembersClientCertificatePublisher>(),
                    LifecycleCoordinatorName.forComponent<HSMRegistrationClient>()
                )
            ).also { it.startAndWait() }

            publisher.publishMessagingConf(messagingConfig)
            publisher.publishMembershipConf(membershipConfig)
            publisher.publishDefaultCryptoConf(cryptoConfig)
            publisher.publishGatewayConfig()
            publisher.publishRawGroupPolicyData(
                virtualNodeInfoReader,
                cpiInfoReader,
                aliceHoldingIdentity,
                connectionIds.getValue(aliceVNodeDb.name)
            )
            publisher.publishRawGroupPolicyData(
                virtualNodeInfoReader,
                cpiInfoReader,
                bobHoldingIdentity,
                connectionIds.getValue(bobVNodeDb.name)
            )

            // Wait for published content to be picked up by components.
            eventually { assertNotNull(virtualNodeInfoReader.get(aliceHoldingIdentity)) }

            testDependencies.waitUntilAllUp(Duration.ofSeconds(60))
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            if (::testDependencies.isInitialized) {
                testDependencies.stop()
            }
        }

        private fun setupDatabases() {
            val databaseInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
            val configEmf = databaseInstaller.setupClusterDatabase(
                clusterDb,
                "config",
                ConfigurationEntities.classes
            )
            databaseInstaller.setupDatabase(
                cryptoDb,
                "crypto"
            ).close()
            databaseInstaller.setupDatabase(
                aliceVNodeDb,
                "vnode-crypto",
                CryptoEntities.classes
            ).close()
            databaseInstaller.setupDatabase(
                bobVNodeDb,
                "vnode-crypto",
                CryptoEntities.classes
            ).close()
            databaseInstaller.setupDatabase(
                charlieVNodeDb,
                "vnode-crypto",
                CryptoEntities.classes
            ).close()
            connectionIds = addDbConnectionConfigs(configEmf, cryptoDb, aliceVNodeDb, bobVNodeDb, charlieVNodeDb)
            configEmf.close()
        }

        private fun addDbConnectionConfigs(configEmf: EntityManagerFactory, vararg dbs: TestDbInfo): Map<String, UUID> {
            val ids = mutableMapOf<String, UUID>()
            dbs.forEach { db ->
                val configAsString = db.config.root().render(ConfigRenderOptions.concise())
                configEmf.transaction {
                    val existing = it.createQuery(
                        """
                        SELECT c FROM DbConnectionConfig c WHERE c.name=:name AND c.privilege=:privilege
                    """.trimIndent(), DbConnectionConfig::class.java
                    )
                        .setParameter("name", db.name)
                        .setParameter("privilege", DbPrivilege.DML)
                        .resultList
                    ids[db.name] = if (existing.isEmpty()) {
                        val record = DbConnectionConfig(
                            UUID.randomUUID(),
                            db.name,
                            DbPrivilege.DML,
                            clock.instant(),
                            "sa",
                            "Test ${db.name}",
                            configAsString
                        )
                        it.persist(record)
                        record.id
                    } else {
                        existing.first().id
                    }
                }
            }
            return ids
        }

        private val clock = TestClock(Instant.ofEpochSecond(100))
    }

    @Test
    fun `Group policy can be retrieved for valid holding identity`() {
        assertGroupPolicy(
            getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity)
        )
    }

    @Test
    fun `Additional group policy reads return the same (cached) instance`() {
        assertEquals(
            getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity),
            getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity)
        )
    }

    @Test
    fun `Get group policy fails for unknown holding identity`() {
        getGroupPolicyFails(
            groupPolicyProvider,
            invalidHoldingIdentity
        )
    }

    @Test
    fun `Group policy object is updated when CPI info changes`() {
        // Increase duration for `eventually` usage since the expected change needs to propagate through
        // multiple components
        val waitDuration = 10.seconds

        val groupPolicy1 = getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity)
        publisher.publishRawGroupPolicyData(
            virtualNodeInfoReader,
            cpiInfoReader,
            aliceHoldingIdentity,
            connectionIds.getValue(aliceVNodeDb.name),
            groupPolicy = sampleGroupPolicy2
        )

        eventually(duration = waitDuration) {
            assertSecondGroupPolicy(
                getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity),
                groupPolicy1
            )
        }
        publisher.publishRawGroupPolicyData(
            virtualNodeInfoReader,
            cpiInfoReader,
            aliceHoldingIdentity,
            connectionIds.getValue(aliceVNodeDb.name),
            groupPolicy = sampleGroupPolicy1
        )

        // Wait for the group policy change to be visible (so following tests don't fail as a result)
        eventually(duration = waitDuration) {
            assertEquals(
                groupPolicy1.groupId,
                getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity).groupId
            )
        }
    }

    /**
     * Test assumes the group policy file is configured to use the static member registration.
     */
    @Test
    fun `Register and view static member list`() {
        register(registrationProxy, aliceHoldingIdentity)

        val aliceGroupReader = eventually {
            membershipGroupReaderProvider.getGroupReader(aliceHoldingIdentity).also {
                assertEquals(aliceX500Name, it.owningMember)
                assertEquals(groupId, it.groupId)
            }
        }

        assertLookupSize(aliceGroupReader, 1)

        register(registrationProxy, bobHoldingIdentity)

        val aliceMemberInfo = lookup(aliceGroupReader, aliceX500Name)
        val bobMemberInfo = lookup(aliceGroupReader, bobX500Name)

        assertEquals(aliceX500Name, aliceMemberInfo.name)
        assertEquals(bobX500Name, bobMemberInfo.name)
        assertLookupSize(aliceGroupReader, 2)

        // Charlie is inactive in the sample group policy as only active members are returned by default
        lookupFails(aliceGroupReader, charlieX500Name)

        val bobReader = eventually {
            membershipGroupReaderProvider.getGroupReader(bobHoldingIdentity).also {
                assertEquals(bobX500Name, it.owningMember)
                assertEquals(groupId, it.groupId)
            }
        }

        assertLookupSize(bobReader, 2)

        assertEquals(aliceMemberInfo, lookUpBySessionKey(aliceGroupReader, aliceMemberInfo))
        assertEquals(bobMemberInfo, lookUpBySessionKey(aliceGroupReader, bobMemberInfo))

    }
}
