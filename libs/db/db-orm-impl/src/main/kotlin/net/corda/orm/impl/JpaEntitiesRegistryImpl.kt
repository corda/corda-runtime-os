package net.corda.orm.impl

import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.osgi.service.component.annotations.Component

@Component(service = [JpaEntitiesRegistry::class])
class JpaEntitiesRegistryImpl : JpaEntitiesRegistry {
    private val fullSet = mutableMapOf<String,JpaEntitiesSet>()

    override val all: Set<JpaEntitiesSet>
        get() = fullSet.values.toSet()

    override fun get(persistenceUnitName: String): JpaEntitiesSet? = fullSet[persistenceUnitName]

    override fun register(persistenceUnitName: String, jpeEntities: Set<Class<*>>) {
        fullSet[persistenceUnitName] = JpaEntitiesSet.create(persistenceUnitName, jpeEntities.toSet())
    }
}