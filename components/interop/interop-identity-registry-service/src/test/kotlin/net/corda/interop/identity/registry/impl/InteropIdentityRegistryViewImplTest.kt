package net.corda.interop.identity.registry.impl

import java.util.*
import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.interop.identity.registry.InteropIdentityRegistryStateError
import net.corda.v5.application.interop.facade.FacadeId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


class InteropIdentityRegistryViewImplTest {
    companion object {
        private val VIEW_OWNER_SHORT_HASH = ShortHash.parse("0123456789AB")
        private val INTEROP_GROUP_ID = UUID.fromString("3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08")
    }

    private val testView = InteropIdentityRegistryViewImpl(VIEW_OWNER_SHORT_HASH)

    @Test
    fun `add interop identity by value`() {
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(testInteropIdentity)

        val identicalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(identicalInteropIdentity)

        assertThat(testView.getIdentities()).contains(testInteropIdentity)
        assertThat(testView.getIdentities()).hasSize(1)

        val nonIdenticalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://bob.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(nonIdenticalInteropIdentity)

        assertThat(testView.getIdentities()).contains(testInteropIdentity, nonIdenticalInteropIdentity)
        assertThat(testView.getIdentities()).hasSize(2)
    }

    @Test
    fun `remove interop identity by value`() {
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(testInteropIdentity)

        assertThat(testView.getIdentities()).contains(testInteropIdentity)
        assertThat(testView.getIdentities()).hasSize(1)

        val identicalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.removeInteropIdentity(identicalInteropIdentity)

        assertThat(testView.getIdentities()).isEmpty()
    }

    @Test
    fun `get interop identities by short hash`() {
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(testInteropIdentity)

        var byShortHash = testView.getIdentityWithShortHash(testInteropIdentity.shortHash)

        val identity = checkNotNull(byShortHash)

        assertThat(identity.shortHash).isEqualTo(testInteropIdentity.shortHash)
        assertThat(identity).isEqualTo(testInteropIdentity)

        testView.removeInteropIdentity(testInteropIdentity)

        byShortHash = testView.getIdentityWithShortHash(testInteropIdentity.shortHash)

        assertThat(byShortHash).isNull()
    }

    @Test
    fun `get owned identities`() {
        val ownedIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = VIEW_OWNER_SHORT_HASH,
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        val notOwnedIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://bob.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(ownedIdentity)
        testView.putInteropIdentity(notOwnedIdentity)

        var ownedIdentities = testView.getOwnedIdentities(INTEROP_GROUP_ID)

        assertThat(ownedIdentities).hasSize(1)
        assertThat(ownedIdentities).contains(ownedIdentity)
        assertThat(ownedIdentities).doesNotContain(notOwnedIdentity)

        testView.removeInteropIdentity(ownedIdentity)

        ownedIdentities = testView.getOwnedIdentities(INTEROP_GROUP_ID)

        assertThat(ownedIdentities).hasSize(0)
    }

    @Test
    fun `get interop identities by facadeId`() {
        val facadeId1 = FacadeId.of("org.corda.interop/platform/tokens/v2.0")
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(facadeId1),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        val facadeId2 = FacadeId.of("org.corda.interop/platform/tokens/v3.0")
        val testInteropIdentity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(facadeId1, facadeId2),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(testInteropIdentity)
        testView.putInteropIdentity(testInteropIdentity2)

        val facade1Identities = testView.getIdentitiesByFacadeId(facadeId1)

        check(facade1Identities.isNotEmpty()) {
            "No Facade data found for given FacadeId"
        }

        assertThat(facade1Identities).hasSize(2)
        assertThat(facade1Identities).contains(testInteropIdentity)
        assertThat(facade1Identities).contains(testInteropIdentity2)

        val facade2Identities = testView.getIdentitiesByFacadeId(facadeId2)

        check(facade2Identities.isNotEmpty()) {
            "No Facade data found for given FacadeId"
        }

        assertThat(facade2Identities).hasSize(1)
        assertThat(facade2Identities).doesNotContain(testInteropIdentity)
        assertThat(facade2Identities).contains(testInteropIdentity2)
    }

    @Test
    fun `get interop identities by Application Name`() {
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(testInteropIdentity)

        val identity = testView.getIdentitiesByApplicationName("Gold").single()

        assertThat(identity).isEqualTo(testInteropIdentity)
    }

    @Test
    fun `duplicate application names causes error on read`() {
        val applicationName = "Gold"

        val testInteropIdentity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = applicationName,
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        val testInteropIdentity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = applicationName,
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(testInteropIdentity1)
        testView.putInteropIdentity(testInteropIdentity2)

        val identities = testView.getIdentitiesByApplicationName(applicationName)

        assertThat(identities).hasSize(2)
        assertThat(identities).containsAll(listOf(testInteropIdentity1, testInteropIdentity2))

        assertThrows<InteropIdentityRegistryStateError> {
            testView.getIdentityWithApplicationName(applicationName)
        }

        testView.removeInteropIdentity(testInteropIdentity2)

        val identity = testView.getIdentityWithApplicationName(applicationName)

        assertThat(identity).isNotNull
        assertThat(identity).isEqualTo(testInteropIdentity1)
    }

    @Test
    fun `multiple owned identities causes an error on read`() {
        val ownedIdentity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice1",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = VIEW_OWNER_SHORT_HASH,
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice1.corda5.r3.com:10000",
            enabled = true
        )

        val ownedIdentity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice2",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = VIEW_OWNER_SHORT_HASH,
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice2.corda5.r3.com:10000",
            enabled = true
        )

        testView.putInteropIdentity(ownedIdentity1)
        testView.putInteropIdentity(ownedIdentity2)

        val identities = testView.getOwnedIdentities(INTEROP_GROUP_ID)

        assertThat(identities).hasSize(2)
        assertThat(identities).containsAll(listOf(ownedIdentity1, ownedIdentity2))

        assertThrows<InteropIdentityRegistryStateError> {
            testView.getOwnedIdentity(INTEROP_GROUP_ID)
        }

        testView.removeInteropIdentity(ownedIdentity2)

        val identity = testView.getOwnedIdentity(INTEROP_GROUP_ID)

        assertThat(identity).isNotNull
        assertThat(identity).isEqualTo(ownedIdentity1)
    }
}
