package net.corda.interop.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


/**
 * The [InteropIdentity] class must always have a valid equals method.
 * These tests guard against accidental breakage of this behaviour.
 */
class InteropIdentityTest {
    companion object {
        private const val GROUP_ID_1 = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
        private const val GROUP_ID_2 = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf09"
    }

    @Test
    fun `equality test`() {
        val identity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            holdingIdentityShortHash = "101010101010",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            holdingIdentityShortHash = "101010101010",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        assertThat(identity1).isEqualTo(identity2)
    }

    @Test
    fun `unequal x500 name`() {
        val identity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            holdingIdentityShortHash = "101010101010",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = GROUP_ID_1,
            holdingIdentityShortHash = "101010101010",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://bob.corda5.r3.com:10000"
        )

        assertThat(identity1).isNotEqualTo(identity2)
    }

    @Test
    fun `unequal group ID`() {
        val identity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            holdingIdentityShortHash = "101010101010",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_2,
            holdingIdentityShortHash = "101010101010",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        assertThat(identity1).isNotEqualTo(identity2)
    }

    @Test
    fun `unequal holding identity short hash`() {
        val identity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            holdingIdentityShortHash = "101010101010",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            holdingIdentityShortHash = "010101010101",
            facadeIds = listOf("org.corda.interop/platform/tokens/v2.0"),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        // Because each holding identity is expected to have only one identity in any given interop group
        // we don't expect changes to the holding identity to affect equality
        assertThat(identity1).isEqualTo(identity2)
    }
}