package net.corda.p2p.crypto.protocol

import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.NoCommonModeError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ProtocolModeNegotiationTest {

    @Test
    fun `when initiator and responder share a common supported mode, it is selected`() {
        var selectedMode = ProtocolModeNegotiation.selectMode(
            setOf(ProtocolMode.AUTHENTICATION_ONLY, ProtocolMode.AUTHENTICATED_ENCRYPTION),
            setOf(ProtocolMode.AUTHENTICATION_ONLY)
        )
        assertThat(selectedMode).isEqualTo(ProtocolMode.AUTHENTICATION_ONLY)

        selectedMode = ProtocolModeNegotiation.selectMode(
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            setOf(ProtocolMode.AUTHENTICATION_ONLY, ProtocolMode.AUTHENTICATED_ENCRYPTION)
        )
        assertThat(selectedMode).isEqualTo(ProtocolMode.AUTHENTICATION_ONLY)

        selectedMode = ProtocolModeNegotiation.selectMode(
            setOf(ProtocolMode.AUTHENTICATION_ONLY, ProtocolMode.AUTHENTICATED_ENCRYPTION),
            setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        )
        assertThat(selectedMode).isEqualTo(ProtocolMode.AUTHENTICATED_ENCRYPTION)

        selectedMode = ProtocolModeNegotiation.selectMode(
            setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
            setOf(ProtocolMode.AUTHENTICATION_ONLY, ProtocolMode.AUTHENTICATED_ENCRYPTION)
        )
        assertThat(selectedMode).isEqualTo(ProtocolMode.AUTHENTICATED_ENCRYPTION)
    }

    @Test
    fun `when there are multiple common modes, the most secure one is selected`() {
        val selectedMode = ProtocolModeNegotiation.selectMode(
            setOf(ProtocolMode.AUTHENTICATION_ONLY, ProtocolMode.AUTHENTICATED_ENCRYPTION),
            setOf(ProtocolMode.AUTHENTICATION_ONLY, ProtocolMode.AUTHENTICATED_ENCRYPTION),
        )
        assertThat(selectedMode).isEqualTo(ProtocolMode.AUTHENTICATED_ENCRYPTION)
    }

    @Test
    fun `when there is no common mode, an error is thrown`() {
        assertThatThrownBy {
            ProtocolModeNegotiation.selectMode(
                setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION),
                setOf(ProtocolMode.AUTHENTICATION_ONLY)
            )
        }.isInstanceOf(NoCommonModeError::class.java)
         .hasMessageContaining("There was no common mode between those supported by the initiator ([AUTHENTICATED_ENCRYPTION]) " +
                 "and those supported by the responder ([AUTHENTICATION_ONLY]).")

        assertThatThrownBy {
            ProtocolModeNegotiation.selectMode(
                setOf(ProtocolMode.AUTHENTICATION_ONLY),
                setOf(ProtocolMode.AUTHENTICATED_ENCRYPTION)
            )
        }.isInstanceOf(NoCommonModeError::class.java)
            .hasMessageContaining("There was no common mode between those supported by the initiator ([AUTHENTICATION_ONLY]) " +
                    "and those supported by the responder ([AUTHENTICATED_ENCRYPTION]).")
    }

}