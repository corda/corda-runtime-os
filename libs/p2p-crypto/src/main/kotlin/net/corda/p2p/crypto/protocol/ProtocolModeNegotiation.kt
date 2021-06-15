package net.corda.p2p.crypto.protocol

import net.corda.p2p.crypto.ProtocolMode
import net.corda.v5.base.exceptions.CordaRuntimeException

class ProtocolModeNegotiation {

    companion object {
        fun selectMode(initiatorModes: Set<ProtocolMode>, responderModes: Set<ProtocolMode>): ProtocolMode {
            val commonModes = initiatorModes.intersect(responderModes)
            return if (commonModes.isEmpty()) {
                throw NoCommonModeError(initiatorModes, responderModes)
            } else {
                commonModes.maxByOrNull { getPreference(it) }!!
            }
        }

        /**
         * Defines preference order for different protocol modes, with a higher number indicating higher preference.
         * Protocol modes that are considered more secure are preferred to less secure modes, when both are supported.
         */
        private fun getPreference(mode: ProtocolMode): Int {
            return when(mode) {
                ProtocolMode.AUTHENTICATION_ONLY -> 1
                ProtocolMode.AUTHENTICATED_ENCRYPTION -> 2
            }
        }
    }

}

class NoCommonModeError(initiatorModes: Set<ProtocolMode>, responderModes: Set<ProtocolMode>):
    CordaRuntimeException("There was no common mode between those supported by the initiator ($initiatorModes) " +
            "and those supported by the responder ($responderModes).")