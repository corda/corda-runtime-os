package net.corda.membership.group.policy.validation.impl

import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.group.policy.validation.MembershipInvalidGroupPolicyException
import net.corda.membership.group.policy.validation.MembershipInvalidTlsTypeException
import net.corda.membership.lib.exceptions.BadGroupPolicyException
import net.corda.membership.lib.grouppolicy.GroupPolicy.P2PParameters
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import net.corda.membership.lib.grouppolicy.MemberGroupPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class MembershipGroupPolicyValidatorImplTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val groupPolicyParser = mock<GroupPolicyParser>()
    private val configurationGetService = mock<ConfigurationGetService>()

    private val impl = MembershipGroupPolicyValidatorImpl(
        lifecycleCoordinatorFactory,
        groupPolicyParser,
        configurationGetService,
    )

    @Nested
    inner class LifecycleTest {
        @Test
        fun `start calls coordinator start`() {
            impl.start()

            verify(coordinator).start()
        }

        @Test
        fun `stop calls coordinator stop`() {
            impl.stop()

            verify(coordinator).stop()
        }

        @Test
        fun `isRunning calls coordinator isRunning`() {
            whenever(coordinator.status).doReturn(LifecycleStatus.UP)

            assertThat(impl.isRunning).isTrue
        }

        @Test
        fun `start event follow changes`() {
            val creator = argumentCaptor<() -> Resource>()
            whenever(coordinator.createManagedResource(any(), creator.capture())).doAnswer { }

            handler.firstValue.processEvent(StartEvent(), coordinator)
            creator.firstValue.invoke()

            verify(coordinator)
                .followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
        }

        @Test
        fun `stop event close the following`() {
            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(coordinator)
                .closeManagedResources(any())
        }

        @Test
        fun `stop event update the status`() {
            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(coordinator)
                .updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `registration changed event update the status`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP,
                ),
                coordinator
            )

            verify(coordinator)
                .updateStatus(LifecycleStatus.UP)
        }
    }

    @Nested
    inner class ValidateGroupPolicyTest {
        private val sslConfiguration = mock<SmartConfig> {
            on { getString("tlsType") } doReturn "ONE_WAY"
        }
        private val gatewayConfiguration = mock<SmartConfig> {
            on { getConfig("sslConfig") } doReturn sslConfiguration
        }

        @Test
        fun `parsing error will fail the validation`() {
            whenever(groupPolicyParser.parseMember(any())).doThrow(BadGroupPolicyException("Ooops"))

            assertThrows<MembershipInvalidGroupPolicyException> {
                impl.validateGroupPolicy("policy")
            }
        }

        @Test
        fun `Mgm group policy will not check the configuration`() {
            whenever(groupPolicyParser.parseMember(any())).doReturn(null)

            impl.validateGroupPolicy("policy")

            verifyNoInteractions(configurationGetService)
        }

        @Test
        fun `valid TLS type will not throw an exception`() {
            val parameters = mock<P2PParameters> {
                on { tlsType } doReturn TlsType.ONE_WAY
            }
            val memberPolicy = mock<MemberGroupPolicy> {
                on { p2pParameters } doReturn parameters
            }
            whenever(configurationGetService.getSmartConfig(any())).doReturn(gatewayConfiguration)
            whenever(groupPolicyParser.parseMember(any())).doReturn(memberPolicy)

            impl.validateGroupPolicy("policy")
        }

        @Test
        fun `invalid TLS type will not throw an exception`() {
            val parameters = mock<P2PParameters> {
                on { tlsType } doReturn TlsType.MUTUAL
            }
            val memberPolicy = mock<MemberGroupPolicy> {
                on { p2pParameters } doReturn parameters
            }
            whenever(configurationGetService.getSmartConfig(any())).doReturn(gatewayConfiguration)
            whenever(groupPolicyParser.parseMember(any())).doReturn(memberPolicy)

            assertThrows<MembershipInvalidTlsTypeException> {
                impl.validateGroupPolicy("policy")
            }
        }
    }
}
