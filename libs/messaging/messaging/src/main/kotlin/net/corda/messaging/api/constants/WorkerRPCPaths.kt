package net.corda.messaging.api.constants

/**
 * These are the paths which should be appended to the Corda worker service endpoints to create the
 * full RPC endpoint URI. E.g.: "${messagingConfig.getString(CRYPTO_WORKER_REST_ENDPOINT)}$CRYPTO_PATH"
 *
 */
object WorkerRPCPaths {
    const val CRYPTO_PATH = "/crypto"
    const val LEDGER_PATH = "/ledger"
    const val PERSISTENCE_PATH = "/persistence"
    const val UNIQUENESS_PATH = "/uniqueness-checker"
    const val VERIFICATION_PATH = "/verification"
}
