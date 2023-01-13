package net.corda.membership.impl.httprpc.v1

import net.corda.configuration.read.ConfigurationGetService
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
import net.corda.schema.configuration.ConfigKeys.P2P_GATEWAY_CONFIG
import net.corda.v5.base.types.MemberX500Name
import net.corda.membership.client.dto.ApprovalRuleTypeDto
import net.corda.virtualnode.ShortHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MGMRpcOpsTest {
    companion object {
        private const val HOLDING_IDENTITY_ID = "111213141500"
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

    private val mgmRpcOps = MGMRpcOpsImpl(
        lifecycleCoordinatorFactory,
        mgmOpsClient,
        configurationGetService,
    )

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
        assertEquals("MGMRpcOpsImpl is not running. Operation cannot be fulfilled.", ex.message)
    }

    @Nested
    inner class GenerateGroupPolicyTests {
        @Test
        fun `generateGroupPolicy calls the client svc`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
            verify(mgmOpsClient).generateGroupPolicy(eq((ShortHash.of(HOLDING_IDENTITY_ID))))
            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `generateGroupPolicy throws resource not found for invalid member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.generateGroupPolicy(any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `generateGroupPolicy throws invalid input for non MGM member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.generateGroupPolicy(any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.generateGroupPolicy(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `generateGroupPolicy throws bad request if short hash is invalid`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            assertThrows<BadRequestException> {
                mgmRpcOps.generateGroupPolicy("ABS09234745D")
            }
        }
    }

    @Nested
    inner class AddGroupApprovalRuleTests {
        @Test
        fun `addGroupApprovalRule delegates correctly to mgm ops client`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            val rule = "rule"
            val label = "label"

            mgmRpcOps.addGroupApprovalRule(HOLDING_IDENTITY_ID, rule, label)

            verify(mgmOpsClient).addApprovalRule(
                eq((ShortHash.of(HOLDING_IDENTITY_ID))), eq(rule), eq(ApprovalRuleTypeDto.STANDARD), eq(label)
            )
            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `addGroupApprovalRule throws resource not found for invalid member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.addApprovalRule(any(), any(), any(), any()))
                .doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.addGroupApprovalRule(HOLDING_IDENTITY_ID, "rule", "label")
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `addGroupApprovalRule throws invalid input for non MGM member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.addApprovalRule(any(), any(), any(), any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.addGroupApprovalRule(HOLDING_IDENTITY_ID, "rule", "label")
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `addGroupApprovalRule throws bad request if short hash is invalid`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            assertThrows<BadRequestException> {
                mgmRpcOps.addGroupApprovalRule("ABS09234745D", "rule", "label")
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }
    }

    @Nested
    inner class DeleteGroupApprovalRuleTests {
        @Test
        fun `deleteGroupApprovalRule delegates correctly to mgm ops client`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            val ruleId = "rule-id"

            mgmRpcOps.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, ruleId)

            verify(mgmOpsClient).deleteApprovalRule(eq((ShortHash.of(HOLDING_IDENTITY_ID))), eq(ruleId))
            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `deleteGroupApprovalRule throws resource not found for invalid member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.deleteApprovalRule(any(), any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, "rule-id")
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `deleteGroupApprovalRule throws invalid input for non MGM member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.deleteApprovalRule(any(), any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.deleteGroupApprovalRule(HOLDING_IDENTITY_ID, "rule-id")
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `deleteGroupApprovalRule throws bad request if short hash is invalid`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            assertThrows<BadRequestException> {
                mgmRpcOps.deleteGroupApprovalRule("ABS09234745D", "rule-id")
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }
    }

    @Nested
    inner class GetGroupApprovalRulesTests {
        @Test
        fun `getGroupApprovalRules delegates correctly to mgm ops client`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            mgmRpcOps.getGroupApprovalRules(HOLDING_IDENTITY_ID)

            verify(mgmOpsClient).getApprovalRules(eq((ShortHash.of(HOLDING_IDENTITY_ID))), eq(ApprovalRuleTypeDto.STANDARD))
            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `getGroupApprovalRules throws resource not found for invalid member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.getApprovalRules(any(), any())).doThrow(mock<CouldNotFindMemberException>())

            assertThrows<ResourceNotFoundException> {
                mgmRpcOps.getGroupApprovalRules(HOLDING_IDENTITY_ID)
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `getGroupApprovalRules throws invalid input for non MGM member`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(mgmOpsClient.getApprovalRules(any(), any())).doThrow(mock<MemberNotAnMgmException>())

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.getGroupApprovalRules(HOLDING_IDENTITY_ID)
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
        }

        @Test
        fun `getGroupApprovalRules throws bad request if short hash is invalid`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            assertThrows<BadRequestException> {
                mgmRpcOps.getGroupApprovalRules("ABS09234745D")
            }

            mgmRpcOps.deactivate("")
            mgmRpcOps.stop()
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
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when the subject is not a valid X500 name`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, "Invalid")
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(
                mgmOpsClient.mutualTlsAllowClientCertificate(
                    any(),
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it sends the request to the client`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            mgmRpcOps.mutualTlsAllowClientCertificate(HOLDING_IDENTITY_ID, subject)

            verify(mgmOpsClient).mutualTlsAllowClientCertificate(
                ShortHash.of(HOLDING_IDENTITY_ID),
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
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it fails when the subject is not a valid X500 name`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, "Invalid")
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(
                mgmOpsClient.mutualTlsDisallowClientCertificate(
                    any(),
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)
            }
        }

        @Test
        fun `it sends the request to the client`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")

            mgmRpcOps.mutualTlsDisallowClientCertificate(HOLDING_IDENTITY_ID, subject)

            verify(mgmOpsClient).mutualTlsDisallowClientCertificate(
                ShortHash.of(HOLDING_IDENTITY_ID),
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
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(gatewayConfiguration.getString("tlsType")).doReturn("ONE_WAY")

            assertThrows<BadRequestException> {
                mgmRpcOps.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it fails when the member is not an MGM`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            whenever(
                mgmOpsClient.mutualTlsListClientCertificate(
                    any(),
                )
            ).doThrow(MemberNotAnMgmException(ShortHash.of(HOLDING_IDENTITY_ID)))

            assertThrows<InvalidInputDataException> {
                mgmRpcOps.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)
            }
        }

        @Test
        fun `it returns the list from the client`() {
            mgmRpcOps.start()
            mgmRpcOps.activate("")
            val parsedSubject = MemberX500Name.parse(subject)
            whenever(
                mgmOpsClient.mutualTlsListClientCertificate(
                    ShortHash.of(HOLDING_IDENTITY_ID)
                )
            ).doReturn(listOf(parsedSubject))

            val list = mgmRpcOps.mutualTlsListClientCertificate(HOLDING_IDENTITY_ID)

            assertThat(list)
                .containsExactly(parsedSubject.toString())
        }
    }
}
