package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.linkmanager.LinkManagerNetworkMap.HoldingIdentity
import org.slf4j.Logger

class SessionManagerWarnings {
    companion object {
        internal fun Logger.noSessionWarning(messageName: String, sessionId: String) {
            this.warn("Received $messageName with sessionId $sessionId but there is no pending session with this id." +
                " The message was discarded.")
        }

        internal fun Logger.ourIdNotInNetworkMapWarning(messageName: String, sessionId: String, ourId: HoldingIdentity) {
            this.warn("Received $messageName with sessionId $sessionId but cannot find public key for our identity" +
                " $ourId. The message was discarded.")
        }

        internal fun Logger.ourHashNotInNetworkMapWarning(messageName: String, sessionId: String, hash: String) {
            this.warn("Received $messageName with sessionId ${sessionId}. The received public key hash ($hash) corresponding" +
                    " to one of our holding identities is not in the network map. The message was discarded.")
        }

        internal fun Logger.peerHashNotInNetworkMapWarning(messageName: String, sessionId: String, hash: String) {
            this.warn("Received $messageName with sessionId ${sessionId}. The received public key hash ($hash) corresponding" +
                    " to one of the sender's holding identities is not in the network map. The message was discarded.")
        }

        internal fun Logger.peerNotInTheNetworkMapWarning(messageName: String, sessionId: String, responderId: HoldingIdentity) {
            this.warn("Received $messageName with sessionId $sessionId from peer $responderId which is not in the network map." +
                " The message was discarded.")
        }

        internal fun Logger.noTenantId(messageName: String, sessionId: String, holdingIdentity: HoldingIdentity) {
            this.warn(
                "Received $messageName with sessionId $sessionId but $holdingIdentity has no tenant ID." +
                        " The message was discarded."
            )
        }

        internal fun Logger.couldNotFindNetworkType(messageName: String, sessionId: String, groupId: String) {
            this.warn("Could not find the network type in the NetworkMap for groupId $groupId." +
                    " The $messageName for sessionId $sessionId was discarded.")
        }

        internal fun Logger.validationFailedWarning(messageName: String, sessionId: String, error: String?) {
            this.warn("Received $messageName with sessionId $sessionId, which failed validation with: $error The message was discarded.")
        }

    }
}