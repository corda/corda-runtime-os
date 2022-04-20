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
    const val RECONCILIATION_CONFIG = "corda.reconciliation"

    // Lower level config elements
    //  Messaging
    const val BOOTSTRAP_SERVERS = "messaging.kafka.common.bootstrap.servers"

    //  RPC
    const val RPC_ADDRESS = "address"
    const val RPC_CONTEXT_DESCRIPTION = "context.description"
    const val RPC_CONTEXT_TITLE = "context.title"
    const val RPC_ENDPOINT_TIMEOUT_MILLIS = "endpoint.timeoutMs"
    const val RPC_MAX_CONTENT_LENGTH = "maxContentLength"
    const val RPC_AZUREAD_CLIENT_ID = "sso.azureAd.clientId"
    const val RPC_AZUREAD_CLIENT_SECRET = "sso.azureAd.clientSecret"
    const val RPC_AZUREAD_TENANT_ID = "sso.azureAd.tenantId"

    // Secrets Service
    const val SECRETS_PASSPHRASE = "passphrase"
    const val SECRETS_SALT = "salt"

    // DB
    const val JDBC_DRIVER = "database.jdbc.driver"
    const val JDBC_URL = "database.jdbc.url"
    const val DB_USER = "database.user"
    const val DB_PASS = "database.pass"
    const val DB_POOL_MAX_SIZE = "database.pool.max_size"

    const val WORKSPACE_DIR = "dir.workspace"
    const val TEMP_DIR = "dir.tmp"

    // Scheduled reconciliation tasks
    const val RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS = "permissionSummary.intervalMs"
    const val RECONCILIATION_CPK_WRITE_INTERVAL_MS = "cpkWrite.intervalMs"
}
