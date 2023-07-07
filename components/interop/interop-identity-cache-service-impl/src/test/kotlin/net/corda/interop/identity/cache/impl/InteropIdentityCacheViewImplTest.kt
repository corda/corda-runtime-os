package net.corda.interop.identity.cache.impl

import net.corda.interop.core.InteropIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class InteropIdentityCacheViewImplTest {
    companion object {
        private const val SHORT_HASH = "0123456789AB"
        private const val INTEROP_GROUP_ID = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
    }

    private val testView = InteropIdentityCacheViewImpl(SHORT_HASH)

    @Test
    fun `add interop identity by value`() {
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            holdingIdentityShortHash = "101010101010"
        )

        testView.putInteropIdentity(testInteropIdentity)

        val identicalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            holdingIdentityShortHash = "101010101010"
        )

        testView.putInteropIdentity(identicalInteropIdentity)

        assertThat(testView.getIdentities()).contains(testInteropIdentity)
        assertThat(testView.getIdentities()).hasSize(1)

        val nonIdenticalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Bob",
            groupId = INTEROP_GROUP_ID,
            holdingIdentityShortHash = "101010101010"
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
            holdingIdentityShortHash = "101010101010"
        )

        testView.putInteropIdentity(testInteropIdentity)

        assertThat(testView.getIdentities()).contains(testInteropIdentity)
        assertThat(testView.getIdentities()).hasSize(1)

        val identicalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            holdingIdentityShortHash = "101010101010"
        )

        testView.removeInteropIdentity(identicalInteropIdentity)

        assertThat(testView.getIdentities()).isEmpty()
    }
}
