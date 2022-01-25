package net.corda.orm

interface JpaEntitiesRegistry {
    /**
     * Get all registered [JpaEntitiesSet]s
     */
    val all: Set<JpaEntitiesSet>

    /**
     * Register a new [JpaEntitiesSet]
     *
     * @param set
     */
    fun register(set: JpaEntitiesSet)
}