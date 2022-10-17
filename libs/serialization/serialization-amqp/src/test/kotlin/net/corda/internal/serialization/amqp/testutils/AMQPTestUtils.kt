package net.corda.internal.serialization.amqp.testutils

import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.BytesAndSchemas
import net.corda.internal.serialization.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.Schema
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.TransformsSchema
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.serialization.SerializationContext
import net.corda.serialization.SerializationEncoding
import net.corda.utilities.copyTo
import net.corda.utilities.div
import net.corda.utilities.isDirectory
import net.corda.utilities.reflection.packageName_
import net.corda.utilities.toPath
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.SerializedBytes
import org.apache.qpid.proton.codec.Data
import org.junit.jupiter.api.Test
import java.io.File.separatorChar
import java.io.NotSerializableException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.sandbox.SandboxGroup
import org.osgi.framework.Bundle

/**
 * For tests that want to see inside the serializer registry
 */
class TestDescriptorBasedSerializerRegistry : DescriptorBasedSerializerRegistry {
    val contents = mutableMapOf<String, AMQPSerializer<Any>>()

    override fun get(descriptor: String): AMQPSerializer<Any>? = contents[descriptor]

    override fun set(descriptor: String, serializer: AMQPSerializer<Any>) {
        contents.putIfAbsent(descriptor, serializer)
    }

    override fun getOrBuild(descriptor: String, builder: () -> AMQPSerializer<Any>): AMQPSerializer<Any> =
        get(descriptor) ?: builder().also { set(descriptor, it) }
}

@JvmOverloads
fun testDefaultFactory(
    descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
        DefaultDescriptorBasedSerializerRegistry(),
    externalCustomSerializerAllowed: ((Class<*>) -> Boolean)? = null
) =
    SerializerFactoryBuilder.build(
        testSerializationContext.currentSandboxGroup(),
        descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
        externalCustomSerializerAllowed = externalCustomSerializerAllowed
    )

@JvmOverloads
fun testDefaultFactoryNoEvolution(
    descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
        DefaultDescriptorBasedSerializerRegistry()
): SerializerFactory =
    SerializerFactoryBuilder.build(
        testSerializationContext.currentSandboxGroup(),
        descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
        allowEvolution = false
    )

class TestSerializationOutput(
    private val verbose: Boolean,
    serializerFactory: SerializerFactory = testDefaultFactory()
) :
    SerializationOutput(serializerFactory) {

    override fun writeSchema(schema: Schema, data: Data) {
        if (verbose) println(schema)
        super.writeSchema(schema, data)
    }

    override fun writeTransformSchema(transformsSchema: TransformsSchema, data: Data) {
        if (verbose) {
            println("Writing Transform Schema")
            println(transformsSchema)
        }
        super.writeTransformSchema(transformsSchema, data)
    }

    @Throws(NotSerializableException::class)
    fun <T : Any> serialize(obj: T): SerializedBytes<T> {
        try {
            return _serialize(obj, testSerializationContext)
        } finally {
            andFinally()
        }
    }
}

fun testName(): String {
    val classLoader = Thread.currentThread().contextClassLoader
    return Thread.currentThread().stackTrace.first {
        try {
            classLoader.loadClass(it.className).getMethod(it.methodName).isAnnotationPresent(Test::class.java)
        } catch (e: Exception) {
            false
        }
    }.methodName
}

fun Any.testResourceName(): String = "${javaClass.simpleName}.${testName()}"

internal object ProjectStructure {
    val projectRootDir: Path = run {
        var dir = javaClass.getResource("/").toPath()
        while (!(dir / ".git").isDirectory()) {
            dir = dir.parent
        }
        dir
    }
}

fun Any.writeTestResource(bytes: OpaqueBytes) {
    val dir = ProjectStructure.projectRootDir.toString() /
        "libs" / "serialization" / "serialization-amqp" / "src" / "test" / "resources" / javaClass.packageName_.replace('.', separatorChar)
    bytes.open().copyTo(dir / testResourceName(), REPLACE_EXISTING)
}

fun Any.readTestResource(): ByteArray = javaClass.getResourceAsStream(testResourceName()).readBytes()

@Throws(NotSerializableException::class)
inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
    bytes: SerializedBytes<T>,
    context: SerializationContext? = null
): ObjectAndEnvelope<T> {
    return deserializeAndReturnEnvelope(
        bytes, T::class.java,
        context ?: testSerializationContext
    )
}

@Throws(NotSerializableException::class)
inline fun <reified T : Any> DeserializationInput.deserialize(
    bytes: SerializedBytes<T>
): T = deserialize(bytes, T::class.java, testSerializationContext)

@Throws(NotSerializableException::class)
fun <T : Any> SerializationOutput.serializeAndReturnSchema(
    obj: T,
    context: SerializationContext? = null
): BytesAndSchemas<T> = serializeAndReturnSchema(obj, context ?: testSerializationContext)

@Throws(NotSerializableException::class)
fun <T : Any> SerializationOutput.serialize(obj: T, encoding: SerializationEncoding? = null): SerializedBytes<T> {
    return serialize(obj, testSerializationContext.withEncoding(encoding))
}
