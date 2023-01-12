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
    const val MESSAGING_CONFIG = "corda.messaging"
    const val UTXO_LEDGER_CONFIG = "corda.ledger.utxo"
    const val P2P_LINK_MANAGER_CONFIG = "corda.p2p.linkManager"
    const val P2P_GATEWAY_CONFIG = "corda.p2p.gateway"
    const val REST_CONFIG = "corda.rest"
    const val SECRETS_CONFIG = "corda.secrets"
    const val SANDBOX_CONFIG = "corda.sandbox"
    const val RECONCILIATION_CONFIG = "corda.reconciliation"
    const val MEMBERSHIP_CONFIG = "corda.membership"
    const val SECURITY_CONFIG = "corda.security"

    //  REST
    const val REST_ADDRESS = "address"
    const val REST_CONTEXT_DESCRIPTION = "context.description"
    const val REST_CONTEXT_TITLE = "context.title"
    const val REST_ENDPOINT_TIMEOUT_MILLIS = "endpoint.timeoutMs"
    const val REST_MAX_CONTENT_LENGTH = "maxContentLength"
    const val REST_AZUREAD_CLIENT_ID = "sso.azureAd.clientId"
    const val REST_AZUREAD_CLIENT_SECRET = "sso.azureAd.clientSecret"
    const val REST_AZUREAD_TENANT_ID = "sso.azureAd.tenantId"
    const val REST_WEBSOCKET_CONNECTION_IDLE_TIMEOUT_MS = "websocket.idleTimeoutMs"

    // Secrets Service
    const val SECRETS_TYPE = "type"
    const val SECRETS_PASSPHRASE = "passphrase"
    const val SECRETS_SALT = "salt"

    const val WORKSPACE_DIR = "dir.workspace"
    const val TEMP_DIR = "dir.tmp"

    // Sandbox
    const val SANDBOX_CACHE_SIZE = "cache.size"

    // Security
    const val SECURITY_POLICY = "policy"
}
