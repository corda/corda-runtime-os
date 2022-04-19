package net.corda.flow.utils

import net.corda.data.identity.HoldingIdentity
import net.corda.flow.BOB_X500_NAME
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CheckpointTest {

    @Test
    fun `getMemberX500Name returns x500name parsed from holding identity`() {
        val holdingIdentity = HoldingIdentity(BOB_X500_NAME.toString(), "group1")
        assertThat(holdingIdentity.getMemberX500Name()).isEqualTo(BOB_X500_NAME)
    }

    @Test
    fun `getMemberX500Name throws when the x500 name is invalid`() {
        val holdingIdentity = HoldingIdentity("x500", "group1")
        val exception = assertThrows<IllegalStateException> {
            holdingIdentity.getMemberX500Name()
        }
        assertThat(exception.message).isEqualTo("Failed to convert Holding Identity x500 name 'x500' to MemberX500Name")
    }
}