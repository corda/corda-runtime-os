package net.corda.flow.mapper.factory

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperMetaData
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.messaging.api.records.Record

interface FlowMapperMetaDataFactory {

    /**
     * Create a meta data object to extract common fields from different types of [FlowMapperEvent]s and the flow mapper[state].
     * Provide the possible output topics for events via [flowMapperTopics]
     * @return A common object representing the data needed to execute the event.
     */
    fun createFromEvent(flowMapperTopics: FlowMapperTopics, state: FlowMapperState?, eventRecord: Record<String, FlowMapperEvent>):
            FlowMapperMetaData
}
