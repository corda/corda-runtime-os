package net.corda.p2p.linkmanager.integration.stub

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorName

class StateManagerFactoryStub : StateManagerFactory {
    override fun create(config: SmartConfig): StateManager {
        return object : StateManager {
            override val name = LifecycleCoordinatorName.forComponent<StateManager>()
            override fun create(states: Collection<State>): Set<String> = throw UnsupportedOperationException()

            override fun get(keys: Collection<String>): Map<String, State> = throw UnsupportedOperationException()

            override fun update(states: Collection<State>): Map<String, State?> = throw UnsupportedOperationException()

            override fun delete(states: Collection<State>): Map<String, State> = throw UnsupportedOperationException()

            override fun updatedBetween(interval: IntervalFilter): Map<String, State> =
                throw UnsupportedOperationException()

            override fun findByMetadataMatchingAll(filters: Collection<MetadataFilter>): Map<String, State> =
                throw UnsupportedOperationException()

            override fun findByMetadataMatchingAny(filters: Collection<MetadataFilter>): Map<String, State> =
                throw UnsupportedOperationException()

            override fun findUpdatedBetweenWithMetadataMatchingAll(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> = throw UnsupportedOperationException()

            override fun findUpdatedBetweenWithMetadataMatchingAny(
                intervalFilter: IntervalFilter,
                metadataFilters: Collection<MetadataFilter>
            ): Map<String, State> = throw UnsupportedOperationException()

            override val isRunning: Boolean
                get() = true

            override fun start() {}

            override fun stop() {}
        }
    }

}