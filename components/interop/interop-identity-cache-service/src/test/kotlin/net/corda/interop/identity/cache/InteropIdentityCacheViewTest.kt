package net.corda.interop.identity.cache

import net.corda.interop.core.InteropIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class InteropIdentityCacheViewTest {
    companion object {
        private const val SHORT_HASH = "0123456789AB"
        private const val INTEROP_GROUP_ID = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
    }

    private val testView = InteropIdentityCacheView(SHORT_HASH)

    @Test
    fun `add and remove interop identity`() {
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            holdingIdentityShortHash = "101010101010"
        )

        testView.addIdentity(testInteropIdentity)

        val identities = testView.getIdentities()

        assertThat(identities).contains(testInteropIdentity)
    }

    @Test
    fun `remove interop identity`() {
        val testInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            holdingIdentityShortHash = "101010101010"
        )

        testView.addIdentity(testInteropIdentity)

        val identicalInteropIdentity = InteropIdentity(
            x500Name = "C=GB, L=London, O=Alice",
            groupId = INTEROP_GROUP_ID,
            holdingIdentityShortHash = "101010101010"
        )

        testView.removeIdentity(identicalInteropIdentity)

        assertThat(testView.getIdentities()).isEmpty()
    }
}
