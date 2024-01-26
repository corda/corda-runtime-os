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
     * @param defaultSchema optional default schema to use
     * @return [EntityManagerFactory]
     */
    fun create(
        persistenceUnitName: String,
        entities: List<Class<*>>,
        configuration: EntityManagerConfiguration,
        defaultSchema: String? = null
    ): EntityManagerFactory

    /**
     * Create [EntityManagerFactory] for the given JPA [entities] as strings, classloader(s) and target data source
     *
     * @param persistenceUnitName
     * @param classLoaders
     * @param entities
     * @param configuration
     * @param defaultSchema optional default schema to use
     * @return
     */
    fun create(
        persistenceUnitName: String,
        entities: List<String>,
        classLoaders: List<ClassLoader>,
        configuration: EntityManagerConfiguration,
        defaultSchema: String? = null
    ): EntityManagerFactory
}
