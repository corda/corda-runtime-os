package net.corda.libs.configuration.schema.p2p

class LinkManagerConfiguration {

    companion object {
        private const val PACKAGE_NAME = "net.corda.p2p.linkmanager"
        private const val COMPONENT_NAME = "linkManager"
        const val CONFIG_KEY = "$PACKAGE_NAME.$COMPONENT_NAME"

        /**
         * The value is a list of objects with two string fields (x500Name, groupId), as specified below.
         */
        const val LOCALLY_HOSTED_IDENTITIES_KEY = "locallyHostedIdentities"
        const val LOCALLY_HOSTED_IDENTITY_X500_NAME = "x500Name"
        const val LOCALLY_HOSTED_IDENTITY_GPOUP_ID = "groupId"
    }

}