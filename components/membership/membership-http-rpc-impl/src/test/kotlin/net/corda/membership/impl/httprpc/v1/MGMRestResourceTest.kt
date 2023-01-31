package net.corda.membership.impl.httprpc.v1

import net.corda.configuration.read.ConfigurationGetService
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.ApprovalRuleType.PREAUTH
import net.corda.data.membership.common.ApprovalRuleType.STANDARD
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MGMOpsClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.httprpc.v1.types.request.ApprovalRuleRequestParams
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.schema.configuration.ConfigKeys.P2P_GATEWAY_CONFIG
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.ShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MGMRestResourceTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "111213141500"
        private const val INVALID_SHORT_HASH = "ABS09234745D"
        private const val RULE_REGEX = "rule-regex"
        private const val INVALID_RULE_REGEX = "*"
        private const val RULE_LABEL = "rule-label"
        private const val RULE_ID = "rule-id"

        fun String.shortHash() = ShortHash.of(this)
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

    private val mgmOpsClient: MGMOpsClient = mock {
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

    private val mgmRpcOps = MGMRestResourceImpl(
        lifecycleCoordinatorFactory,
        mgmOpsClient,
        configurationGetService,
    )

    private fun startService() {
        mgmRpcOps.start()
        mgmRpcOps.activate("")
    }

    private fun stopService() {
        mgmRpcOps.deactivate("")
        mgmRpcOps.stop()
    }

    @Test
    fun `starting and stopping the service succeeds`() {
        mgmRpcOps.start()
        assertTrue(mgmRpcOps.isRunning)
        mgmRpcOps.stop()
        assertFalse(mgmRpcOps.isRunning)
    }

    @Test
    fun `operation fails when svc is not running`() {
        val ex = assertFailsWith<ServiceUnavailableException> {
            mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
        }
        assertEquals("MGMRestResourceImpl is not running. Operation cannot be fulfilled.", ex.message)
    }

    @Nested
    inner class GenerateGroupPolicyTests {
        @Test
        fun `generateGroupPolicy calls the client svc`() {
            startService()
            mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
            verify(mgmOpsClient).generateGroupPolicy(eq(HOLDING_IDENTITY_ID.shortHash()))
            stopService()
        }

        @Test
        fun `generateGroupPolicy throws resource not found for invalid member`() {
            startService()
            whenever(mgmOpsClient.generateGroupPolicy(any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `generateGroupPolicy throws invalid input for non MGM member`() {
            startService()
            whenever(mgmOpsClient.generateGroupPolicy(any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `generateGroupPolicy throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRpcOps.generateGroupPolicy("INVALID_SHORT_HASH")
            }
        }
    }

    @Nested
    inner class AddGroupApprovalRuleTests {
        @Test
        fun `addGroupApprovalRule delegates correctly to mgm ops client`() {
            startService()
            whenever(mgmOpsClient.addApprovalRule(any(), any())).doReturn(ApprovalRuleDetails(RULE_ID, RULE_REGEX, RULE_LABEL))

            mgmRpcOps.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))

            verify(mgmOpsClient).addApprovalRule(
                eq(HOLDING_IDENTITY_ID.shortHash()),
                eq(ApprovalRuleParams(RULE_REGEX, ApprovalRuleType.STANDARD, RULE_LABEL))
            )
            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws resource not found for invalid member`() {
            startService()
            whenever(mgmOpsClient.addApprovalRule(any(), any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws invalid input for non MGM member`() {
            startService()
            whenever(mgmOpsClient.addApprovalRule(any(), any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRpcOps.addGroupApprovalRule(INVALID_SHORT_HASH, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws bad request for duplicate rule`() {
            startService()
            whenever(mgmOpsClient.addApprovalRule(any(), any())).doThrow(mock<MembershipPersistenceException>())


            assertThrows<BadRequestException> {
                mgmRpcOps.addGroupApprovalRule(HOLDING_IDENTITY_ID, ApprovalRuleRequestParams(RULE_REGEX, RULE_LABEL))
            }

            stopService()
        }

        @Test
        fun `addGroupApprovalRule throws bad request for invalid regex syntax`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRpcOps.addGroupApprovalRule(
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

            mgmRpcOps.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)

            verify(mgmOpsClient).deleteApprovalRule(eq(HOLDING_IDENTITY_ID.shortHash()), eq(RULE_ID), eq(STANDARD))
            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws resource not found for invalid member`() {
            startService()
            whenever(mgmOpsClient.deleteApprovalRule(any(), any(), eq(STANDARD))).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)
            }

            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws resource not found for non-existent rule`() {
            startService()
            whenever(mgmOpsClient.deleteApprovalRule(any(), any(), eq(STANDARD))).doThrow(mock<MembershipPersistenceException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)
            }

            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws invalid input for non MGM member`() {
            startService()
            whenever(mgmOpsClient.deleteApprovalRule(any(), any(), eq(STANDARD))).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, RULE_ID)
            }

            stopService()
        }

        @Test
        fun `deleteGroupApprovalRule throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRpcOps.deleteGroupApprovalRule(INVALID_SHORT_HASH, RULE_ID)
            }

            stopService()
        }
    }

    @Nested
    inner class GetGroupApprovalRulesTests {
        @Test
        fun `getGroupApprovalRules delegates correctly to mgm ops client`() {
            startService()

            mgmRpcOps.getGroupApprovalRules(HOLDING_IDENTITY_ID)

            verify(mgmOpsClient).getApprovalRules(eq(HOLDING_IDENTITY_ID.shortHash()), eq(ApprovalRuleType.STANDARD))
            stopService()
        }

        @Test
        fun `getGroupApprovalRules throws resource not found for invalid member`() {
            startService()
            whenever(mgmOpsClient.getApprovalRules(any(), any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.getGroupApprovalRules(HOLDING_IDENTITY_ID)
            }

            stopService()
        }

        @Test
        fun `getGroupApprovalRules throws invalid input for non MGM member`() {
            startService()
            whenever(mgmOpsClient.getApprovalRules(any(), any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.getGroupApprovalRules(HOLDING_IDENTITY_ID)
            }

            stopService()
        }

        @Test
        fun `getGroupApprovalRules throws bad request if short hash is invalid`() {
            startService()

            assertThrows<BadRequestException> {
                mgmRpcOps.getGroupApprovalRules(INVALID_SHORT_HASH)
            }

            stopService()
        }
    }

    @Nested
    inner class MutualTlsAllowClientCertificateTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when mutual tls is not enabled`() {
            startService()
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when the subject is not a valid X500 name`() {
            startService()

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, "Invalid")
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            startService()
            whenever(
                mgmOpsClient.mutualTlsAllowClientCertificate(
                    any(),
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(HOLDING_IDENTITY_ID.shortHash()))

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it sends the request to the client`() {
            startService()

            mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)

            verify(mgmOpsClient).mutualTlsAllowClientCertificate(
                HOLDING_IDENTITY_ID.shortHash(),
                MemberX500Name.Companion.parse(subject),
            )
        }
    }

    @Nested
    inner class MutualTlsDisallowClientCertificateTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when mutual tls is not enabled`() {
            startService()
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when the subject is not a valid X500 name`() {
            startService()

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, "Invalid")
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            startService()
            whenever(
                mgmOpsClient.mutualTlsDisallowClientCertificate(
                    any(),
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(HOLDING_IDENTITY_ID.shortHash()))

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it sends the request to the client`() {
            startService()

            mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)

            verify(mgmOpsClient).mutualTlsDisallowClientCertificate(
                HOLDING_IDENTITY_ID.shortHash(),
                MemberX500Name.Companion.parse(subject),
            )
        }
    }


    @Nested
    inner class MutualTlsListClientCertificateTest {
        @Test
        fun `it fails when not ready`() {
            assertThrows<ServiceUnavailableException> {
                mgmRpcOps.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it fails when mutual tls is not enabled`() {
            startService()
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRpcOps.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            startService()
            whenever(
                mgmOpsClient.mutualTlsListClientCertificate(
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(HOLDING_IDENTITY_ID.shortHash()))

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it returns the list from the client`() {
            startService()
            val parsedSubject = MemberX500Name.parse(subject)
            whenever(
                mgmOpsClient.mutualTlsListClientCertificate(
                    HOLDING_IDENTITY_ID.shortHash()
                )
            ).doReturn(listOf(parsedSubject))

            val list = mgmRpcOps.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)

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
        ) = mgmRpcOps.addPreAuthGroupApprovalRule(
            holdingIdentity,
            requestParams
        )

        private fun onCallingClientService() = whenever(
            mgmOpsClient.addApprovalRule(any(), argThat { ruleType == PREAUTH })
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

            verify(mgmOpsClient).addApprovalRule(
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
            mgmOpsClient.getApprovalRules(any(), eq(PREAUTH))
        )

        private fun callFunctionUnderTest(
            holdingIdentity: String = HOLDING_IDENTITY_ID
        ) = mgmRpcOps.getPreAuthGroupApprovalRules(holdingIdentity)

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `it delegates correctly to mgm ops client`() {
            callFunctionUnderTest()

            verify(mgmOpsClient).getApprovalRules(
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
        ) = mgmRpcOps.deletePreAuthGroupApprovalRule(
            holdingIdentity,
            ruleId
        )

        private fun whenCallingClientService() = whenever(
            mgmOpsClient.deleteApprovalRule(any(), any(), eq(PREAUTH))
        )

        @BeforeEach
        fun setUp() = startService()

        @AfterEach
        fun tearDown() = stopService()

        @Test
        fun `deleteGroupApprovalRule delegates correctly to mgm ops client`() {
            callFunctionUnderTest(HOLDING_IDENTITY_ID, RULE_ID)

            verify(mgmOpsClient).deleteApprovalRule(
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
}
