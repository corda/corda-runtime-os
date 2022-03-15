package net.corda.schema

class TestSchema {
    companion object {

        /**
         * P2P Topic Test Schema
         */
        const val CRYPTO_KEYS_TOPIC = "p2p.crypto.keys"
        const val GROUP_POLICIES_TOPIC = "p2p.group.policies"
        const val MEMBER_INFO_TOPIC = "p2p.members.info"
        const val HOSTED_MAP_TOPIC = "p2p.hosted.identities"
    }
}
