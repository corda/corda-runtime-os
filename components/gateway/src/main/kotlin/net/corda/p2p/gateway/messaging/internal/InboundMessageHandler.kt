package net.corda.p2p.gateway.messaging.internal

import net.corda.messaging.api.subscription.LifeCycle

//TODO: Do we need this? Maybe a simple publisher for received messages as there's no subscription to attach to
class InboundMessageHandler :  LifeCycle {
    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}