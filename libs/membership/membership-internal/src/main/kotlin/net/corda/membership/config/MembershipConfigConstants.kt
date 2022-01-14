package net.corda.membership.config

import net.corda.schema.Schemas

object MembershipConfigConstants {
    // Config key
    const val CONFIG_KEY = "membership"

    object Registration {
        // Config key
        const val CONFIG_KEY = "registration"
    }

    object Sync {
        // Config key
        const val CONFIG_KEY = "sync"
    }

    object Kafka {
        // Config key
        const val CONFIG_KEY = "kafka"

        // Common keys
        const val GROUP_NAME_KEY = "groupName"
        const val TOPIC_NAME_KEY = "topicName"
        const val CLIENT_ID_KEY = "clientId"

        object Persistence {
            // Config key
            const val CONFIG_KEY = "persistence"

            object MemberList {
                // Config key
                const val CONFIG_KEY = "memberlist"

                // Defaults copy the default topic name
                const val DEFAULT_GROUP_NAME = Schemas.Membership.MEMBER_LIST_TOPIC
                const val DEFAULT_CLIENT_ID = Schemas.Membership.MEMBER_LIST_TOPIC
            }

            object CpiWhitelist {
                // Config key
                const val CONFIG_KEY = "cpiWhitelist"

                // Defaults copy the default topic name
                const val DEFAULT_GROUP_NAME = Schemas.Membership.CPI_WHITELIST_TOPIC
                const val DEFAULT_CLIENT_ID = Schemas.Membership.CPI_WHITELIST_TOPIC
            }

            object GroupParameters {
                // Config key
                const val CONFIG_KEY = "groupParameters"

                // Defaults copy the default topic name
                const val DEFAULT_GROUP_NAME = Schemas.Membership.GROUP_PARAMETERS_TOPIC
                const val DEFAULT_CLIENT_ID = Schemas.Membership.GROUP_PARAMETERS_TOPIC
            }

            object Proposals {
                // Config key
                const val CONFIG_KEY = "proposals"

                // Defaults copy the default topic name
                const val DEFAULT_GROUP_NAME = Schemas.Membership.PROPOSAL_TOPIC
                const val DEFAULT_CLIENT_ID = Schemas.Membership.PROPOSAL_TOPIC
            }
        }

        object Messaging {
            // Config key
            const val CONFIG_KEY = "messaging"

            object Updates {
                // Config key
                const val CONFIG_KEY = "updates"

                // Defaults copy the default topic name
                const val DEFAULT_GROUP_NAME = Schemas.Membership.UPDATE_TOPIC
                const val DEFAULT_CLIENT_ID = Schemas.Membership.UPDATE_TOPIC
            }

            object Events {
                // Config key
                const val CONFIG_KEY = "events"

                // Defaults copy the default topic name
                const val DEFAULT_GROUP_NAME = Schemas.Membership.EVENT_TOPIC
                const val DEFAULT_CLIENT_ID = Schemas.Membership.EVENT_TOPIC
            }
        }
    }
}