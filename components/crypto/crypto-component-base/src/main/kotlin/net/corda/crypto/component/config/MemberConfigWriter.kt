package net.corda.crypto.component.config

import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import java.util.concurrent.CompletableFuture

/**
 * Defines operations to write member's configuration.
 */
interface MemberConfigWriter {
    /**
     * Persist the specified member's configuration.
     */
    fun put(memberId: String, entity: CryptoMemberConfig): CompletableFuture<Unit>
}
