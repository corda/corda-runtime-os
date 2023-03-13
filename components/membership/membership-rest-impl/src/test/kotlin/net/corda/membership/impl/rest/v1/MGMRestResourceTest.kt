package net.corda.membership.impl.rest.v1

import net.corda.configuration.read.ConfigurationGetService
import net.corda.crypto.core.ShortHash
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType.PREAUTH
import net.corda.data.membership.common.ApprovalRuleType.STANDARD
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.rest.v1.types.request.ApprovalRuleRequestParams
import net.corda.membership.rest.v1.types.request.PreAuthTokenRequest
import net.corda.membership.rest.v1.types.response.PreAuthToken
import net.corda.membership.rest.v1.types.response.PreAuthTokenStatus
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.rest.v1.types.request.SuspensionActivationParameters
import net.corda.rest.exception.InvalidStateChangeException
import net.corda.schema.configuration.ConfigKeys.P2P_GATEWAY_CONFIG
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.persistence.PersistenceException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.corda.data.membership.preauth.PreAuthToken as AvroPreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus as AvroPreAuthTokenStatus

class MGMRestResourceTest {
    private companion object {
        const val HOLDING_IDENTITY_ID = "111213141500"
        const val INVALID_SHORT_HASH = "ABS09234745D"
        const val RULE_REGEX = "rule-regex"
        const val INVALID_RULE_REGEX = "*"
        const val RULE_LABEL = "rule-label"
        const val RULE_ID = "rule-id"
        const val REQUEST_ID = "b305129b-8c92-4092-b3a2-e6d452ce2b01"
        const val REASON = "test"
        const val SERIAL = 1L

        fun String.shortHash() = ShortHash.of(this)
        fun String.uuid(): UUID = UUID.fromString(this)
    }

    private var coordinatorIsRunning = false
    private val coordinator: LifecycleCoordinator = mock {
        on { isRunning } doAnswer { coordinatorIsRunning }
        on { start() } doAnswer { coordinatorIsRunning = true }
        on { stop() } doAnswer { coordinatorIsRunning = false }
    }

    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val mgmGenerateGroupPolicyResponseDto = "grouppolicy"

    private val mgmResourceClient: MGMResourceClient = mock {
        on { generateGroupPolicy(any()) } doReturn mgmGenerateGroupPolicyResponseDto
    }
    private val subject = "CN=Alice, O=Alice ,L=London ,C=GB"
    private val gatewayConfiguration = mock<SmartConfig> {
        on { getConfig("sslConfig") } doReturn mock
        on { getString("tlsType") } doReturn "MUTUAL"
    }
    private val configurationGetService = mock<ConfigurationGetService> {
        on { getSmartConfig(P2P_GATEWAY_CONFIG) } doReturn gatewayConfiguration
    }
    private val initialTime = Instant.parse("2007-12-03T00:00:00.00Z")
    private val manualDeclinationReason = REASON
    private val suspensionActivationParameters = SuspensionActivationParameters(subject, 1, REASON)

    private val mgmRestResource = MGMRestResourceImpl(
        lifecycleCoordinatorFactory,
        mgmResourceClient,
        configurationGetService,
        clock = MockTimeFacilitiesProvider(initialTime).clock
    )

    private fun startService() {
        mgmRestResource.start()
        mgmRestResource.activate("")
    }

    private fun stopService() {
        mgmRestResource.deactivate("")
        mgmRestResource.stop()
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        mgmRestResource.start()
        assertTrue(mgmRestResource.isRunning)
        mgmRestResource.stop()
        assertFalse(mgmRestResource.isRunning)
    }

