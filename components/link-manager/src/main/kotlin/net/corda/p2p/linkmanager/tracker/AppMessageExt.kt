package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.OutboundUnauthenticatedMessage

internal val AppMessage.id
    get() =
        when (val message = this.message) {
            is AuthenticatedMessage -> {
                message.header.messageId
            }
            is OutboundUnauthenticatedMessage -> {
                message.header.messageId
            }
            is InboundUnauthenticatedMessage -> {
                message.header.messageId
            }
            else -> null
        }
