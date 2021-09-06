package net.corda.orm

import javax.persistence.EntityManagerFactory

/**
 * EntityManagerFactory factory
 */
interface EntityManagerFactoryFactory {
    /**
     * Create [EntityManagerFactory] for the given JPA [entities] and target data source
     *
     * @param persistenceUnitName
     * @param entities to be managed by the [EntityManagerFactory]
     * @param configuration for the target data source
     * @return [EntityManagerFactory]
     */
    fun create(
        persistenceUnitName: String,
        entities: List<Class<*>>,
        configuration: EntityManagerConfiguration
    ): EntityManagerFactory
}
