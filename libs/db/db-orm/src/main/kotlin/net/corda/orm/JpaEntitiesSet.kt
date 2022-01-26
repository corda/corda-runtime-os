package net.corda.orm

/**
 * Groups a set of classes into a logical unit which may be used to construct the [javax.persistence.EntityManagerFactory]
 * using [EntityManagerFactoryFactory]
 */
interface JpaEntitiesSet {
    companion object {
        fun create(name: String, classes: Set<Class<*>>): JpaEntitiesSet {
            return object :JpaEntitiesSet {
                override val persistenceUnitName: String
                    get() = name
                override val classes: Set<Class<*>>
                    get() = classes

            }
        }
    }

    val persistenceUnitName: String
    val classes: Set<Class<*>>
}

