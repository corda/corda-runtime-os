package net.corda.orm

/**
 * Groups a set of classes into a logical unit which may be used to construct the [javax.persistence.EntityManagerFactory]
 * using [EntityManagerFactoryFactory]
 */
interface EntitiesSet {
    val name: String
    val content: Set<Class<*>>
}