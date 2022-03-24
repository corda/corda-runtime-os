package net.corda.processor.member

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.membership.exceptions.BadGroupPolicyException
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.MembershipRequestRegistrationOutcome
import net.corda.membership.registration.provider.RegistrationProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceHoldingIdentity
import net.corda.processor.member.MemberProcessorTestUtils.Companion.aliceX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.assertSecondGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bobX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.bootConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.charlieX500Name
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicy
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getGroupPolicyFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getRegistrationService
import net.corda.processor.member.MemberProcessorTestUtils.Companion.getRegistrationServiceFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.groupId
import net.corda.processor.member.MemberProcessorTestUtils.Companion.isStarted
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookUpFromPublicKey
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookup
import net.corda.processor.member.MemberProcessorTestUtils.Companion.lookupFails
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishCryptoConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishMessagingConf
import net.corda.processor.member.MemberProcessorTestUtils.Companion.publishRawGroupPolicyData
import net.corda.processor.member.MemberProcessorTestUtils.Companion.sampleGroupPolicy2
import net.corda.processor.member.MemberProcessorTestUtils.Companion.startAndWait
import net.corda.processor.member.MemberProcessorTestUtils.Companion.stopAndWait
import net.corda.processors.crypto.CryptoProcessor
import net.corda.processors.member.MemberProcessor
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.seconds
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import kotlin.reflect.KFunction

@ExtendWith(ServiceExtension::class)
class MemberProcessorIntegrationTest {
    companion object {
        const val CLIENT_ID = "member-processor-integration-test"

        val logger = contextLogger()
    }

    @InjectService(timeout = 5000L)
    lateinit var groupPolicyProvider: GroupPolicyProvider

    @InjectService(timeout = 5000L)
    lateinit var registrationProvider: RegistrationProvider

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

    lateinit var publisher: Publisher

    private val invalidHoldingIdentity = HoldingIdentity("", groupId)

    @BeforeEach
    fun setUp() {
        // Set basic bootstrap config
        memberProcessor.start(bootConf)
        cryptoProcessor.start(bootConf)

        membershipGroupReaderProvider.start()

        publisher = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        publisher.publishCryptoConf()
        publisher.publishMessagingConf()
        publisher.publishRawGroupPolicyData(virtualNodeInfoReader, cpiInfoReader)

        // Wait for published content to be picked up by components.
        eventually { assertNotNull(virtualNodeInfoReader.get(aliceHoldingIdentity)) }
    }

    fun runTest(testFunction: KFunction<Unit>) {
        logger.info("Running test: \"${testFunction.name}\"")
        testFunction.call()
    }

    @Test
    fun `Run all tests`() {
        logger.info("Running multiple member processor related integration tests under one test run.")
        logger.info("Running ${GroupPolicyProvider::class.simpleName} tests.")
        for (test in groupPolicyProviderTests) {
            runTest(test)
        }
        logger.info("Running ${RegistrationProvider::class.simpleName} tests.")
        for (test in registrationProviderTests) {
            runTest(test)
        }
        logger.info("Finished test run.")
        logger.info("Ran ${groupPolicyProviderTests.size + registrationProviderTests.size} tests successfully.")
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
        publisher.publishRawGroupPolicyData(virtualNodeInfoReader, cpiInfoReader)

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
    val registrationProviderTests = listOf(
        ::`Register and view static member list`,
        ::`Registration provider fails to get registration service if it is down`,
        ::`Registration service fails to register if it is down`,
    )

    /**
     * Test assumes the group policy file is configured to use the static member registration.
     */
    fun `Register and view static member list`() {
        val registrationService = getRegistrationService(registrationProvider)
        val result = registrationService.register(aliceHoldingIdentity)
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

    fun `Registration provider fails to get registration service if it is down`() {
        // bringing down the group policy provider brings down the static registration service
        groupPolicyProvider.stopAndWait()

        getRegistrationServiceFails(registrationProvider)

        // bring back up
        groupPolicyProvider.startAndWait()

        // Wait for it to pass again before moving to next test
        getRegistrationService(registrationProvider)
    }

    fun `Registration service fails to register if it is down`() {
        val registrationService = getRegistrationService(registrationProvider)

        // bringing down the group policy provider brings down the static registration service
        groupPolicyProvider.stopAndWait()

        val registrationOutcome = registrationService
            .register(aliceHoldingIdentity)
            .outcome

        assertEquals(MembershipRequestRegistrationOutcome.NOT_SUBMITTED, registrationOutcome)

        // bring back up
        groupPolicyProvider.startAndWait()

        // Wait for it to pass again before moving to next test
        getRegistrationService(registrationProvider)
    }
}
