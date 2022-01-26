package net.corda.orm.impl

import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.osgi.service.component.annotations.Component
import java.util.concurrent.ConcurrentHashMap

@Component(service = [JpaEntitiesRegistry::class])
class JpaEntitiesRegistryImpl : JpaEntitiesRegistry {
    private val fullSet = ConcurrentHashMap<String,JpaEntitiesSet>()

    override val all: Set<JpaEntitiesSet>
        get() = fullSet.values.toSet()

    override fun get(persistenceUnitName: String): JpaEntitiesSet? = fullSet[persistenceUnitName]

    override fun register(persistenceUnitName: String, jpeEntities: Set<Class<*>>) {
        fullSet.putIfAbsent(persistenceUnitName, JpaEntitiesSet.create(persistenceUnitName, jpeEntities.toSet()))
    }
}