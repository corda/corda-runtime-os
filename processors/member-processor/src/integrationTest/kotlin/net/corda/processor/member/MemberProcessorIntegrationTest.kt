package net.corda.processor.member

import com.typesafe.config.ConfigRenderOptions
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.HSMRegistrationClient
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.db.admin.LiquibaseSchemaMigrator
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
import net.corda.membership.exceptions.BadGroupPolicyException
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
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertSecondGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobHoldingIdentity
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.charlieName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.charlieX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicyFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getRegistrationResult
import net.corda.processor.member.MemberProcessorTestUtils.Companion.groupId
import net.corda.processor.member.MemberProcessorTestUtils.Companion.isStarted
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookUpFromPublicKey
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookup
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookupFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.makeBootstrapConfig
import net.corda.processor.member.MemberProcessorTestUtils.Companion.makeMessagingConfig
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishMessagingConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishRawGroupPolicyData
import net.corda.processor.member.MemberProcessorTestUtils.Companion.sampleGroupPolicy1
import net.corda.processor.member.MemberProcessorTestUtils.Companion.sampleGroupPolicy2
import net.corda.processor.member.MemberProcessorTestUtils.Companion.startAndWait
import net.corda.processor.member.MemberProcessorTestUtils.Companion.stopAndWait
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.member.MemberProcessor
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.seconds
import net.corda.virtualnode.HoldingIdentity
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
import java.util.UUID
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

        private val invalidHoldingIdentity = HoldingIdentity("", groupId)

        private val aliceVNodeId = HoldingIdentity(MemberX500Name.parse(aliceName).toString(), groupId).id

        private val bobVNodeId = HoldingIdentity(MemberX500Name.parse(bobName).toString(), groupId).id

        private val charlieVNodeId = HoldingIdentity(MemberX500Name.parse(charlieName).toString(), groupId).id

        private lateinit var testDependencies: TestDependenciesTracker

        private val clusterDb = TestDbInfo.createConfig()

        private val cryptoDb = TestDbInfo(
            name = CordaDb.Crypto.persistenceUnitName,
            schemaName = DbSchema.CRYPTO
        )

        private val aliceVNodeDb = TestDbInfo(
            name = "vnode_crypto_$aliceVNodeId",
            schemaName = "vnode_crypto_alice"
        )

        private val bobVNodeDb = TestDbInfo(
            name = "vnode_crypto_$bobVNodeId",
            schemaName = "vnode_crypto_bob"
        )

        private val charlieVNodeDb = TestDbInfo(
            name = "vnode_crypto_$charlieVNodeId",
            schemaName = "vnode_crypto_charlie"
        )

        private val boostrapConfig = makeBootstrapConfig(
            mapOf(
                ConfigKeys.DB_CONFIG to clusterDb.config
            )
        )

        private val messagingConfig = makeMessagingConfig(boostrapConfig)

        @JvmStatic
        @BeforeAll
        fun setUp() {
            setupDatabases()

            // Set basic bootstrap config
            cryptoProcessor.start(boostrapConfig)
            memberProcessor.start(boostrapConfig)
            membershipGroupReaderProvider.start()
            hsmRegistrationClient.start()
            testDependencies = TestDependenciesTracker(
                LifecycleCoordinatorName.forComponent<MemberProcessorIntegrationTest>(),
                coordinatorFactory,
                lifecycleRegistry,
                setOf(
                    LifecycleCoordinatorName.forComponent<MemberProcessor>(),
                    LifecycleCoordinatorName.forComponent<CryptoProcessor>(),
                    LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                    LifecycleCoordinatorName.forComponent<HSMRegistrationClient>()
                )
            ).also { it.startAndWait() }

            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), messagingConfig)
            publisher.publishMessagingConf(messagingConfig)
            publisher.publishRawGroupPolicyData(virtualNodeInfoReader, cpiInfoReader, aliceHoldingIdentity)
            publisher.publishRawGroupPolicyData(virtualNodeInfoReader, cpiInfoReader, bobHoldingIdentity)

            // Wait for published content to be picked up by components.
            eventually { assertNotNull(virtualNodeInfoReader.get(aliceHoldingIdentity)) }

            testDependencies.waitUntilAllUp(Duration.ofSeconds(60))

            assignHSMs()
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            if (::testDependencies.isInitialized) {
                testDependencies.close()
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
            addDbConnectionConfigs(configEmf, cryptoDb, aliceVNodeDb, bobVNodeDb, charlieVNodeDb)
            configEmf.close()
        }

        private fun addDbConnectionConfigs(configEmf: EntityManagerFactory, vararg dbs: TestDbInfo) {
            dbs.forEach { db ->
                val configAsString = db.config.root().render(ConfigRenderOptions.concise())
                configEmf.transaction {
                    val existing = it.createQuery("""
                        SELECT c FROM DbConnectionConfig c WHERE c.name=:name AND c.privilege=:privilege
                    """.trimIndent())
                        .setParameter("name", db.name)
                        .setParameter("privilege", DbPrivilege.DML)
                        .resultList
                    if(existing.isEmpty()) {
                        val record = DbConnectionConfig(
                            UUID.randomUUID(),
                            db.name,
                            DbPrivilege.DML,
                            Instant.now(),
                            "sa",
                            "Test ${db.name}",
                            configAsString
                        )
                        it.persist(record)
                    }
                }
            }
        }

        private fun assignHSMs() {
            CryptoConsts.Categories.all.forEach {
                // cluster is assigned in the crypto processor
                if(hsmRegistrationClient.findHSM(aliceVNodeId, it) == null) {
                    hsmRegistrationClient.assignSoftHSM(aliceVNodeId, it, emptyMap())
                }
                if(hsmRegistrationClient.findHSM(bobVNodeId, it) == null) {
                    hsmRegistrationClient.assignSoftHSM(bobVNodeId, it, emptyMap())
                }
                if(hsmRegistrationClient.findHSM(charlieVNodeId, it) == null) {
                    hsmRegistrationClient.assignSoftHSM(charlieVNodeId, it, emptyMap())
                }
            }
        }
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
            invalidHoldingIdentity,
            BadGroupPolicyException::class.java
        )
    }

    @Test
    fun `Group policy fails to be read if the component stops`() {
        groupPolicyProvider.stopAndWait()
        getGroupPolicyFails(groupPolicyProvider, aliceHoldingIdentity)
        groupPolicyProvider.startAndWait()
    }

    @Test
    fun `Group policy cache is cleared after a restart (new instance is returned)`() {
        val groupPolicy1 = getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity)
        groupPolicyProvider.stopAndWait()
        groupPolicyProvider.startAndWait()
        eventually {
            assertGroupPolicy(
                getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity),
                groupPolicy1
            )
        }
    }

    @Test
    fun `Group policy cannot be retrieved if virtual node info reader dependency component goes down`() {
        val groupPolicy1 = getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity)
        virtualNodeInfoReader.stopAndWait()
        getGroupPolicyFails(groupPolicyProvider, aliceHoldingIdentity)

        virtualNodeInfoReader.startAndWait()
        groupPolicyProvider.isStarted()
        eventually {
            assertGroupPolicy(
                getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity),
                groupPolicy1
            )
        }
    }

    @Test
    fun `Group policy cannot be retrieved if CPI info reader dependency component goes down`() {
        val groupPolicy1 = getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity)
        cpiInfoReader.stopAndWait()
        getGroupPolicyFails(groupPolicyProvider, aliceHoldingIdentity)

        cpiInfoReader.startAndWait()
        groupPolicyProvider.isStarted()
        eventually {
            assertGroupPolicy(
                getGroupPolicy(groupPolicyProvider, aliceHoldingIdentity),
                groupPolicy1
            )
        }
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
        val aliceResult = getRegistrationResult(registrationProxy, aliceHoldingIdentity)
        assertEquals(MembershipRequestRegistrationOutcome.SUBMITTED, aliceResult.outcome)

        val aliceGroupReader = eventually {
            membershipGroupReaderProvider.getGroupReader(aliceHoldingIdentity).also {
                assertEquals(aliceX500Name, it.owningMember)
                assertEquals(groupId, it.groupId)
            }
        }

        assertEquals(1, aliceGroupReader.lookup().size)

        val bobResult = getRegistrationResult(registrationProxy, bobHoldingIdentity)
        assertEquals(MembershipRequestRegistrationOutcome.SUBMITTED, bobResult.outcome)

        val aliceMemberInfo = lookup(aliceGroupReader, aliceX500Name)
        val bobMemberInfo = lookup(aliceGroupReader, bobX500Name)

        assertEquals(aliceX500Name, aliceMemberInfo.name)
        assertEquals(bobX500Name, bobMemberInfo.name)
        assertEquals(2, aliceGroupReader.lookup().size)

        // Charlie is inactive in the sample group policy as only active members are returned by default
        lookupFails(aliceGroupReader, charlieX500Name)

        val bobReader = eventually {
            membershipGroupReaderProvider.getGroupReader(bobHoldingIdentity)
        }

        assertEquals(2, bobReader.lookup().size)

        assertEquals(aliceMemberInfo, lookUpFromPublicKey(aliceGroupReader, aliceMemberInfo))
        assertEquals(bobMemberInfo, lookUpFromPublicKey(aliceGroupReader, bobMemberInfo))

    }
}
