package net.corda.interop.identity.registry.impl

import net.corda.crypto.core.ShortHash
import net.corda.interop.core.InteropIdentity
import net.corda.v5.application.interop.facade.FacadeId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID


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
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        testView.putInteropIdentity(testInteropIdentity)

        val identicalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
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
            endpointProtocol = "https://bob.corda5.r3.com:10000"
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
            endpointProtocol = "https://alice.corda5.r3.com:10000"
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
            endpointProtocol = "https://alice.corda5.r3.com:10000"
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
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        testView.putInteropIdentity(testInteropIdentity)

        var byShortHash = testView.getIdentitiesByShortHash()

        assertThat(byShortHash).hasSize(1)

        val singleEntry = byShortHash.entries.single()
        val identity = singleEntry.value

        assertThat(singleEntry.key).isEqualTo(testInteropIdentity.shortHash)
        assertThat(identity).isEqualTo(testInteropIdentity)

        testView.removeInteropIdentity(testInteropIdentity)

        byShortHash = testView.getIdentitiesByShortHash()

        assertThat(byShortHash).hasSize(0)
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
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        val notOwnedIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://bob.corda5.r3.com:10000"
        )

        testView.putInteropIdentity(ownedIdentity)
        testView.putInteropIdentity(notOwnedIdentity)

        var ownedIdentities = testView.getOwnedIdentities()

        assertThat(ownedIdentities).hasSize(1)
        assertThat(ownedIdentities.values).contains(ownedIdentity)
        assertThat(ownedIdentities.values).doesNotContain(notOwnedIdentity)

        testView.removeInteropIdentity(ownedIdentity)

        ownedIdentities = testView.getOwnedIdentities()

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
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        val facadeId2 = FacadeId.of("org.corda.interop/platform/tokens/v3.0")
        val testInteropIdentity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = ShortHash.parse("101010101010"),
            facadeIds = listOf(facadeId1, facadeId2),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        testView.putInteropIdentity(testInteropIdentity)
        testView.putInteropIdentity(testInteropIdentity2)

        val facadeToIds = testView.getIdentitiesByFacadeId()

        val facadeMap1 = checkNotNull(facadeToIds[facadeId1.toString()]) {
            "No Facade data found for given FacadeId"
        }

        assertThat(facadeMap1).hasSize(2)
        assertThat(facadeMap1).contains(testInteropIdentity)
        assertThat(facadeMap1).contains(testInteropIdentity2)

        val facadeMap2 = checkNotNull(facadeToIds[facadeId2.toString()]) {
            "No Facade data found for given FacadeId"
        }

        assertThat(facadeMap2).hasSize(1)
        assertThat(facadeMap2).doesNotContain(testInteropIdentity)
        assertThat(facadeMap2).contains(testInteropIdentity2)
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
            endpointProtocol = "https://alice.corda5.r3.com:10000"
        )

        testView.putInteropIdentity(testInteropIdentity)

        val identity = testView.getIdentitiesByApplicationName()["Gold"]
        assertThat(identity).isEqualTo(testInteropIdentity)
    }

    @Test
    fun `multiple owned identities causes an error`() {
        val ownedIdentity1 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice1",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = VIEW_OWNER_SHORT_HASH,
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice1.corda5.r3.com:10000"
        )

        val ownedIdentity2 = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice2",
            groupId = INTEROP_GROUP_ID,
            owningVirtualNodeShortHash = VIEW_OWNER_SHORT_HASH,
            facadeIds = listOf(FacadeId.of("org.corda.interop/platform/tokens/v2.0")),
            applicationName = "Gold",
            endpointUrl = "1",
            endpointProtocol = "https://alice2.corda5.r3.com:10000"
        )

        testView.putInteropIdentity(ownedIdentity1)

        assertThrows<IllegalArgumentException> {
            testView.putInteropIdentity(ownedIdentity2)
        }
    }
}
