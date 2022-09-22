package net.corda.simulator.crypto

/**
 * Categories of HSM supported by Corda.
 */
enum class HsmCategory {
    /**
     * Ledger keys. These can also be retrieved via `MemberInfo`.
     */
    LEDGER
}
