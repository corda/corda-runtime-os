package net.corda.processor.member

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.persistence.db.model.CryptoEntities
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DatabaseInstaller
import net.corda.db.testkit.TestDbInfo
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.datamodel.ConfigurationEntities
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
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceHoldingIdentity
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertSecondGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bootConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.charlieName
import net.corda.processor.member.MemberProcessorTestUtils.Companion.charlieX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicyFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getRegistrationResult
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getRegistrationResultFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.groupId
import net.corda.processor.member.MemberProcessorTestUtils.Companion.isStarted
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookUpFromPublicKey
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookup
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookupFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishCryptoConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishMessagingConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishRawGroupPolicyData
import net.corda.processor.member.MemberProcessorTestUtils.Companion.sampleGroupPolicy1
import net.corda.processor.member.MemberProcessorTestUtils.Companion.sampleGroupPolicy2
import net.corda.processor.member.MemberProcessorTestUtils.Companion.startAndWait
import net.corda.processor.member.MemberProcessorTestUtils.Companion.stopAndWait
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.member.MemberProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.time.Duration
import kotlin.reflect.KFunction

@ExtendWith(ServiceExtension::class)
class MemberProcessorIntegrationTest {
    companion object {
        const val CLIENT_ID = "member-processor-integration-test"
        val logger = contextLogger()


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
        lateinit var dbConnectionManager: DbConnectionManager

        @InjectService(timeout = 5000)
        lateinit var entitiesRegistry: JpaEntitiesRegistry

        @InjectService(timeout = 5000)
        lateinit var entityManagerFactoryFactory: EntityManagerFactoryFactory

        @InjectService(timeout = 5000)
        lateinit var lbm: LiquibaseSchemaMigrator

        @InjectService(timeout = 5000)
        lateinit var softCryptoServiceProvider: SoftCryptoServiceProvider

        @InjectService(timeout = 5000L)
        lateinit var coordinatorFactory: LifecycleCoordinatorFactory

        @InjectService(timeout = 5000L)
        lateinit var lifecycleRegistry: LifecycleRegistry

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

        @JvmStatic
        @BeforeAll
        fun setUp() {
            setupDatabases()

            // Set basic bootstrap config
            memberProcessor.start(bootConf)
            cryptoProcessor.start(bootConf)

            membershipGroupReaderProvider.start()

            testDependencies = TestDependenciesTracker(
                LifecycleCoordinatorName.forComponent<MemberProcessorIntegrationTest>(),
                coordinatorFactory,
                lifecycleRegistry,
                setOf(
                    //LifecycleCoordinatorName.forComponent<MemberProcessor>(),
                    LifecycleCoordinatorName.forComponent<CryptoProcessor>(),
                    //LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>()
                )
            ).also { it.startAndWait() }

            publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID), SmartConfigImpl.empty())
            publisher.publishCryptoConf()
            publisher.publishMessagingConf()
            publisher.publishRawGroupPolicyData(virtualNodeInfoReader, cpiInfoReader)

            // Wait for published content to be picked up by components.
            eventually { assertNotNull(virtualNodeInfoReader.get(aliceHoldingIdentity)) }

