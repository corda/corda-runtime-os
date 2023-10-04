package net.corda.session.mapper.service.integration

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import org.osgi.service.component.annotations.Component

/**
 * The real state manager implementation requires postgres to run. As a result, it is impossible to plug it into the
 * flow mapper integration tests and have those execute in a non-postgres environment at present.
 *
 * The flow mapper integration tests do not currently need the state manager and so this can be used as a temporary
 * workaround. However, longer term this will not be feasible.
 */
@Component
class TestStateManagerFactoryImpl : StateManagerFactory {

    override fun create(config: SmartConfig): StateManager {
        return object : StateManager {
            override fun close() {
            }

            override fun create(states: Collection<State>): Map<String, Exception> {
                TODO("Not yet implemented")
            }

            override fun get(keys: Collection<String>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun update(states: Collection<State>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun delete(states: Collection<State>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun updatedBetween(interval: IntervalFilter): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> {
                TODO("Not yet implemented")
            }

            override fun findUpdatedBetweenWithMetadataFilter(
                intervalFilter: IntervalFilter,
                metadataFilter: MetadataFilter
            ): Map<String, State> {
                TODO("Not yet implemented")
            }
        }
    }
}