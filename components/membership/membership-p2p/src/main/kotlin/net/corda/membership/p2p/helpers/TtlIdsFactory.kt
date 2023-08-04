package net.corda.membership.p2p.helpers


class TtlIdsFactory: MessageIdsFactory(TTL_ID_PREFIX) {
    private companion object {
        const val TTL_ID_PREFIX = "corda.membership.decline.if.ttl-"
    }
}
