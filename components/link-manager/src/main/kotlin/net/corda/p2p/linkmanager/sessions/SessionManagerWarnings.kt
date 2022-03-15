package net.corda.p2p.linkmanager.sessions

import net.corda.p2p.linkmanager.LinkManagerInternalTypes.HoldingIdentity
import org.slf4j.Logger

class SessionManagerWarnings {
    companion object {
        internal fun Logger.noSessionWarning(messageName: String, sessionId: String) {
            this.warn("Received $messageName with sessionId $sessionId but there is no pending session with this id." +
                " The message was discarded.")
        }

        internal fun Logger.ourIdNotInMembersMapWarning(messageName: String, sessionId: String, ourId: HoldingIdentity) {
            this.warn("Received $messageName with sessionId $sessionId but cannot find public key for our identity" +
                " $ourId. The message was discarded.")
        }

        internal fun Logger.ourHashNotInMembersMapWarning(messageName: String, sessionId: String, hash: String) {
            this.warn("Received $messageName with sessionId ${sessionId}. The received public key hash ($hash) corresponding" +
                    " to one of our holding identities is not in the members map. The message was discarded.")
        }

        internal fun Logger.peerHashNotInMembersMapWarning(messageName: String, sessionId: String, hash: String) {
            this.warn("Received $messageName with sessionId ${sessionId}. The received public key hash ($hash) corresponding" +
                    " to one of the sender's holding identities is not in the members map. The message was discarded.")
        }

        internal fun Logger.peerNotInTheMembersMapWarning(messageName: String, sessionId: String, responderId: HoldingIdentity) {
            this.warn("Received $messageName with sessionId $sessionId from peer $responderId which is not in the members map." +
                " The message was discarded.")
        }

        internal fun Logger.noTenantId(messageName: String, sessionId: String, holdingIdentity: HoldingIdentity) {
            this.warn(
                "Received $messageName with sessionId $sessionId but $holdingIdentity has no tenant ID." +
                        " The message was discarded."
            )
        }

        internal fun Logger.couldNotFindGroupInfo(messageName: String, sessionId: String, groupId: String) {
            this.warn("Could not find the group information in the GroupPolicyProvider for groupId $groupId." +
                    " The $messageName for sessionId $sessionId was discarded.")
        }

        internal fun Logger.validationFailedWarning(messageName: String, sessionId: String, error: String?) {
            this.warn("Received $messageName with sessionId $sessionId, which failed validation with: $error The message was discarded.")
        }

    }
}