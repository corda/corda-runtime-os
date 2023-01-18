package net.corda.application.addon

/**
 * Implementations of [CordaAddonResolver] need to be able to resolve all implementations of [CordaAddon] that are
 * available.
 */
interface CordaAddonResolver {
    /**
     * Find all implementations of [CordaAddon].
     */
    fun findAll(): Collection<CordaAddon>
}