package net.corda.libs.configuration.schema.p2p

class LinkManagerConfiguration {

    companion object {
        const val PACKAGE_NAME = "net.corda.p2p.linkmanager"
        const val COMPONENT_NAME = "linkManager"
        const val CONFIG_KEY = "$PACKAGE_NAME.$COMPONENT_NAME"

        /**
         * The value is a list of objects with two string fields (x500Name, groupId), as specified below.
         */
        const val LOCALLY_HOSTED_IDENTITIES_KEY = "locallyHostedIdentities"
        const val LOCALLY_HOSTED_IDENTITY_X500_NAME = "x500Name"
        const val LOCALLY_HOSTED_IDENTITY_GPOUP_ID = "groupId"
        const val LOCALLY_HOSTED_IDENTITY_TLS_TENANT_ID = "tlsTenantId"
        const val LOCALLY_HOSTED_IDENTITY_IDENTITY_TENANT_ID = "identityTenantId"
        const val LOCALLY_HOSTED_TLS_CERTIFICATES = "tlsCertificates"
        const val MAX_MESSAGE_SIZE_KEY = "maxMessageSize"
        const val PROTOCOL_MODE_KEY = "protocolMode"
        const val MESSAGE_REPLAY_KEY_PREFIX = "messageReplay"
        const val BASE_REPLAY_PERIOD_KEY_POSTFIX = "BasePeriod"
        const val CUTOFF_REPLAY_KEY_POSTFIX = "Cutoff"
        const val MAX_REPLAYING_MESSAGES_PER_PEER_POSTFIX = "MaxMessages"
        const val HEARTBEAT_MESSAGE_PERIOD_KEY = "heartbeatMessagePeriod"
        const val SESSION_TIMEOUT_KEY = "sessionTimeout"
    }

}