            dbConnectionManager.bootstrap(clusterDb.config)
            testDependencies.waitUntilAllUp(Duration.ofSeconds(60))
            addDbConnectionConfigs(cryptoDb, aliceVNodeDb, bobVNodeDb, charlieVNodeDb)
            softCryptoServiceProvider.getInstance(
                SoftCryptoServiceConfig(
                    passphrase = "PASSPHRASE",
                    salt = "SALT"
                )
            ).createWrappingKey("wrapping-key", false, emptyMap())
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            if(::testDependencies.isInitialized) {
                testDependencies.close()
            }
        }

        private fun setupDatabases() {
            val databaseInstaller = DatabaseInstaller(entityManagerFactoryFactory, lbm, entitiesRegistry)
            databaseInstaller.setupClusterDatabase(
                clusterDb,
                "config",
                ConfigurationEntities.classes
            ).close()
            databaseInstaller.setupDatabase(
                cryptoDb,
                "crypto",
                CryptoEntities.classes
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
        }

        private fun addDbConnectionConfigs(vararg dbs: TestDbInfo) {
            dbs.forEach { db ->
                dbConnectionManager.putConnection(
                    name = db.name,
                    privilege = DbPrivilege.DML,
                    config = db.config,
                    description = null,
                    updateActor = "sa"
                )
            }
        }

        private fun runTest(testFunction: KFunction<Unit>) {
            logger.info("Running test: \"${testFunction.name}\"")
            testFunction.call()
        }
    }

    @Test
    fun `Run all tests`() {
        logger.info("Running multiple member processor related integration tests under one test run.")
        logger.info("Running ${GroupPolicyProvider::class.simpleName} tests.")
        for (test in groupPolicyProviderTests) {
            runTest(test)
        }
        logger.info("Running ${RegistrationProxy::class.simpleName} tests.")
        for (test in registrationProxyTests) {
            runTest(test)
        }
        logger.info("Finished test run.")
        logger.info("Ran ${groupPolicyProviderTests.size + registrationProxyTests.size} tests successfully.")
    }

    /**
     * Group Policy provider tests.
     */
    val groupPolicyProviderTests = listOf(
        ::`Group policy can be retrieved for valid holding identity`,
        ::`Additional group policy reads return the same (cached) instance`,
        ::`Get group policy fails for unknown holding identity`,
        ::`Group policy fails to be read if the component stops`,
        ::`Group policy cache is cleared after a restart (new instance is returned)`,
        ::`Group policy cannot be retrieved if virtual node info reader dependency component goes down`,
        ::`Group policy cannot be retrieved if CPI info reader dependency component goes down`,
        ::`Group policy object is updated when CPI info changes`
    )

    fun `Group policy can be retrieved for valid holding identity`() {
        assertGroupPolicy(getGroupPolicy(groupPolicyProvider))
    }

    fun `Additional group policy reads return the same (cached) instance`() {
        assertEquals(getGroupPolicy(groupPolicyProvider), getGroupPolicy(groupPolicyProvider))
    }

    fun `Get group policy fails for unknown holding identity`() {
        getGroupPolicyFails(groupPolicyProvider, invalidHoldingIdentity, BadGroupPolicyException::class.java)
    }

    fun `Group policy fails to be read if the component stops`() {
        groupPolicyProvider.stopAndWait()
        getGroupPolicyFails(groupPolicyProvider)
        groupPolicyProvider.startAndWait()
    }

    fun `Group policy cache is cleared after a restart (new instance is returned)`() {
        val groupPolicy1 = getGroupPolicy(groupPolicyProvider)
        groupPolicyProvider.stopAndWait()
        groupPolicyProvider.startAndWait()
        eventually {
            assertGroupPolicy(
                getGroupPolicy(groupPolicyProvider),
                groupPolicy1
            )
        }
    }

    fun `Group policy cannot be retrieved if virtual node info reader dependency component goes down`() {
        val groupPolicy1 = getGroupPolicy(groupPolicyProvider)
        virtualNodeInfoReader.stopAndWait()
        getGroupPolicyFails(groupPolicyProvider)

        virtualNodeInfoReader.startAndWait()
        groupPolicyProvider.isStarted()
        eventually {
            assertGroupPolicy(
                getGroupPolicy(groupPolicyProvider),
                groupPolicy1
            )
        }
    }

    fun `Group policy cannot be retrieved if CPI info reader dependency component goes down`() {
        val groupPolicy1 = getGroupPolicy(groupPolicyProvider)
        cpiInfoReader.stopAndWait()
        getGroupPolicyFails(groupPolicyProvider)

        cpiInfoReader.startAndWait()
        groupPolicyProvider.isStarted()
        eventually {
            assertGroupPolicy(
                getGroupPolicy(groupPolicyProvider),
                groupPolicy1
            )
        }
    }

    fun `Group policy object is updated when CPI info changes`() {
        // Increase duration for `eventually` usage since the expected change needs to propagate through
        // multiple components
        val waitDuration = 10.seconds

        val groupPolicy1 = getGroupPolicy(groupPolicyProvider)
        publisher.publishRawGroupPolicyData(virtualNodeInfoReader, cpiInfoReader, groupPolicy = sampleGroupPolicy2)

        eventually(duration = waitDuration) {
            assertSecondGroupPolicy(
                getGroupPolicy(groupPolicyProvider),
                groupPolicy1
            )
        }
        publisher.publishRawGroupPolicyData(virtualNodeInfoReader, cpiInfoReader, groupPolicy = sampleGroupPolicy1)

        // Wait for the group policy change to be visible (so following tests don't fail as a result)
        eventually(duration = waitDuration) {
            assertEquals(
                groupPolicy1.groupId,
                getGroupPolicy(groupPolicyProvider).groupId
            )
        }
    }

    /**
     * Registration provider tests.
     */
    val registrationProxyTests = listOf(
        ::`Register and view static member list`,
        //::`Registration proxy fails to register if registration service is down`
    )

    /**
     * Test assumes the group policy file is configured to use the static member registration.
     */
    fun `Register and view static member list`() {
        val result = getRegistrationResult(registrationProxy)
        assertEquals(MembershipRequestRegistrationOutcome.SUBMITTED, result.outcome)

        val groupReader = eventually {
            membershipGroupReaderProvider.getGroupReader(aliceHoldingIdentity).also {
                assertEquals(aliceX500Name, it.owningMember)
                assertEquals(groupId, it.groupId)
            }
        }

        val aliceMemberInfo = lookup(groupReader, aliceX500Name)
        val bobMemberInfo = lookup(groupReader, bobX500Name)

        // Charlie is inactive in the sample group policy as only active members are returned by default
        lookupFails(groupReader, charlieX500Name)

        assertEquals(aliceX500Name, aliceMemberInfo.name)
        assertEquals(bobX500Name, bobMemberInfo.name)

        assertEquals(aliceMemberInfo, lookUpFromPublicKey(groupReader, aliceMemberInfo))
        assertEquals(bobMemberInfo, lookUpFromPublicKey(groupReader, bobMemberInfo))

    }

    fun `Registration proxy fails to register if registration service is down`() {
        // bringing down the group policy provider brings down the static registration service
        groupPolicyProvider.stopAndWait()

        getRegistrationResultFails(registrationProxy)

        // bring back up
        groupPolicyProvider.startAndWait()

        // Wait for it to pass again before moving to next test
        getRegistrationResult(registrationProxy)
    }
}
