package net.corda.schema.configuration;

/** The keys for various configurations for a worker. */
public final class ConfigKeys {
    private ConfigKeys() {
    }

    // These root keys are the values that will be used when configuration changes. Writers will use them when
    // publishing changes to one of the config sections defined by a key, and readers will use the keys to
    // determine which config section a given update is for.
    public static final String BOOT_CONFIG = "corda.boot";
    public static final String CRYPTO_CONFIG = "corda.cryptoLibrary";
    public static final String DB_CONFIG = "corda.db";
    public static final String FLOW_CONFIG = "corda.flow";
    public static final String MESSAGING_CONFIG = "corda.messaging";
    public static final String UTXO_LEDGER_CONFIG = "corda.ledger.utxo";
    public static final String P2P_LINK_MANAGER_CONFIG = "corda.p2p.linkManager";
    public static final String P2P_GATEWAY_CONFIG = "corda.p2p.gateway";
    public static final String REST_CONFIG = "corda.rest";
    public static final String SECRETS_CONFIG = "corda.secrets";
    public static final String SANDBOX_CONFIG = "corda.sandbox";
    public static final String RECONCILIATION_CONFIG = "corda.reconciliation";
    public static final String MEMBERSHIP_CONFIG = "corda.membership";
    public static final String SECURITY_CONFIG = "corda.security";

    //  REST
    public static final String REST_ADDRESS = "address";
    public static final String REST_CONTEXT_DESCRIPTION = "context.description";
    public static final String REST_CONTEXT_TITLE = "context.title";
    public static final String REST_ENDPOINT_TIMEOUT_MILLIS = "endpoint.timeoutMs";
    public static final String REST_MAX_CONTENT_LENGTH = "maxContentLength";
    public static final String REST_AZUREAD_CLIENT_ID = "sso.azureAd.clientId";
    public static final String REST_AZUREAD_CLIENT_SECRET = "sso.azureAd.clientSecret";
    public static final String REST_AZUREAD_TENANT_ID = "sso.azureAd.tenantId";
    public static final String REST_WEBSOCKET_CONNECTION_IDLE_TIMEOUT_MS = "websocket.idleTimeoutMs";

    // Secrets Service
    // 
    // SECRETS_TYPE control which secrets service implementation will be selected.
    //
    // Only a subset of the other keys will be needed for specific secrets service implementation.
    //
    // For instance:
    //   - EncryptionSecretsService in corda-runtime-os needs SECRETS_PASSPHRASE and SECRETS_SALT
    //   - The Hashicorp Vault secrets add on needs SECRETS_SERVER_ADDRESS, SECRETS_SERVER_ADDRESS and
    //     SECRETS_CREATED_SECRET_PATH
    //
    public static final String SECRETS_TYPE = "type";
    public static final String SECRETS_PASSPHRASE = "passphrase";
    public static final String SECRETS_SALT = "salt";
    public static final String SECRETS_SERVER_ADDRESS = "serverAddress";
    public static final String SECRETS_SERVER_CREDENTIALS = "serverCredentials";
    public static final String SECRETS_CREATED_SECRET_PATH = "createdSecretPath";
    public static final String WORKSPACE_DIR = "dir.workspace";
    public static final String TEMP_DIR = "dir.tmp";

    // Sandbox
    public static final String SANDBOX_CACHE_SIZE = "cache.size";

    // Security
    public static final String SECURITY_POLICY = "policy";
}
