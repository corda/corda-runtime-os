package net.corda.p2p.linkmanager.inbound

import net.corda.data.p2p.LinkInMessage

interface InboundMessage {
    val message: LinkInMessage?
}