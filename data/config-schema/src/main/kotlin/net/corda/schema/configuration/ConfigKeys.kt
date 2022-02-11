package net.corda.schema.configuration

/** The keys for various configurations for a worker. */
object ConfigKeys {
    // These root keys are the values that will be used when configuration changes. Writers will use them when
    // publishing changes to one of the config sections defined by a key, and readers will use the keys to
    // determine which config section a given update is for.
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

    // Lower level config elements
    //  Messaging
    const val BOOTSTRAP_SERVERS = "messaging.kafka.common.bootstrap.servers"

    //  RPC
    const val RPC_ENDPOINT_TIMEOUT_MILLIS = "rpc.endpoint.timeoutMs"

    // Secrets Service
    const val SECRETS_PASSPHRASE = "passphrase"
    const val SECRETS_SALT = "salt"

    // DB
    const val JDBC_DRIVER = "database.jdbc.driver"
    const val JDBC_URL = "database.jdbc.url"
    const val DB_USER = "database.user"
    const val DB_PASS = "database.pass"
    const val DB_POOL_MAX_SIZE = "database.pool.max_size"
}
