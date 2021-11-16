package net.corda.crypto.impl.config

object DefaultConfigConsts {
    object Kafka {
        const val GROUP_NAME_KEY = "groupName"
        const val TOPIC_NAME_KEY = "topicName"
        const val CLIENT_ID_KEY = "clientId"

        object Signing {
            const val GROUP_NAME = "crypto.keys"
            const val TOPIC_NAME = "crypto.keys"
            const val CLIENT_ID = "crypto.keys"
        }

        object DefaultCryptoService {
            const val GROUP_NAME = "crypto.keys.defaultservice"
            const val TOPIC_NAME = "crypto.keys.defaultservice"
            const val CLIENT_ID = "crypto.keys.defaultservice"
        }

        object MemberConfig {
            const val GROUP_NAME = "crypto.config.members"
            const val TOPIC_NAME = "crypto.config.members"
            const val CLIENT_ID = "crypto.config.members"
        }

        object Rpc {
            const val GROUP_NAME = "crypto.rpc"
            const val FRESH_KEY_REQUEST_TOPIC_NAME = "crypto.rpc.freshkeys"
            const val SIGNING_REQUEST_TOPIC_NAME = "crypto.rpc.signing"
            const val CLIENT_ID = "crypto.rpc"
        }
    }
}