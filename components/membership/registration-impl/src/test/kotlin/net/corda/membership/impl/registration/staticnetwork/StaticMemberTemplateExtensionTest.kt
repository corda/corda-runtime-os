package net.corda.membership.impl.registration.staticnetwork

import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.aliceName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.bobName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.charlieName
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetwork
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StaticMemberTemplateExtensionTest {
    private val memberNames = listOf(aliceName.toString(), bobName.toString(), charlieName.toString())

    @Test
    fun `static network parameters are not empty when group policy file with static network is used`() {
        val staticMembers = groupPolicyWithStaticNetwork.protocolParameters.staticNetworkMembers
        assertThat(staticMembers).isNotNull.hasSize(3)
    }

    @Test
    fun `static network parameters are empty when group policy file without static network is used`() {
        assertThat(groupPolicyWithoutStaticNetwork.protocolParameters.staticNetworkMembers).isNull()
    }

    @Test
    fun `retrieving member list`() {
        assertThat(
            groupPolicyWithStaticNetwork.protocolParameters.staticNetworkMembers?.map { it["name"] }
        ).containsExactlyElementsOf(memberNames)
    }
}