package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import org.slf4j.Logger

class SessionManagerWarnings {
    companion object {
        internal fun Logger.noSessionWarning(messageName: String, sessionId: String) {
            this.warn("Received $messageName with sessionId $sessionId but there is no pending session with this id." +
                " The message was discarded.")
        }

        internal fun Logger.groupIdNotInNetworkMapWarning(messageName: String, sessionId: String, groupId: String?) {
            this.warn("Received $messageName with sessionId $sessionId but cannot find public key for our group identity" +
                " $groupId. The message was discarded.")
        }

        internal fun Logger.hashNotInNetworkMapWarning(messageName: String, sessionId: String, hash: String) {
            this.warn("Received $messageName with sessionId ${sessionId}. The received public key hash ($hash) corresponding" +
                    " to one of our holding identities is not in the network map. The message was discarded.")
        }

        internal fun Logger.initiatorHashNotInNetworkMapWarning(messageName: String, sessionId: String, hash: String) {
            this.warn("Received $messageName with sessionId ${sessionId}. The received public key hash ($hash) corresponding" +
                    " to one of the senders holding identities is not in the network map. The message was discarded.")
        }

        internal fun Logger.peerNotInTheNetworkMapWarning(
            messageName: String,
            sessionId: String,
            responderId: LinkManagerNetworkMap.HoldingIdentity
        ) {
            this.warn("Received $messageName with sessionId $sessionId from peer $responderId which is not in the network map." +
                " The message was discarded.")
        }

        internal fun Logger.validationFailedWarning(messageName: String, sessionId: String, error: String?) {
            this.warn("Received $messageName with sessionId $sessionId, which failed validation with: $error The message was discarded.")
        }

    }
}