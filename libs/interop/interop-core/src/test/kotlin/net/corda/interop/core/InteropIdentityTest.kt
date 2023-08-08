package net.corda.interop.core

import net.corda.v5.application.interop.facade.FacadeId
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
            owningVirtualNodeShortHash = "101010101010",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            endpointUrl = "1"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            owningVirtualNodeShortHash = "101010101010",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            endpointUrl = "1"
        )

        assertThat(identity1).isEqualTo(identity2)
    }

    @Test
    fun `unequal x500 name`() {
        val identity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            owningVirtualNodeShortHash = "101010101010",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "https://alice.corda5.r3.com:10000",
            endpointProtocol = "1"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = GROUP_ID_1,
            owningVirtualNodeShortHash = "101010101010",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "https://bob.corda5.r3.com:10000",
            endpointProtocol = "1"
        )

        assertThat(identity1).isNotEqualTo(identity2)
    }

    @Test
    fun `unequal group ID`() {
        val identity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            owningVirtualNodeShortHash = "101010101010",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "https://alice.corda5.r3.com:10000",
            endpointProtocol = "1"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_2,
            owningVirtualNodeShortHash = "101010101010",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "https://alice.corda5.r3.com:10000",
            endpointProtocol = "1"
        )

        assertThat(identity1).isNotEqualTo(identity2)
    }

    @Test
    fun `unequal virtual node short hash`() {
        val identity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            owningVirtualNodeShortHash = "101010101010",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "https://alice.corda5.r3.com:10000",
            endpointProtocol = "1"
        )

        val identity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = GROUP_ID_1,
            owningVirtualNodeShortHash = "010101010101",
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "https://alice.corda5.r3.com:10000",
            endpointProtocol = "1"
        )

        // Because each virtual node is expected to have only one identity in any given interop group
        // we don't expect changes to the virtual node short hash to affect equality
        assertThat(identity1).isEqualTo(identity2)
    }
}
