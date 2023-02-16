package net.corda.p2p.linkmanager.sessions

import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import org.slf4j.Logger

internal object SessionManagerWarnings {
    internal fun Logger.noSessionWarning(messageName: String, sessionId: String) {
        this.warn(
            "Received $messageName with sessionId $sessionId but there is no pending session with this id." +
                " The message was discarded."
        )
    }

    internal fun Logger.alreadySessionWarning(messageName: String, sessionId: String) {
        this.warn(
            "Received $messageName with sessionId $sessionId but there is already an established session with" +
                " this id. The message was discarded."
        )
    }

    internal fun Logger.ourIdNotInMembersMapWarning(messageName: String, sessionId: String, ourId: HoldingIdentity) {
        this.warn(
            "Received $messageName with sessionId $sessionId but cannot find public key for our identity" +
                " $ourId. The message was discarded."
        )
    }

    internal fun Logger.ourHashNotInMembersMapWarning(messageName: String, sessionId: String, hash: String) {
        this.warn(
            "Received $messageName with sessionId $sessionId. The received public key hash ($hash) corresponding" +
                " to one of our holding identities is not in the members map. The message was discarded."
        )
    }

    internal fun Logger.peerHashNotInMembersMapWarning(messageName: String, sessionId: String, hash: String) {
        this.warn(
            "Received $messageName with sessionId $sessionId. The received public key hash ($hash) corresponding" +
                " to one of the sender's holding identities is not in the members map. The message was discarded."
        )
    }

    internal fun Logger.peerNotInTheMembersMapWarning(
        messageName: String,
        sessionId: String,
        responderId: HoldingIdentity
    ) {
        this.warn(
            "Received $messageName with sessionId $sessionId from peer $responderId which is not in the members map." +
                " The message was discarded."
        )
    }

    internal fun Logger.couldNotFindGroupInfo(messageName: String, sessionId: String, holdingIdentity: HoldingIdentity) {
        this.warn(
            "Could not find the group information in the GroupPolicyProvider for identity $holdingIdentity." +
                " The $messageName for sessionId $sessionId was discarded."
        )
    }

    internal fun Logger.validationFailedWarning(messageName: String, sessionId: String, error: String?) {
        this.warn("Received $messageName with sessionId $sessionId, which failed validation with: $error The message was discarded.")
    }

    internal fun Logger.couldNotFindSessionInformation(us: ShortHash, peer: ShortHash, messageId: String) {
        this.warn("Could not get session information from message sent from $us" +
                " to $peer with ID `$messageId`. Peer is not in the members map.")
    }
}
