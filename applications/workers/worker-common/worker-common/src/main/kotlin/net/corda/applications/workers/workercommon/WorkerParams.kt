package net.corda.applications.workers.workercommon

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import picocli.CommandLine
import java.lang.reflect.Field
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * The startup parameters handled by all workers.
 *
 * Can be extended to handle additional parameters for a given worker type.
 */
open class WorkerParams {
    @Suppress("Unused")
    @CommandLine.Option(names = [PARAM_INSTANCE_ID], description = ["The Kafka instance ID for this worker."])
    var instanceId = Random.nextInt().absoluteValue

    @Suppress("Unused")
    @CommandLine.Option(names = [PARAM_EXTRA], description = ["Additional parameters for the processor."])
    var additionalParams = emptyMap<String, String>()

    /**
     * Parses the [args] into a [SmartConfig] object using [CommandLine] (with `this` as a template) and the
     * [smartConfigFactory].
     *
     * @throws IllegalArgumentException If parsing the arguments using [CommandLine] fails.
     */
    internal fun parseArgs(args: Array<String>, smartConfigFactory: SmartConfigFactory): SmartConfig {
        val commandLine = CommandLine(this)
        try {
            commandLine.parseArgs(*args)
        } catch (e: CommandLine.ParameterException) {
            throw IllegalArgumentException(e.message)
        }

        val config = ConfigFactory.parseMap(getParams())
        return smartConfigFactory.create(config)
    }

    /**
     * Creates a map of the names and values of any [CommandLine.Option] fields on this object.
     *
     * Cannot be hardcoded because child classes may add additional fields.
     */
    private fun getParams() = getCommandLineOptionFields().associate { field ->
        field.isAccessible = true
        field.name to field.get(this)
    }

    /** Returns any [CommandLine.Option] fields on this class and any superclasses up to [WorkerParams], inclusive. */
    private fun getCommandLineOptionFields(): Set<Field> {
        val subclasses = mutableSetOf<Class<*>>()
        var currentSubclass: Class<*> = this::class.java
        while (currentSubclass != WorkerParams::class.java) {
            subclasses.add(currentSubclass)
            currentSubclass = currentSubclass.superclass
        }

        return (subclasses + WorkerParams::class.java).flatMapTo(LinkedHashSet()) { klass ->
            klass.declaredFields.filter { field ->
                field.annotations.any { annotation -> annotation.annotationClass == CommandLine.Option::class }
            }
        }
    }
}