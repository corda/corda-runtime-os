package net.corda.flow.mapper.impl.executor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExecuteCleanupEventExecutorTest {

    @Test
    fun testSExecuteCleanupEventExecutor() {
        val result = ExecuteCleanupEventExecutor("key").execute()
        assertThat(result.flowMapperState).isNull()
        assertThat(result.outputEvents).isEmpty()
    }
}