    @Test
    fun `operation fails when svc is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            mgmRestResource.generateGroupPolicy(HOLDING_IDENTITY_ID)
        }
        assertEquals("MGMRestResourceImpl is not running. Operation cannot be fulfilled.", ex.message)
    }

    @Nested
    inner class GenerateGroupPolicyTests {
        @Test
        fun `generateGroupPolicy calls the client svc`() {
            startService()
            mgmRestResource.generateGroupPolicy(HOLDING_IDENTITY_ID)
            verify(mgmResourceClient).generateGroupPolicy(eq(HOLDING_IDENTITY_ID.shortHash()))
            stopService()
        }

        @Test
        fun `generateGroupPolicy throws resource not found for invalid member`() {
            startService()
            whenever(mgmResourceClient.generateGroupPolicy(any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.generateGroupPolicy(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `generateGroupPolicy throws invalid input for non MGM member`() {
            startService()
            whenever(mgmResourceClient.generateGroupPolicy(any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.generateGroupPolicy(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `generateGroupPolicy throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRestResource.generateGroupPolicy("INVALID_SHORT_HASH")
            }
        }
    }

    @Nested
    inner class AddGroupApprovalRuleTests {
        @Test
        fun `addGroupApprovalRule delegates correctly to mgm ops client`() {
            startService()
            whenever(mgmResourceClient.addApprovalRule(any(), any())).doReturn(ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL))

            mgmRestResource.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))

            verify(mgmResourceClient).addApprovalRule(
                eq(HOLDING_IDENTITY_ID.shortHash()),
                eq(ApprovalRuleParams(RULE_REGEX, STANDARD, RULE_LABEL))
            )
            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws resource not found for invalid member`() {
            startService()
            whenever(mgmResourceClient.addApprovalRule(any(), any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws invalid input for non MGM member`() {
            startService()
            whenever(mgmResourceClient.addApprovalRule(any(), any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRestResource.addGroupApprovalRule(INVALID_SHORT_HASH, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws bad request for duplicate rule`() {
            startService()
            whenever(mgmResourceClient.addApprovalRule(any(), any())).doThrow(mock<MembershipPersistenceException>())


            assertThrows<BadRequestException> {
                mgmRestResource.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws bad request for invalid regex syntax`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRestResource.addGroupApprovalRule(
                    HOLDING_IDENTITY_ID,
                    ApprovalRuleRequestParams(INVALID_RULE_REGEX, RULE_LABEL)
                )
            }

            stopService()
        }
    }

    @Nested
    inner class DeleteGroupApprovalRuleTests {
        @Test
        fun `deleteGroupApprovalRule delegates correctly to mgm ops client`() {
            startService()

            mgmRestResource.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)

            verify(mgmResourceClient).deleteApprovalRule(eq(HOLDING_IDENTITY_ID.shortHash()), eq(RULE_ID), eq(STANDARD))
            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws resource not found for invalid member`() {
            startService()
            whenever(mgmResourceClient.deleteApprovalRule(any(), any(), eq(STANDARD))).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)
            }

            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws resource not found for non-existent rule`() {
            startService()
            whenever(mgmResourceClient.deleteApprovalRule(any(), any(), eq(STANDARD))).doThrow(mock<MembershipPersistenceException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)
            }

            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws invalid input for non MGM member`() {
            startService()
            whenever(mgmResourceClient.deleteApprovalRule(any(), any(), eq(STANDARD))).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)
            }

            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRestResource.deleteGroupApprovalRule(INVALID_SHORT_HASH, RULE_ID)
            }

            stopService()
        }
    }

    @Nested
    inner class GetGroupApprovalRulesTests {
        @Test
        fun `getGroupApprovalRules delegates correctly to mgm ops client`() {
            startService()

            mgmRestResource.getGroupApprovalRules(HOLDING_IDENTITY_ID)

            verify(mgmResourceClient).getApprovalRules(eq(HOLDING_IDENTITY_ID.shortHash()), eq(STANDARD))
            stopService()
        }

        @Test
        fun `getGroupApprovalRules throws resource not found for invalid member`() {
            startService()
            whenever(mgmResourceClient.getApprovalRules(any(), any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.getGroupApprovalRules(HOLDING_IDENTITY_ID)
            }

            stopService()
        }

        @Test
        fun `getGroupApprovalRules throws invalid input for non MGM member`() {
            startService()
            whenever(mgmResourceClient.getApprovalRules(any(), any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.getGroupApprovalRules(HOLDING_IDENTITY_ID)
            }

            stopService()
        }

        @Test
        fun `getGroupApprovalRules throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRestResource.getGroupApprovalRules(INVALID_SHORT_HASH)
            }

            stopService()
        }
    }

    @Nested
    inner class ViewRegistrationRequestsTests {
        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `viewRegistrationRequests delegates correctly to mgm ops client`() {
            mgmRestResource.viewRegistrationRequests(HOLDING_IDENTITY_ID)

            verify(mgmResourceClient).viewRegistrationRequests(eq((ShortHash.of(HOLDING_IDENTITY_ID))), eq(null), eq(false))
        }

        @Test
        fun `viewRegistrationRequests throws resource not found for invalid member`() {
            whenever(mgmResourceClient.viewRegistrationRequests(any(), eq(null), eq(false))).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.viewRegistrationRequests(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `viewRegistrationRequests throws invalid input for non MGM member`() {
            whenever(mgmResourceClient.viewRegistrationRequests(any(), eq(null), eq(false))).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.viewRegistrationRequests(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `viewRegistrationRequests throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                mgmRestResource.viewRegistrationRequests(INVALID_SHORT_HASH)
            }
        }
    }

    @Nested
    inner class ApproveRegistrationRequestTests {
        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `approveRegistrationRequest delegates correctly to mgm ops client`() {
            mgmRestResource.approveRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)

            verify(mgmResourceClient).reviewRegistrationRequest(
                (ShortHash.of(HOLDING_IDENTITY_ID)), REQUEST_ID.uuid(), true
            )
        }

        @Test
        fun `approveRegistrationRequest throws resource not found for invalid member`() {
            whenever(mgmResourceClient.reviewRegistrationRequest(
                ShortHash.of(HOLDING_IDENTITY_ID), REQUEST_ID.uuid(), true
            )).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.approveRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)
            }
        }

        @Test
        fun `approveRegistrationRequest throws invalid input for non MGM member`() {
            whenever(mgmResourceClient.reviewRegistrationRequest(
                ShortHash.of(HOLDING_IDENTITY_ID), REQUEST_ID.uuid(), true
            )).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.approveRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)
            }
        }

        @Test
        fun `approveRegistrationRequest throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                mgmRestResource.approveRegistrationRequest(INVALID_SHORT_HASH, REQUEST_ID)
            }
        }

        @Test
        fun `approveRegistrationRequest throws bad request if request is not found or is not pending review`() {
            whenever(mgmResourceClient.reviewRegistrationRequest(
                ShortHash.of(HOLDING_IDENTITY_ID), REQUEST_ID.uuid(), true
            )).doThrow(mock<IllegalArgumentException>())

            assertThrows<BadRequestException> {
                mgmRestResource.approveRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID)
            }
        }
    }

    @Nested
    inner class DeclineRegistrationRequestTests {
        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `declineRegistrationRequest delegates correctly to mgm ops client`() {
            mgmRestResource.declineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID, manualDeclinationReason)

            verify(mgmResourceClient).reviewRegistrationRequest(
                (ShortHash.of(HOLDING_IDENTITY_ID)), REQUEST_ID.uuid(), false, manualDeclinationReason
            )
        }

        @Test
        fun `declineRegistrationRequest throws resource not found for invalid member`() {
            whenever(mgmResourceClient.reviewRegistrationRequest(
                ShortHash.of(HOLDING_IDENTITY_ID), REQUEST_ID.uuid(), false, manualDeclinationReason
            )).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.declineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID, manualDeclinationReason)
            }
        }

        @Test
        fun `declineRegistrationRequest throws invalid input for non MGM member`() {
            whenever(mgmResourceClient.reviewRegistrationRequest(
                ShortHash.of(HOLDING_IDENTITY_ID), REQUEST_ID.uuid(), false, manualDeclinationReason
            )).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.declineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID, manualDeclinationReason)
            }
        }

        @Test
        fun `declineRegistrationRequest throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                mgmRestResource.declineRegistrationRequest(INVALID_SHORT_HASH, REQUEST_ID, manualDeclinationReason)
            }
        }

        @Test
        fun `declineRegistrationRequest throws bad request if request is not found or is not pending review`() {
            whenever(mgmResourceClient.reviewRegistrationRequest(
                ShortHash.of(HOLDING_IDENTITY_ID), REQUEST_ID.uuid(), false, manualDeclinationReason
            )).doThrow(mock<IllegalArgumentException>())

            assertThrows<BadRequestException> {
                mgmRestResource.declineRegistrationRequest(HOLDING_IDENTITY_ID, REQUEST_ID, manualDeclinationReason)
            }
        }
    }

    @Nested
    inner class MutualTlsAllowClientCertificateTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRestResource.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when mutual tls is not enabled`() {
            startService()
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRestResource.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when the subject is not a valid X500 name`() {
            startService()

            assertThrows<InvalidInputDataException> {
                mgmRestResource.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, "Invalid")
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            startService()
            whenever(
                mgmResourceClient.mutualTlsAllowClientCertificate(
                    any(),
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(HOLDING_IDENTITY_ID.shortHash()))

            assertThrows<InvalidInputDataException> {
                mgmRestResource.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it sends the request to the client`() {
            startService()

            mgmRestResource.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)

            verify(mgmResourceClient).mutualTlsAllowClientCertificate(
                HOLDING_IDENTITY_ID.shortHash(),
                MemberX500Name.parse(subject),
            )
        }
    }

    @Nested
    inner class MutualTlsDisallowClientCertificateTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRestResource.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when mutual tls is not enabled`() {
            startService()
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRestResource.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when the subject is not a valid X500 name`() {
            startService()

            assertThrows<InvalidInputDataException> {
                mgmRestResource.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, "Invalid")
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            startService()
            whenever(
                mgmResourceClient.mutualTlsDisallowClientCertificate(
                    any(),
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(HOLDING_IDENTITY_ID.shortHash()))

            assertThrows<InvalidInputDataException> {
                mgmRestResource.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it sends the request to the client`() {
            startService()

            mgmRestResource.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)

            verify(mgmResourceClient).mutualTlsDisallowClientCertificate(
                HOLDING_IDENTITY_ID.shortHash(),
                MemberX500Name.parse(subject),
            )
        }
    }


    @Nested
    inner class MutualTlsListClientCertificateTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRestResource.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it fails when mutual tls is not enabled`() {
            startService()
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRestResource.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            startService()
            whenever(
                mgmResourceClient.mutualTlsListClientCertificate(
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(HOLDING_IDENTITY_ID.shortHash()))

            assertThrows<InvalidInputDataException> {
                mgmRestResource.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it returns the list from the client`() {
            startService()
            val parsedSubject = MemberX500Name.parse(subject)
            whenever(
                mgmResourceClient.mutualTlsListClientCertificate(
                    HOLDING_IDENTITY_ID.shortHash()
                )
            ).doReturn(listOf(parsedSubject))

            val list = mgmRestResource.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)

            assertThat(list)
                .containsExactly(parsedSubject.toString())
        }
    }

    @Nested
    inner class AddPreAuthGroupApprovalRuleTests {
        /**
         * Function for calling function under test to avoid calling the wrong function since there are similar tests
         * for non pre-auth token tests.
         */
        private fun callFunctionUnderTest(
            holdingIdentity: String,
            requestParams: ApprovalRuleRequestParams
        ) = mgmRestResource.addPreAuthGroupApprovalRule(
            holdingIdentity,
            requestParams
        )

        private fun onCallingClientService() = whenever(
            mgmResourceClient.addApprovalRule(any(), argThat { ruleType == PREAUTH })
        )

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `it delegates call to mgm ops client`() {
            onCallingClientService().doReturn(ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL))

            callFunctionUnderTest(
                HOLDING_IDENTITY_ID,
                ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL)
            )

            verify(mgmResourceClient).addApprovalRule(
                eq(HOLDING_IDENTITY_ID.shortHash()),
                eq(ApprovalRuleParams(RULE_REGEX, PREAUTH, RULE_LABEL))
            )
        }

        @Test
        fun `it maps result to expected http type`() {
            onCallingClientService().doReturn(ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL))

            callFunctionUnderTest(
                HOLDING_IDENTITY_ID,
                ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL)
            ).apply {
                assertThat(ruleId).isEqualTo(RULE_ID)
                assertThat(ruleRegex).isEqualTo(RULE_REGEX)
                assertThat(ruleLabel).isNotNull.isEqualTo(RULE_LABEL)
            }
        }

        @Test
        fun `it maps result to expected http type without a label`() {
            onCallingClientService().doReturn(ApprovalRuleDetails(RULE_ID, RULE_REGEX, null))

            callFunctionUnderTest(
                HOLDING_IDENTITY_ID,
                ApprovalRuleRequestParams(RULE_REGEX)
            ).apply {
                assertThat(ruleId).isEqualTo(RULE_ID)
                assertThat(ruleRegex).isEqualTo(RULE_REGEX)
                assertThat(ruleLabel).isNull()
            }
        }

        @Test
        fun `it throws resource not found for invalid member`() {
            onCallingClientService().doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                callFunctionUnderTest(
                    HOLDING_IDENTITY_ID,
                    ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL)
                )
            }
        }

        @Test
        fun `it throws invalid input for non MGM member`() {
            onCallingClientService().doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                callFunctionUnderTest(
                    HOLDING_IDENTITY_ID,
                    ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL)
                )
            }
        }

        @Test
        fun `it throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                callFunctionUnderTest(
                    INVALID_SHORT_HASH,
                    ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL)
                )
            }
        }

        @Test
        fun `it throws bad request for duplicate rule`() {
            onCallingClientService().doThrow(mock<MembershipPersistenceException>())

            assertThrows<BadRequestException> {
                callFunctionUnderTest(
                    HOLDING_IDENTITY_ID,
                    ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL)
                )
            }
        }

        @Test
        fun `it throws bad request for invalid regex syntax`() {
            assertThrows<BadRequestException> {
                callFunctionUnderTest(
                    HOLDING_IDENTITY_ID,
                    ApprovalRuleRequestParams(INVALID_RULE_REGEX, RULE_LABEL)
                )
            }
        }
    }

    @Nested
    inner class GetPreAuthGroupApprovalRulesTests {
        private fun onCallingClientService() = whenever(
            mgmResourceClient.getApprovalRules(any(), eq(PREAUTH))
        )

        private fun callFunctionUnderTest(
            holdingIdentity: String = HOLDING_IDENTITY_ID
        ) = mgmRestResource.getPreAuthGroupApprovalRules(holdingIdentity)

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `it delegates correctly to mgm ops client`() {
            callFunctionUnderTest()

            verify(mgmResourceClient).getApprovalRules(
                eq(HOLDING_IDENTITY_ID.shortHash()),
                eq(PREAUTH)
            )
        }

        @Test
        fun `it returns the client response mapped to http type`() {
            onCallingClientService().thenReturn(
                listOf(ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL))
            )
            callFunctionUnderTest().apply {
                assertThat(this).hasSize(1)
                val info = this.first()
                assertThat(info.ruleId).isEqualTo(RULE_ID)
                assertThat(info.ruleRegex).isEqualTo(RULE_REGEX)
                assertThat(info.ruleLabel).isNotNull.isEqualTo(RULE_LABEL)
            }
        }

        @Test
        fun `it returns the client response mapped to http type with nullable label`() {
            onCallingClientService().thenReturn(
                listOf(ApprovalRuleDetails(RULE_ID, RULE_REGEX, null))
            )
            callFunctionUnderTest().apply {
                assertThat(this).hasSize(1)
                val info = this.first()
                assertThat(info.ruleId).isEqualTo(RULE_ID)
                assertThat(info.ruleRegex).isEqualTo(RULE_REGEX)
                assertThat(info.ruleLabel).isNull()
            }
        }

        @Test
        fun `it throws resource not found for invalid member`() {
            onCallingClientService().doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                callFunctionUnderTest()
            }
        }

        @Test
        fun `it throws invalid input for non MGM member`() {
            onCallingClientService().doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                callFunctionUnderTest()
            }
        }

        @Test
        fun `it throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                callFunctionUnderTest(INVALID_SHORT_HASH)
            }
        }
    }

    @Nested
    inner class DeletePreAuthGroupApprovalRuleTests {
        /**
         * Function for calling function under test to avoid calling the wrong function since there are similar tests
         * for non pre-auth token tests.
         */
        private fun callFunctionUnderTest(
            holdingIdentity: String,
            ruleId: String
        ) = mgmRestResource.deletePreAuthGroupApprovalRule(
            holdingIdentity,
            ruleId
        )

        private fun whenCallingClientService() = whenever(
            mgmResourceClient.deleteApprovalRule(any(), any(), eq(PREAUTH))
        )

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `deleteGroupApprovalRule delegates correctly to mgm ops client`() {
            callFunctionUnderTest(HOLDING_IDENTITY_ID, RULE_ID)

            verify(mgmResourceClient).deleteApprovalRule(
                eq(HOLDING_IDENTITY_ID.shortHash()),
                eq(RULE_ID),
                eq(PREAUTH)
            )
        }

        @Test
        fun `deleteGroupApprovalRule throws resource not found for invalid member`() {
            whenCallingClientService().doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                callFunctionUnderTest(HOLDING_IDENTITY_ID, RULE_ID)
            }
        }

        @Test
        fun `deleteGroupApprovalRule throws resource not found for non-existent rule`() {
            whenCallingClientService().doThrow(mock<MembershipPersistenceException>())

            assertThrows<ResourceNotFoundException> {
                callFunctionUnderTest(HOLDING_IDENTITY_ID, RULE_ID)
            }
        }

        @Test
        fun `deleteGroupApprovalRule throws invalid input for non MGM member`() {
            whenCallingClientService().doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                callFunctionUnderTest(HOLDING_IDENTITY_ID, RULE_ID)
            }
        }

        @Test
        fun `deleteGroupApprovalRule throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                callFunctionUnderTest(INVALID_SHORT_HASH, RULE_ID)
            }
        }
    }

    @Nested
    inner class GeneratePreAuthTokenTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRestResource.generatePreAuthToken(HOLDING_IDENTITY_ID, PreAuthTokenRequest(subject))
            }
        }

        @Test
        fun `it fails when the owner x500Name is invalid`() {
            startService()
            assertThrows<InvalidInputDataException> {
                mgmRestResource.generatePreAuthToken(HOLDING_IDENTITY_ID, PreAuthTokenRequest("Invalid X500Name"))
            }
        }

        @Test
        fun `it fails when the holding identity does not exist`() {
            startService()
            whenever(
                mgmResourceClient.generatePreAuthToken(any(), any(), anyOrNull(), anyOrNull())
            ).doThrow(CouldNotFindMemberException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.generatePreAuthToken(HOLDING_IDENTITY_ID, PreAuthTokenRequest(subject, Duration.ofDays(5)))
            }
        }

        @Test
        fun `it fails when the holding identity is not an MGM`() {
            startService()
            whenever(
                mgmResourceClient.generatePreAuthToken(any(), any(), anyOrNull(), anyOrNull())
            ).doThrow(MemberNotAnMgmException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<InvalidInputDataException> {
                mgmRestResource.generatePreAuthToken(HOLDING_IDENTITY_ID, PreAuthTokenRequest(subject, Duration.ofDays(5)))
            }
        }

        @Test
        fun `it returns the correct result from the client`() {
            startService()
            val tokenId = "tokenId"
            val ttl = Duration.ofDays(5)
            val remark = "Remark"
            val removalRemark = "RemovalRemark"
            val expiryTimestamp = initialTime.plus(5, ChronoUnit.DAYS)
            whenever(
                mgmResourceClient.generatePreAuthToken(
                    ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject), expiryTimestamp, remark
                )
            ).doReturn(AvroPreAuthToken(tokenId, subject, expiryTimestamp, AvroPreAuthTokenStatus.AVAILABLE, remark, removalRemark))

            val token = mgmRestResource.generatePreAuthToken(HOLDING_IDENTITY_ID, PreAuthTokenRequest(subject, ttl, remark))

            assertThat(token).isEqualTo(
                PreAuthToken(tokenId, subject, expiryTimestamp, PreAuthTokenStatus.AVAILABLE, remark, removalRemark)
            )
        }
    }

    @Nested
    inner class GetPreAuthTokenTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRestResource.getPreAuthTokens(HOLDING_IDENTITY_ID, null, null,  false)
            }
        }

        @Test
        fun `it fails when the owner x500Name is invalid`() {
            startService()
            assertThrows<InvalidInputDataException> {
                mgmRestResource.getPreAuthTokens(HOLDING_IDENTITY_ID, "Invalid X500Name", null, false)
            }
        }

        @Test
        fun `it fails when the uuid is invalid`() {
            startService()
            assertThrows<InvalidInputDataException> {
                mgmRestResource.getPreAuthTokens(HOLDING_IDENTITY_ID, subject, "invalidUUID", false)
            }
        }

        @Test
        fun `it fails when the holding identity does not exist`() {
            startService()
            whenever(
                mgmResourceClient.getPreAuthTokens(any(), anyOrNull(), anyOrNull(), any())
            ).doThrow(CouldNotFindMemberException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.getPreAuthTokens(HOLDING_IDENTITY_ID, null, null, false)
            }
        }

        @Test
        fun `it fails when the holding identity is not an MGM`() {
            startService()
            whenever(
                mgmResourceClient.getPreAuthTokens(any(), anyOrNull(), anyOrNull(), any())
            ).doThrow(MemberNotAnMgmException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<InvalidInputDataException> {
                mgmRestResource.getPreAuthTokens(HOLDING_IDENTITY_ID, null, null, false)
            }
        }

        @Test
        fun `it returns the correct result from the client`() {
            startService()
            val tokenId = UUID.randomUUID()
            val ttl = Instant.ofEpochSecond(10)
            val remark = "Remark"
            val removalRemark = "RemovalRemark"
            whenever(
                mgmResourceClient.getPreAuthTokens(ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject), tokenId, true)
            ).doReturn(listOf(AvroPreAuthToken(tokenId.toString(), subject, ttl, AvroPreAuthTokenStatus.AVAILABLE, remark, removalRemark)))

            val token = mgmRestResource.getPreAuthTokens(HOLDING_IDENTITY_ID, subject, tokenId.toString(), true)

            assertThat(token).isEqualTo(
                listOf(PreAuthToken(tokenId.toString(), subject, ttl, PreAuthTokenStatus.AVAILABLE, remark, removalRemark))
            )
        }
    }

    @Nested
    inner class RevokePreAuthTokenTest {

        private val tokenId: UUID = UUID.randomUUID()

        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRestResource.revokePreAuthToken(HOLDING_IDENTITY_ID, "", null)
            }
        }

        @Test
        fun `it fails when the id is invalid`() {
            startService()
            assertThrows<InvalidInputDataException> {
                mgmRestResource.revokePreAuthToken(HOLDING_IDENTITY_ID, "invalidUUID", null)
            }
        }

        @Test
        fun `it fails when the holding identity does not exist`() {
            startService()
            whenever(
                mgmResourceClient.revokePreAuthToken(any(), any(), anyOrNull())
            ).doThrow(CouldNotFindMemberException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.revokePreAuthToken(HOLDING_IDENTITY_ID, tokenId.toString())
            }
        }

        @Test
        fun `it fails when the holding identity is not an MGM`() {
            startService()
            whenever(
                mgmResourceClient.revokePreAuthToken(any(), any(), anyOrNull())
            ).doThrow(MemberNotAnMgmException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<InvalidInputDataException> {
                mgmRestResource.revokePreAuthToken(HOLDING_IDENTITY_ID, tokenId.toString())
            }
        }

        @Test
        fun `it returns the correct result from the client`() {
            startService()
            val ttl = Instant.ofEpochSecond(10)
            val remark = "Remark"
            val removalRemark = "RemovalRemark"
            whenever(
                mgmResourceClient.revokePreAuthToken(ShortHash.Companion.of(HOLDING_IDENTITY_ID), tokenId, removalRemark)
            ).doReturn(AvroPreAuthToken(tokenId.toString(), subject, ttl, AvroPreAuthTokenStatus.AVAILABLE, remark, removalRemark))

            val token = mgmRestResource.revokePreAuthToken(HOLDING_IDENTITY_ID, tokenId.toString(), removalRemark)

            assertThat(token).isEqualTo(PreAuthToken(tokenId.toString(), subject, ttl, PreAuthTokenStatus.AVAILABLE, remark, removalRemark))
        }
    }

    @Nested
    inner class SuspendMemberTests {
        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `suspendMember delegates correctly to mgm resource client`() {
            mgmRestResource.suspendMember(HOLDING_IDENTITY_ID, suspensionActivationParameters)

            verify(mgmResourceClient).suspendMember(
                (ShortHash.of(HOLDING_IDENTITY_ID)),
                MemberX500Name.parse(subject),
                SERIAL,
                REASON
            )
        }

        @Test
        fun `suspendMember throws resource not found for invalid member`() {
            whenever(mgmResourceClient.suspendMember(
                ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject), SERIAL, REASON
            )).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.suspendMember(HOLDING_IDENTITY_ID, suspensionActivationParameters)
            }
        }

        @Test
        fun `suspendMember throws invalid input for non MGM member`() {
            whenever(mgmResourceClient.suspendMember(
                ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject), SERIAL, REASON
            )).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.suspendMember(HOLDING_IDENTITY_ID, suspensionActivationParameters)
            }
        }

        @Test
        fun `suspendMember throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                mgmRestResource.suspendMember(INVALID_SHORT_HASH, suspensionActivationParameters)
            }
        }

        @Test
        fun `suspendMember throws bad request if MGM tries to suspend itself`() {
            val mgm = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=MGM"), "group")
            whenever(mgmResourceClient.suspendMember(
                mgm.shortHash, mgm.x500Name, SERIAL, REASON
            )).doThrow(mock<IllegalArgumentException>())

            assertThrows<BadRequestException> {
                mgmRestResource.suspendMember(
                    mgm.shortHash.value,
                    SuspensionActivationParameters(mgm.x500Name.toString(), SERIAL, REASON)
                )
            }
        }

        @Test
        fun `suspendMember throws bad request if member is not currently active`() {
            val mgm = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=SuspendedMember"), "group")
            whenever(mgmResourceClient.suspendMember(
                mgm.shortHash, mgm.x500Name, SERIAL, REASON
            )).doThrow(mock<IllegalArgumentException>())

            assertThrows<BadRequestException> {
                mgmRestResource.suspendMember(
                    mgm.shortHash.value,
                    SuspensionActivationParameters(mgm.x500Name.toString(), SERIAL, REASON)
                )
            }
        }

        @Test
        fun `suspendMember throws resource not found if member to be suspended is not found`() {
            val missingMember = MemberX500Name.parse("C=GB,L=London,O=MissingMember")
            whenever(mgmResourceClient.suspendMember(
                ShortHash.of(HOLDING_IDENTITY_ID), missingMember
            )).doThrow(mock<NoSuchElementException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.suspendMember(HOLDING_IDENTITY_ID, SuspensionActivationParameters(missingMember.toString()))
            }
        }

        @Test
        fun `suspendMember throws invalid state change in case of concurrent update attempt`() {
            whenever(mgmResourceClient.suspendMember(
                ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject)
            )).doThrow(mock<PersistenceException>())

            assertThrows<InvalidStateChangeException> {
                mgmRestResource.suspendMember(HOLDING_IDENTITY_ID, SuspensionActivationParameters(subject))
            }
        }
    }

    @Nested
    inner class ActivateMemberTests {
        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `activateMember delegates correctly to mgm resource client`() {
            mgmRestResource.activateMember(HOLDING_IDENTITY_ID, suspensionActivationParameters)

            verify(mgmResourceClient).activateMember(
                (ShortHash.of(HOLDING_IDENTITY_ID)),
                MemberX500Name.parse(subject),
                SERIAL,
                REASON
            )
        }

        @Test
        fun `activateMember throws resource not found for invalid member`() {
            whenever(mgmResourceClient.activateMember(
                ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject), SERIAL, REASON
            )).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.activateMember(HOLDING_IDENTITY_ID, suspensionActivationParameters)
            }
        }

        @Test
        fun `activateMember throws invalid input for non MGM member`() {
            whenever(mgmResourceClient.activateMember(
                ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject), SERIAL, REASON
            )).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRestResource.activateMember(HOLDING_IDENTITY_ID, suspensionActivationParameters)
            }
        }

        @Test
        fun `activateMember throws bad request if short hash is invalid`() {
            assertThrows<BadRequestException> {
                mgmRestResource.activateMember(INVALID_SHORT_HASH, suspensionActivationParameters)
            }
        }

        @Test
        fun `activateMember throws bad request if MGM tries to activate itself`() {
            val mgm = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=MGM"), "group")
            whenever(mgmResourceClient.activateMember(
                mgm.shortHash, mgm.x500Name, SERIAL, REASON
            )).doThrow(mock<IllegalArgumentException>())

            assertThrows<BadRequestException> {
                mgmRestResource.activateMember(
                    mgm.shortHash.value,
                    SuspensionActivationParameters(mgm.x500Name.toString(), SERIAL, REASON)
                )
            }
        }

        @Test
        fun `activateMember throws bad request if member is not currently suspended`() {
            val mgm = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=ActiveMember"), "group")
            whenever(mgmResourceClient.activateMember(
                mgm.shortHash, mgm.x500Name, SERIAL, REASON
            )).doThrow(mock<IllegalArgumentException>())

            assertThrows<BadRequestException> {
                mgmRestResource.activateMember(
                    mgm.shortHash.value,
                    SuspensionActivationParameters(mgm.x500Name.toString(), SERIAL, REASON)
                )
            }
        }

        @Test
        fun `activateMember throws resource not found if member to be activated is not found`() {
            val missingMember = MemberX500Name.parse("C=GB,L=London,O=MissingMember")
            whenever(mgmResourceClient.activateMember(
                ShortHash.of(HOLDING_IDENTITY_ID), missingMember
            )).doThrow(mock<NoSuchElementException>())

            assertThrows<ResourceNotFoundException> {
                mgmRestResource.activateMember(HOLDING_IDENTITY_ID, SuspensionActivationParameters(missingMember.toString()))
            }
        }

        @Test
        fun `activateMember throws invalid state change in case of concurrent update attempt`() {
            whenever(mgmResourceClient.activateMember(
                ShortHash.of(HOLDING_IDENTITY_ID), MemberX500Name.parse(subject)
            )).doThrow(mock<PersistenceException>())

            assertThrows<InvalidStateChangeException> {
                mgmRestResource.activateMember(HOLDING_IDENTITY_ID, SuspensionActivationParameters(subject))
            }
        }
    }

}
