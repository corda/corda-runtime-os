package net.corda.orm

interface JpaEntitiesRegistry {
    /**
     * Get all registered [JpaEntitiesSet]s
     */
    val all: Set<JpaEntitiesSet>

    /**
     * Get [JpaEntitiesSet] for the given [persistenceUnitName]
     *
     * @param persistenceUnitName
     * @return [JpaEntitiesSet] or null if no such set exists.
     */
    fun get(persistenceUnitName: String): JpaEntitiesSet?

    /**
     * Register a new [JpaEntitiesSet]
     *
     * @param persistenceUnitName
     * @param set of classes that are part of the persistent unit.
     */
    fun register(persistenceUnitName: String, jpeEntities: Set<Class<*>>)
}