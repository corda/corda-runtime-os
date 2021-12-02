package net.corda.applications.workers.workercommon.test

import net.corda.applications.workers.workercommon.WorkerParams
import net.corda.applications.workers.workercommon.internal.PARAM_EXTRA
import net.corda.applications.workers.workercommon.internal.PARAM_INSTANCE_ID
import net.corda.libs.configuration.SmartConfigFactoryImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import picocli.CommandLine.Option

/** Tests of worker config parsing. */
class ConfigTests {

    @Test
    fun `worker config contains provided instance ID`() {
        val expectedInstanceId = "3"

        val config = WorkerParams().parseArgs(arrayOf(PARAM_INSTANCE_ID, expectedInstanceId), SmartConfigFactoryImpl())

        assertEquals(expectedInstanceId.toInt(), config.getAnyRef(CONFIG_INSTANCE_ID))
    }

    @Test
    fun `worker config contains autogenerated instance ID if none is provided`() {
        val iterations = 10
        val autogeneratedInstanceIds = mutableSetOf<Int>()

        repeat(iterations) {
            val config = WorkerParams().parseArgs(arrayOf(), SmartConfigFactoryImpl())
            autogeneratedInstanceIds.add(config.getInt(CONFIG_INSTANCE_ID))
        }

        // We check that there are as many items in the set as there were iterations, and thus that no two instance IDs
        // were the same.
        assertEquals(iterations, autogeneratedInstanceIds.size)
    }

    @Test
    fun `throws if instance ID parameter is passed but no value is provided`() {
        val e = assertThrows<IllegalArgumentException> {
            WorkerParams().parseArgs(arrayOf(PARAM_INSTANCE_ID), SmartConfigFactoryImpl())
        }
        assertEquals("Missing required parameter for option '$PARAM_INSTANCE_ID' (<$CONFIG_INSTANCE_ID>)", e.message)
    }

    @Test
    fun `throws if instance ID parameter is passed but cannot be converted to an int`() {
        val nonIntString = "a"

        val e = assertThrows<IllegalArgumentException> {
            WorkerParams().parseArgs(arrayOf(PARAM_INSTANCE_ID, nonIntString), SmartConfigFactoryImpl())
        }
        assertEquals("Invalid value for option '$PARAM_INSTANCE_ID': '$nonIntString' is not an int", e.message)
    }

    @Test
    fun `throws if instance ID parameter is passed multiple times`() {
        val e = assertThrows<IllegalArgumentException> {
            WorkerParams().parseArgs(arrayOf(PARAM_INSTANCE_ID, "1", PARAM_INSTANCE_ID, "2"), SmartConfigFactoryImpl())
        }
        assertEquals("option '$PARAM_INSTANCE_ID' (<$CONFIG_INSTANCE_ID>) should be specified only once", e.message)
    }

    @Test
    fun `worker config contains provided additional parameters`() {
        val keyOne = "abc"
        val expectedValueOne = "xyz"
        val keyTwo = "123"
        val expectedValueTwo = "789"

        val args = arrayOf(PARAM_EXTRA, "$keyOne=$expectedValueOne", PARAM_EXTRA, "$keyTwo=$expectedValueTwo")
        val config = WorkerParams().parseArgs(args, SmartConfigFactoryImpl())

        assertEquals(expectedValueOne, config.getAnyRef("$CONFIG_EXTRA.$keyOne"))
        assertEquals(expectedValueTwo, config.getAnyRef("$CONFIG_EXTRA.$keyTwo"))
    }

    @Test
    fun `throws if additional parameter is passed but no value is provided`() {
        val e = assertThrows<IllegalArgumentException> {
            WorkerParams().parseArgs(arrayOf(PARAM_EXTRA), SmartConfigFactoryImpl())
        }
        assertEquals("Missing required parameter for option '$PARAM_EXTRA' (<String=String>)", e.message)
    }

    @Test
    fun `throws if additional parameter is passed but cannot be converted to a pair`() {
        val extraParamMissingEquals = "abc"

        val e = assertThrows<IllegalArgumentException> {
            WorkerParams().parseArgs(arrayOf(PARAM_EXTRA, extraParamMissingEquals), SmartConfigFactoryImpl())
        }
        assertEquals(
            "Value for option option '$PARAM_EXTRA' (<String=String>) should be in KEY=VALUE format but was $extraParamMissingEquals",
            e.message
        )
    }

    @Test
    fun `throws if unrecognised parameter is passed`() {
        val unrecognisedParameter = "-d"

        val e = assertThrows<IllegalArgumentException> {
            WorkerParams().parseArgs(arrayOf(unrecognisedParameter), SmartConfigFactoryImpl())
        }
        assertEquals("Unknown option: '$unrecognisedParameter'", e.message)
    }

    @Test
    fun `a worker can define extra standard parameters to be parsed`() {
        val expectedValueOne = "abc"
        val expectedValueTwo = "xyz"

        open class ExtendedParams : WorkerParams() {
            @Suppress("Unused")
            @Option(names = ["--testParamOne"])
            var testParamOne = ""
        }

        class DoublyExtendedParams : ExtendedParams() {
            @Suppress("Unused")
            @Option(names = ["--testParamTwo"])
            var testParamTwo = ""
        }

        val args = arrayOf("--testParamOne", expectedValueOne, "--testParamTwo", expectedValueTwo)
        val config = DoublyExtendedParams().parseArgs(args, SmartConfigFactoryImpl())

        assertEquals(expectedValueOne, config.getAnyRef("testParamOne"))
        assertEquals(expectedValueTwo, config.getAnyRef("testParamTwo"))
    }
}