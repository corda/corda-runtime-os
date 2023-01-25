package net.corda.libs.configuration.secret

/**
 * Implementations of [SecretsServiceFactoryResolver] need to be able to resolve all implementations of
 * [SecretsServiceFactory] that are available.
 */
interface SecretsServiceFactoryResolver {
    /**
     * Find all implementations of [SecretsServiceFactory].
     */
    fun findAll(): Collection<SecretsServiceFactory>
}