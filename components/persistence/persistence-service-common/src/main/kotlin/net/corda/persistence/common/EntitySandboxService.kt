package net.corda.persistence.common

import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity

interface EntitySandboxService {

    /**
     * Get (or create) the entity (db) sandbox for the given holding identity
     *
     * Corda allows its flow authors to use plain Java objects (POJOs) for data entities such as ledger input,
     * output and reference states.
     * That means we need to instantiate user classes in Corda workers, and we use sandboxing to protect the
     * Corda cluster from user level code.
     *
     * @throws [CordaRuntimeException] if not found
     */
    fun get(holdingIdentity: HoldingIdentity, cpkFileHashes: Set<SecureHash>): SandboxGroupContext
}
