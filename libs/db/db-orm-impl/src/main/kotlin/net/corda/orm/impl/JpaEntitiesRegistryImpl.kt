package net.corda.orm.impl

import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.osgi.service.component.annotations.Component

@Component(service = [JpaEntitiesRegistry::class])
class JpaEntitiesRegistryImpl : JpaEntitiesRegistry {
    private val fullSet = mutableSetOf<JpaEntitiesSet>()

    override val all: Set<JpaEntitiesSet>
        get() = fullSet

    override fun register(set: JpaEntitiesSet) {
        fullSet.add(set)
    }
}