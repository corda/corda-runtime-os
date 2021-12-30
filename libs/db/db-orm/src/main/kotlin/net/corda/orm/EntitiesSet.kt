package net.corda.orm

/**
 * Groups a set of classes into a logical unit which may be used to construct the [javax.persistence.EntityManagerFactory]
 * using [EntityManagerFactoryFactory]
 */
interface EntitiesSet {

    companion object {
        fun of(name: String, content: Set<Class<*>>): EntitiesSet {
            return EntitiesSetImpl(name, content)
        }

        private class EntitiesSetImpl(override val name: String, override val content: Set<Class<*>>) : EntitiesSet
    }

    val name: String
    val content: Set<Class<*>>
}