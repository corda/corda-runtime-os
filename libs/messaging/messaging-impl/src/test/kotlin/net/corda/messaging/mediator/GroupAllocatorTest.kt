package net.corda.messaging.mediator

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class GroupAllocatorTest {

    private val groupAllocator = GroupAllocator()

    @Test
    fun `allocate groups with records of 1 key below min group size`() {
        val config = buildTestConfig(2, 20)
        val records = getIntRecords(listOf(19))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result, mapOf(0 to 19))
    }

    @Test
    fun `allocate groups with records of 2 keys below min group size`() {
        val config = buildTestConfig(2, 20)
        val records = getIntRecords(listOf(15, 4))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result,  mapOf(0 to 19))
    }

    @Test
    fun `allocate groups with records of 1 key above min group size`() {
        val config = buildTestConfig(2, 20)
        val records = getIntRecords(listOf(60))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result,  mapOf(0 to 60))
    }

    @Test
    fun `allocate groups with records of 2 keys above min group size`() {
        val config = buildTestConfig(4, 20)
        val records = getIntRecords(listOf(35, 25))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result, mapOf(0 to 35, 1 to 25))
    }

    @Test
    fun `allocate small groups of records with 6 keys`() {
        val config = buildTestConfig(4, 20)
        val records = getIntRecords(listOf(5, 8, 7, 5, 8, 6))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result, mapOf(0 to 20, 1 to 19))
    }

    @Test
    fun `allocate large groups of records with 6 keys`() {
        val config = buildTestConfig(8, 20)
        val records = getIntRecords(listOf(15, 18, 17, 15, 18, 15))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result, mapOf(0 to 18, 1 to 18, 2 to 17, 3 to 32, 4 to 15))
    }

    @Test
    fun `allocate large groups of records with 6 keys but fewer threads than groups`() {
        val config = buildTestConfig(4, 20)
        val records = getIntRecords(listOf(15, 18, 17, 15, 18, 15))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result, mapOf(0 to 18, 1 to 18, 2 to 32, 3 to 30))
    }

    @Test
    fun `allocate large groups of records with 6 keys with less threads than groups`() {
        val config = buildTestConfig(2, 20)
        val records = getIntRecords(listOf(15, 18, 17, 15, 18, 15))

        val result = groupAllocator.allocateGroups(records, config)

        assertGroupsSize(result, mapOf(0 to 50, 1 to 48))
    }

    private fun assertGroupsSize(groups: List<Map<Int, List<Record<Int, Int>>>>, groupSize: Map<Int, Int> ) {
        assertEquals(groupSize.size, groups.size)

        groupSize.map {
            assertEquals(groupSize[it.key], groups[it.key].values.flatten().size)
        }
    }

    private fun buildTestConfig(threadCount: Int, minGroupSize: Int): EventMediatorConfig<Int, Int, Int> {
        return EventMediatorConfig(
            "",
            SmartConfigImpl.empty(),
            emptyList(),
            emptyList(),
            mock<StateAndEventProcessor<Int, Int, Int>>(),
            mock<MessageRouterFactory>(),
            threadCount,
            "",
            mock<StateManager>(),
            minGroupSize
        )
    }

    private fun getIntRecords( recordCountByKey: List<Int>): List<Record<Int, Int>> {
        val records = mutableListOf<Record<Int, Int>>()
        for (i in recordCountByKey.indices) {
            for (j in 1..recordCountByKey[i]) {
                records.add(Record(null, i, j))
            }
        }
        return records
    }
}
