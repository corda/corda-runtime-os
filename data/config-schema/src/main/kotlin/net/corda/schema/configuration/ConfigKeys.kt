package net.corda.schema.configuration

/**
 * The following constants define the root keys for various configurations within a virtual node.
 *
 * These keys are the values that will be used when configuration changes.
 *
 * So writers will use them publishing changes one of the config sections defined by a key, and readers will
 * use the keys to determine which config section a given update is for.
 */
class ConfigKeys {
    companion object {
        const val BOOT_CONFIG = "corda.boot"
        const val CRYPTO_CONFIG = "corda.cryptoLibrary"
        const val DB_CONFIG = "corda.db"
        const val FLOW_CONFIG = "corda.flow"
        const val IDENTITY_CONFIG = "corda.identity"
        const val MESSAGING_CONFIG = "corda.messaging"
        const val P2P_CONFIG = "corda.p2p"
        const val PLATFORM_CONFIG = "corda.platform"
        const val POLICY_CONFIG = "corda.policy"
        const val RPC_CONFIG = "corda.rpc"
        const val SECRETS_CONFIG = "corda.secrets"
        const val SANDBOX_CONFIG = "corda.sandbox"
    }
}
