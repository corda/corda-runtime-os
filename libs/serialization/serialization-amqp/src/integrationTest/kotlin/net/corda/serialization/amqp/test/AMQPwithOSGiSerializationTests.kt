package net.corda.serialization.amqp.test

import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.IllegalCustomSerializerException
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.securitymanager.SecurityManagerService
import net.corda.serialization.SerializationContext
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.utilities.copyTo
import net.corda.utilities.div
import net.corda.utilities.reflection.packageName_
import net.corda.utilities.toByteSequence
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.SerializationCustomSerializer
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.io.File
import java.io.NotSerializableException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class AMQPwithOSGiSerializationTests {
    private val testSerializationContext = AMQP_STORAGE_CONTEXT

    @RegisterExtension
    private val lifecycle = EachTestLifecycle()

    private lateinit var sandboxFactory: SandboxFactory

    @InjectService(timeout = 1000)
    lateinit var securityManagerService: SecurityManagerService

    @BeforeAll
    fun setUp(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            sandboxFactory = setup.fetchService(timeout = 1500)
        }
    }

    private fun testDefaultFactory(sandboxGroup: SandboxGroup): SerializerFactory =
        SerializerFactoryBuilder.build(sandboxGroup, allowEvolution = true)

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
            bytes: SerializedBytes<T>,
            serializationContext: SerializationContext
    ): ObjectAndEnvelope<T> = deserializeAndReturnEnvelope(bytes, T::class.java, serializationContext)

    @Test
    fun `successfully deserialise when composed bundle class is installed`() {
        // Create sandbox group
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializable4-workflows.cpb")
        try {
            // Initialised two serialisation factories to avoid having successful tests due to caching
            val factory1 = testDefaultFactory(sandboxGroup)
            val factory2 = testDefaultFactory(sandboxGroup)

            // Initialise the serialisation context
            val testSerializationContext = testSerializationContext.withSandboxGroup(sandboxGroup)

            // Serialise our object
            val cashClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle1.Cash")
            val cashInstance = cashClass.getConstructor(Int::class.java).newInstance(100)

            val obligationClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle3.Obligation")

            val obligationInstance = obligationClass.getConstructor(
                cashInstance.javaClass
            ).newInstance(cashInstance)

            val documentClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle2.Document")
            val content = "This is a transfer document"
            val documentInstance = documentClass.getConstructor(String::class.java).newInstance(content)

            // Container is used to test amqp serialization works for OSGi bundled generic types.
            val containerClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle5.Container")
            val containerInstance = containerClass.getConstructor(Object::class.java).newInstance(5)

            val transferClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle4.Transfer")

            val transferInstance = transferClass.getConstructor(
                obligationInstance.javaClass, documentInstance.javaClass, containerInstance.javaClass
            ).newInstance(obligationInstance, documentInstance, containerInstance)

            val serialised = SerializationOutput(factory1).serialize(transferInstance, testSerializationContext)

            // Perform deserialisation and check if the correct class is deserialised
            val deserialised =
                DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

            assertThat(deserialised.obj.javaClass.name).isEqualTo("net.cordapp.bundle4.Transfer")
            assertThat(deserialised.obj.javaClass.declaredFields.map { it.name }.toList()).contains("document")

            val document = deserialised.obj.javaClass.getDeclaredField("document").also { it.trySetAccessible() }
                .get(deserialised.obj)
            assertThat(document?.javaClass?.declaredFields?.map { it.name }?.toList()).contains("content")

            val deserialisedValue =
                document?.javaClass?.getDeclaredField("content").also { it?.trySetAccessible() }?.get(document)
            assertThat(deserialisedValue).isEqualTo(content)

            assertThat(deserialised.envelope.metadata.values).hasSize(5)
            assertThat(deserialised.envelope.metadata.values).containsKey("net.cordapp.bundle1.Cash")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.cordapp.bundle2.Document")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.cordapp.bundle3.Obligation")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.cordapp.bundle5.Container")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.cordapp.bundle4.Transfer")
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }

    @Test
    fun `same class gets loaded from different class loaders when deserialized from different sandbox groups`() {
        val sandboxGroup1 = sandboxFactory.loadSandboxGroup("META-INF/TestSerializable4-workflows.cpb")
        val sandboxGroup2 = sandboxFactory.loadSandboxGroup("META-INF/TestSerializable4-workflows.cpb")
        try {
            // Initialised two serialisation factories to avoid having successful tests due to caching
            val factory1 = testDefaultFactory(sandboxGroup1)
            val factory11 = testDefaultFactory(sandboxGroup1)
            val factory2 = testDefaultFactory(sandboxGroup2)

            // Initialise two different serialization contexts one per sandbox group
            val testSerializationContext1 = testSerializationContext.withSandboxGroup(sandboxGroup1)
            val testSerializationContext2 = testSerializationContext.withSandboxGroup(sandboxGroup2)

            // Serialise our object using `sandboxGroup1` context
            val cashClass = sandboxGroup1.loadClassFromMainBundles("net.cordapp.bundle1.Cash")
            val cashInstance = cashClass.getConstructor(Int::class.java).newInstance(100)
            val serialised = SerializationOutput(factory1).serialize(cashInstance, testSerializationContext1)

            // Perform deserialisations and check if the correct classes are deserialised
            val deserialised1 =
                DeserializationInput(factory11).deserializeAndReturnEnvelope(serialised, testSerializationContext1)
            val deserialised2 =
                DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext2)

            val expectedClass2 = sandboxGroup2.loadClassFromMainBundles("net.cordapp.bundle1.Cash")
            val deserialisedClass1 = deserialised1.obj::class.java
            val deserialisedClass2 = deserialised2.obj::class.java
            val classLoader1 = deserialisedClass1.classLoader
            val classLoader2 = deserialisedClass2.classLoader
            assertThat(cashClass).isEqualTo(deserialisedClass1)
            assertThat(expectedClass2).isEqualTo(deserialisedClass2)
            assertThat(deserialisedClass1).isNotEqualTo(deserialisedClass2)
            assertThat(classLoader1).isNotEqualTo(classLoader2)
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup1)
            sandboxFactory.unloadSandboxGroup(sandboxGroup2)
        }
    }

    @Test
    fun `amqp to be serialized objects can only live in cpk's main bundle`() {
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableCpk-using-lib.cpb")
        try {
            val factory = testDefaultFactory(sandboxGroup)
            val context = testSerializationContext.withSandboxGroup(sandboxGroup)

            val mainBundleItemClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle.MainBundleItem")
            val mainBundleItemInstance = mainBundleItemClass.getMethod("newInstance").invoke(null)

            assertThrows<SandboxException>(
                "Attempted to create evolvable class tag for cpk private bundle com.example.serialization.serialization-cpk-library."
            ) {
                SerializationOutput(factory).serialize(mainBundleItemInstance, context)
            }
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }

    @Test
    fun `sandbox external custom serializers targeting platform types are denied`() {
        applyPolicyFile("security-deny-platform-serializers.policy")
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableCpk-platform-type-custom-serializer.cpb")
        try {
            // Corda platform type custom serializer
            val factory = testDefaultFactory(sandboxGroup)
            val serializer =
                sandboxGroup
                    .loadClassFromMainBundles("net.cordapp.bundle.VersionSerializer")
                    .getConstructor()
                    .newInstance() as SerializationCustomSerializer<*, *>
            assertThrows<IllegalCustomSerializerException> {
                factory.registerExternal(serializer, factory)
            }

            // JDK type custom serializer
            val factory1 = testDefaultFactory(sandboxGroup)
            val serializer1 =
                sandboxGroup
                    .loadClassFromMainBundles("net.cordapp.bundle.ThreadSerializer")
                    .getConstructor()
                    .newInstance() as SerializationCustomSerializer<*, *>
            assertThrows<IllegalCustomSerializerException> {
                factory1.registerExternal(serializer1, factory1)
            }
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }

    @Test
    fun `sandbox external custom serializers targeting sandbox types are allowed`() {
        applyPolicyFile("security-deny-platform-serializers.policy")
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableCpk-platform-type-custom-serializer.cpb")
        try {
            val factory = testDefaultFactory(sandboxGroup)
            val serializer =
                sandboxGroup
                    .loadClassFromMainBundles("net.cordapp.bundle.SandboxTypeSerializer")
                    .getConstructor()
                    .newInstance() as SerializationCustomSerializer<*, *>
            factory.registerExternal(serializer, factory)
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }

    private fun applyPolicyFile(fileName: String) {
        val url = this::class.java.classLoader.getResource(fileName) ?: fail("Resource $fileName not found")
        val policy = securityManagerService.readPolicy(url.openConnection().getInputStream())
        securityManagerService.updatePermissions(policy, clear = false)
    }

    @CordaSerializable
    data class SignableDataAmqpTest(val contents: String)

    @CordaSerializable
    data class TestMapOfSignableData(val signableData: Map<Int, SignableDataAmqpTest>)

    @Test
    fun mapOfSignableData() {
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableCpk-using-lib.cpb")
        try {
            val factory = testDefaultFactory(sandboxGroup)
            registerCustomSerializers(factory)
            val context = testSerializationContext.withSandboxGroup(sandboxGroup)

            val testObject = TestMapOfSignableData(mapOf(1 to SignableDataAmqpTest("mapOfSignableData")))

            val serializedBytes = SerializationOutput(factory).serialize(testObject, context)
            val deserialize = DeserializationInput(factory).deserialize(serializedBytes, context)

            assertEquals(testObject, deserialize)
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }

    @CordaSerializable
    data class TestListOfSignableData(val signableData: List<SignableDataAmqpTest>)

    @Test
    fun listOfSignableData() {
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableCpk-using-lib.cpb")
        try {
            val factory = testDefaultFactory(sandboxGroup)
            registerCustomSerializers(factory)
            val context = testSerializationContext.withSandboxGroup(sandboxGroup)

            val testObject = TestListOfSignableData(listOf(SignableDataAmqpTest("listOfSignableData")))

            val serializedBytes = SerializationOutput(factory).serialize(testObject, context)
            val deserialize = DeserializationInput(factory).deserialize(serializedBytes, context)

            assertEquals(testObject, deserialize)

        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }


    // Based on writeTestResource from AMQPTestUtils.kt which is not available as an OSGi exported package
    @Suppress("unused")
    private fun Any.writeIntegrationTestResource(bytes: OpaqueBytes, testResourceName: String) {
        // Change to the full path of the repository to regenerate resources
        val projectRootDir = "/full-path-to-repo-dir"
        val dir = projectRootDir / "libs" / "serialization" / "serialization-amqp"/"src"/"integrationTest"/"resources" / javaClass.packageName_.replace('.', File.separatorChar)
        bytes.open().copyTo(dir / testResourceName, StandardCopyOption.REPLACE_EXISTING)
    }

    @Test
    fun `amqp evolution works when upgrading cpks`() {
        // Build and save original state
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableEvolutionNewer-workflows.cpb")
        try {
            val factory = testDefaultFactory(sandboxGroup)
            val context = testSerializationContext.withSandboxGroup(sandboxGroup)

            val testResourceName = "UpgradingCpk.bin"
            val uuid = UUID.fromString("8a1a7d89-20b1-412e-bba2-c8612210284f")
            // Uncomment to rebuild resource file + also uncomment older version of SerializableStateToNewerVersion class
            // and the version number in serializable-cpk-evolution-newer/build.gradle
//            val originalStateClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle.evolution.newer.SerializableStateToNewerVersion")
//            val originalStateInstance = originalStateClass.getConstructor(UUID::class.java).newInstance(uuid)
//            val serialize = SerializationOutput(factory).serialize(originalStateInstance, context)
//            writeIntegrationTestResource(serialize, testResourceName)

            // Load saved state
            // Test with current version of CPK
            val resource = this::class.java.getResourceAsStream(testResourceName)
                ?: throw RuntimeException("$testResourceName not found")
            val deserialize: Any =
                DeserializationInput(factory).deserialize(
                    resource.readAllBytes().toByteSequence(),
                    Any::class.java,
                    context
                )
            val actualId = deserialize::class.java.getMethod("getId").invoke(deserialize) as UUID
            assertEquals(uuid, actualId)

            val actualAddedField = deserialize::class.java.getMethod("getAddedField").invoke(deserialize) as String?
            assertEquals(null, actualAddedField)
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }

    @Test
    fun `amqp evolution works when downgrading cpks`() {
        // Build and save original state
        val sandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableEvolutionOlder-workflows.cpb")
        try {
            val factory = testDefaultFactory(sandboxGroup)
            val context = testSerializationContext.withSandboxGroup(sandboxGroup)

            val testResourceName = "OlderCpk.bin"
            val uuid = UUID.fromString("8a1a7d89-20b1-412e-bba2-c8612210284f")
            // Uncomment to rebuild resource file + also uncomment newer version of SerializableStateToOlderVersion class
            // and the version number in serializable-cpk-evolution-older/build.gradle
//            val originalStateClass = sandboxGroup.loadClassFromMainBundles("net.cordapp.bundle.evolution.older.SerializableStateToOlderVersion")
//            val originalStateInstance = originalStateClass.getConstructor(UUID::class.java, String::class.java).newInstance(uuid, "TEST")
//            val serialize = SerializationOutput(factory).serialize(originalStateInstance, context)
//            writeIntegrationTestResource(serialize, testResourceName)

            // Load saved state
            // Test with current version of CPK
            val resource = this::class.java.getResourceAsStream(testResourceName)
                ?: throw RuntimeException("$testResourceName not found")
            val deserialized: Any =
                DeserializationInput(factory).deserialize(
                    resource.readAllBytes().toByteSequence(),
                    Any::class.java,
                    context
                )
            val actual = deserialized::class.java.getMethod("getId").invoke(deserialized) as UUID
            assertEquals(uuid, actual)
        } finally {
            sandboxFactory.unloadSandboxGroup(sandboxGroup)
        }
    }

    @Test
    fun `amqp evolution blocks swapping cpks`() {
        // Build original state and serialize
        val originalSandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableEvolutionDifferentOriginal-workflows.cpb")
        val serializedBytes = try {
            val originalFactory = testDefaultFactory(originalSandboxGroup)
            val originalContext = testSerializationContext.withSandboxGroup(originalSandboxGroup)

            val originalStateClass =
                originalSandboxGroup.loadClassFromMainBundles("net.cordapp.bundle.evolution.different.SerializableStateForDifferentCpk")
            val originalStateInstance =
                originalStateClass.getConstructor(UUID::class.java).newInstance(UUID.randomUUID())
            SerializationOutput(originalFactory).serialize(originalStateInstance, originalContext)
        } finally {
            sandboxFactory.unloadSandboxGroup(originalSandboxGroup)
        }

        // Test with replacement CPK
        val replacementSandboxGroup = sandboxFactory.loadSandboxGroup("META-INF/TestSerializableEvolutionDifferentReplacement-workflows.cpb")
        try {
            val replacementFactory = testDefaultFactory(replacementSandboxGroup)
            val replacementContext = testSerializationContext.withSandboxGroup(replacementSandboxGroup)
            val deserializationInput = DeserializationInput(replacementFactory)

            assertThrows<NotSerializableException> {
                deserializationInput.deserialize(serializedBytes, replacementContext)
            }
        } finally {
            sandboxFactory.unloadSandboxGroup(replacementSandboxGroup)
        }
    }
}
