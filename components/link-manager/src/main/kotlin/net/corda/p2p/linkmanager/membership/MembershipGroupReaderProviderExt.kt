package net.corda.p2p.linkmanager.membership

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.bytes
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerWarnings.couldNotFindSessionInformation
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.Base64

private val logger = LoggerFactory.getLogger("net.corda.p2p.linkmanager.membership.MembershipGroupReaderProviderExt.kt")

fun MembershipGroupReaderProvider.lookup(
    requestingIdentity: HoldingIdentity,
    lookupIdentity: HoldingIdentity,
    filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
) = this.getGroupReader(requestingIdentity)
    .lookup(lookupIdentity.x500Name, filter)

fun MembershipGroupReaderProvider.lookupByKey(
    requestingIdentity: HoldingIdentity,
    keyIdBytes: ByteArray,
    filter: MembershipStatusFilter = MembershipStatusFilter.ACTIVE,
) = this.getGroupReader(requestingIdentity)
    .lookupBySessionKey(
        keyIdBytes.run {
            require(this.size == 32) {
                "Input must be 32 bytes long for SHA-256 hash."
            }
            SecureHashImpl(DigestAlgorithmName.SHA2_256.name, this)
        },
        filter
    )

fun MembershipGroupReaderProvider.calculateOutboundSessionKey(
    source: HoldingIdentity,
    destination: HoldingIdentity,
    status: MembershipStatusFilter,
    messageId: String,
): String? {
    val serial = this.lookup(source, destination, status)?.serial
    return if (serial == null) {
        logger.warn(
            "Cannot establish session for message $messageId: Failed to look up counterparty.",
        )
        null
    } else {
        calculateOutboundSessionKey(
            source,
            destination,
            serial,
        )
    }
}

fun calculateOutboundSessionKey(
    source: HoldingIdentity,
    destination: HoldingIdentity,
    serial: Long,
) = SessionCounterpartiesKey(source, destination, serial).hash

private data class SessionCounterpartiesKey(
    override val ourId: HoldingIdentity,
    override val counterpartyId: HoldingIdentity,
    val serial: Long,
) : SessionManager.BaseCounterparties {
    val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val s = (ourId.x500Name.toString() + counterpartyId.x500Name.toString() + serial.toString())
        val digest: MessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        val hash: ByteArray = digest.digest(s.toByteArray())
        String(Base64.getEncoder().encode(SecureHashImpl(DigestAlgorithmName.SHA2_256.name, hash).bytes))
    }
}

internal fun MembershipGroupReaderProvider.getSessionCounterpartiesFromMessage(
    message: AuthenticatedMessage
): SessionManager.SessionCounterparties? {
    val peer = message.header.destination
    val us = message.header.source
    val status = message.header.statusFilter
    val ourInfo = this.lookup(
        us.toCorda(), us.toCorda(), MembershipStatusFilter.ACTIVE_OR_SUSPENDED
    )
    // could happen when member has pending registration or something went wrong
    if (ourInfo == null) {
        logger.warn("Could not get member information about us from message sent from $us" +
                " to $peer with ID `${message.header.messageId}`.")
    }
    val counterpartyInfo = this.lookup(us.toCorda(), peer.toCorda(), status)
    if (counterpartyInfo == null) {
        logger.couldNotFindSessionInformation(us.toCorda().shortHash, peer.toCorda().shortHash, message.header.messageId)
        return null
    }
    return SessionManager.SessionCounterparties(
        us.toCorda(),
        peer.toCorda(),
        status,
        counterpartyInfo.serial,
        isCommunicationBetweenMgmAndMember(ourInfo, counterpartyInfo)
    )
}

private fun isCommunicationBetweenMgmAndMember(ourInfo: MemberInfo?, counterpartyInfo: MemberInfo): Boolean {
    return counterpartyInfo.isMgm || ourInfo?.isMgm == true
}
