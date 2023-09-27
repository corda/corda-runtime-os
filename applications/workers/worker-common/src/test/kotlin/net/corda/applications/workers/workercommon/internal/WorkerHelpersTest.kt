package net.corda.applications.workers.workercommon.internal

import com.typesafe.config.ConfigFactory
import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.applications.workers.workercommon.WorkerHelpers.Companion.mergeOver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkerHelpersTest {
    @Test
    fun `apply top level key to all params`() {
        val key = "top"
        val originalKey = "second"
        val value = "val"
        val map = mapOf(originalKey to value)

        val result = WorkerHelpers.createConfigFromParams(key, map)

        assertThat(result.getString("top.second")).isEqualTo("val")
    }

    @Test
    fun `apply top level key to all params with multiple keys`() {
        val key = "top"
        val map = mapOf(
            "originalKey1" to "val1",
            "originalKey2" to "val2"
        )

        val result = WorkerHelpers.createConfigFromParams(key, map)

        assertThat(result.getString("top.originalKey1")).isEqualTo("val1")
        assertThat(result.getString("top.originalKey2")).isEqualTo("val2")
    }

    @Test
    fun `merge list of config into empty accumulator`() {
        val acc = ConfigFactory.empty()
        val conf1 = ConfigFactory.parseMap(
            mapOf("top.a" to "val1")
        )
        val conf2 = ConfigFactory.parseMap(
            mapOf("top.b" to "val2")
        )

        val result = listOf(conf1, conf2).mergeOver(acc)

        assertTrue(result.hasPath("top.a"))
        assertTrue(result.hasPath("top.b"))
        assertThat(result.getString("top.a")).isEqualTo("val1")
        assertThat(result.getString("top.b")).isEqualTo("val2")
    }

    @Test
    fun `merging configs should overwrite duplicate keys in the accumulator`() {
        val accumulator = ConfigFactory.parseMap(
            mapOf("top.a" to "value1", "top.b" to "value2", "top.c" to "value3", "othertop.a" to "value4")
        )
        val conf1 = ConfigFactory.parseMap(
            mapOf("top.a" to "newVal1")
        )
        val conf2 = ConfigFactory.parseMap(
            mapOf("top.b" to "newVal2")
        )

        val result = listOf(conf1, conf2).mergeOver(accumulator)

        assertThat(result.getString("top.a")).isEqualTo("newVal1")
        assertThat(result.getString("top.b")).isEqualTo("newVal2")
        assertThat(result.getString("top.c")).isEqualTo("value3")
        assertThat(result.getString("othertop.a")).isEqualTo("value4")
    }

    @Test
    fun `merging empty configs should overwrite duplicate keys in the accumulator`() {
        val accumulator = ConfigFactory.parseMap(
            mapOf("top.a" to "value1", "top.b" to "value2", "top.c" to "value3", "othertop.a" to "value4")
        )
        val conf1 = ConfigFactory.empty()
        val conf2 = ConfigFactory.empty()

        val result = listOf(conf1, conf2).mergeOver(accumulator)

        assertThat(result.getString("top.a")).isEqualTo("value1")
        assertThat(result.getString("top.b")).isEqualTo("value2")
        assertThat(result.getString("top.c")).isEqualTo("value3")
        assertThat(result.getString("othertop.a")).isEqualTo("value4")
    }
}