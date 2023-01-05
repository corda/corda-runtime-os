package net.corda.membership.lib.group.policy.validation.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.membership.lib.group.policy.validation.MembershipInvalidGroupPolicyException
import net.corda.membership.lib.group.policy.validation.MembershipInvalidTlsTypeException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MembershipGroupPolicyValidatorImplTest {
    private val groupPolicy = "{\"p2pParameters\": {\"tlsType\": \"OneWay\"}}"
    private val sslConfiguration = mock<SmartConfig> {
        on { getString("tlsType") } doReturn "ONE_WAY"
    }
    private val gatewayConfiguration = mock<SmartConfig> {
        on { getConfig("sslConfig") } doReturn sslConfiguration
    }
    private val impl = MembershipGroupPolicyValidatorImpl()


    @Test
    fun `validateGroupPolicy will pass if the TLS type of the group policy is correct`() {
        impl.validateGroupPolicy(
            groupPolicy
        ) { gatewayConfiguration }
    }

    @Test
    fun `validateGroupPolicy will throw an exception if the TLS type of the group policy is wrong`() {
        whenever(sslConfiguration.getString("tlsType")).doReturn("MUTUAL")

        assertThrows<MembershipInvalidTlsTypeException> {
            impl.validateGroupPolicy(
                groupPolicy
            ) { gatewayConfiguration }
        }
    }

    @Test
    fun `validateGroupPolicy will throw an exception if the group policy JSON is invalid`() {
        assertThrows<MembershipInvalidGroupPolicyException> {
            impl.validateGroupPolicy(
                "{hello}"
            ) { gatewayConfiguration }
        }
    }

    @Test
    fun `validateGroupPolicy will pass if there is no P2P parameters`() {
        whenever(sslConfiguration.getString("tlsType")).doReturn("MUTUAL")

        impl.validateGroupPolicy(
            "{}",
        ) { gatewayConfiguration }
    }

    @Test
    fun `validateGroupPolicy will pass if there is the TLS type is missing`() {
        whenever(sslConfiguration.getString("tlsType")).doReturn("MUTUAL")

        impl.validateGroupPolicy(
            "{\"p2pParameters\": {}}",
        ) { gatewayConfiguration }
    }
}