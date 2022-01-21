package net.corda.serialization.amqp.test

import net.corda.install.InstallService
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.AMQP_STORAGE_CONTEXT
import net.corda.internal.serialization.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.utilities.copyTo
import net.corda.utilities.div
import net.corda.utilities.reflection.packageName_
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.File
import java.io.NotSerializableException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Hashtable
import java.util.UUID
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@ExtendWith(ServiceExtension::class)
class AMQPwithOSGiSerializationTests {

    private val testingBundle = FrameworkUtil.getBundle(this::class.java)
    private val testSerializationContext = AMQP_STORAGE_CONTEXT

    companion object {
        @InjectService
        lateinit var configurationAdmin: ConfigurationAdmin

        @InjectService
        lateinit var scr: ServiceComponentRuntime

        @InjectService
        lateinit var installService: InstallService

        @InjectService
        lateinit var sandboxCreationService: SandboxCreationService

        private val cordappVersion = System.getProperty("test.cordapp.version") ?: fail("Version number missing")

        @BeforeAll
        @JvmStatic
        fun setUp(@TempDir testDirectory: Path) {
            // Initialise configurationAdmin
            val properties = Hashtable<String, Any>()
            properties["platformVersion"] = 999
            properties["blacklistedKeys"] = emptyList<Any>()
            properties["baseDirectory"] = testDirectory.toAbsolutePath().toString()
            val conf = configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
            conf?.update(properties)

            val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
            val (publicBundles, privateBundles) = allBundles.partition { bundle ->
                bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
            }
            sandboxCreationService.createPublicSandbox(publicBundles, privateBundles)
        }

        private fun assembleCPI(vararg cpkUrls: URL): CPI {
            val cpks = cpkUrls.map { url ->
                val urlAsString = url.toString()
                val cpkName = urlAsString.substring(urlAsString.lastIndexOf("/") + 1)
                val cpkFile = Files.createTempFile(cpkName, ".cpk")
                Files.newOutputStream(cpkFile).use {
                    url.openStream().copyTo(it)
                }
                cpkFile.toAbsolutePath()
            }

            val tempFile = Files.createTempFile("dummy-cordapp-bundle", ".cpb")
            return try {
                Files.newOutputStream(tempFile).use { outputStream ->
                    CPI.assemble(outputStream, "dummy-cordapp-bundle", "1.0", cpks)
                }
                Files.newInputStream(tempFile).use(installService::loadCpb)
            } finally {
                Files.delete(tempFile)
                cpks.map(Files::delete)
            }
        }

        @JvmStatic
        @AfterAll
        fun done() {
            // Deactivate the InstallService before JUnit removes the TempDir.
            val installBundle = FrameworkUtil.getBundle(installService::class.java)
            val dto = scr.getComponentDescriptionDTO(installBundle, installService::class.java.name)
            scr.disableComponent(dto).value
        }
    }

    @JvmOverloads
    fun testDefaultFactoryNoEvolution(sandboxGroup: SandboxGroup, descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                                              DefaultDescriptorBasedSerializerRegistry()): SerializerFactory =
            SerializerFactoryBuilder.build(
                    sandboxGroup,
                    descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
                    allowEvolution = false)

    @JvmOverloads
    fun testDefaultFactory(sandboxGroup: SandboxGroup, descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
        DefaultDescriptorBasedSerializerRegistry()): SerializerFactory =
        SerializerFactoryBuilder.build(
            AllWhitelist,
            sandboxGroup,
            descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
            allowEvolution = true)

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
            bytes: SerializedBytes<T>,
            serializationContext: SerializationContext
    ): ObjectAndEnvelope<T> = deserializeAndReturnEnvelope(bytes, T::class.java, serializationContext)

    @Test
    fun `successfully deserialise when composed bundle class is installed`() {
        val cpk1 = getCpkResource("TestSerializable1-workflows-$cordappVersion-cordapp.cpk")
        val cpk2 = getCpkResource("TestSerializable2-workflows-$cordappVersion-cordapp.cpk")
        val cpk3 = getCpkResource("TestSerializable3-workflows-$cordappVersion-cordapp.cpk")
        val cpk4 = getCpkResource("TestSerializable4-workflows-$cordappVersion-cordapp.cpk")

        assembleCPI(cpk1, cpk2, cpk3, cpk4).use { cpi ->
            val cpks = installService.getCpb(cpi.metadata.id)!!.cpks

            // Create sandbox group
            val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks)
            assertThat(sandboxGroup).isNotNull

            // Initialised two serialisation factories to avoid having successful tests due to caching
            val factory1 = testDefaultFactoryNoEvolution(sandboxGroup)
            val factory2 = testDefaultFactoryNoEvolution(sandboxGroup)

            // Initialise the serialisation context
            val testSerializationContext = testSerializationContext.withSandboxGroup(sandboxGroup)

            // Serialise our object
            val cashClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle1.Cash")
            val cashInstance = cashClass.getConstructor(Int::class.java).newInstance(100)

            val obligationClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle3.Obligation")

            val obligationInstance = obligationClass.getConstructor(
                cashInstance.javaClass
            ).newInstance(cashInstance)

            val documentClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle2.Document")
            val content = "This is a transfer document"
            val documentInstance = documentClass.getConstructor(String::class.java).newInstance(content)

            // Container is used to test amqp serialization works for OSGi bundled generic types.
            val containerClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle5.Container")
            val containerInstance = containerClass.getConstructor(Object::class.java).newInstance(5)

            val transferClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle4.Transfer")

            val transferInstance = transferClass.getConstructor(
                obligationInstance.javaClass, documentInstance.javaClass, containerInstance.javaClass
            ).newInstance(obligationInstance, documentInstance, containerInstance)

            val serialised = SerializationOutput(factory1).serialize(transferInstance, testSerializationContext)

            // Perform deserialisation and check if the correct class is deserialised
            val deserialised =
                DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

            assertThat(deserialised.obj.javaClass.name).isEqualTo("net.corda.bundle4.Transfer")
            assertThat(deserialised.obj.javaClass.declaredFields.map { it.name }.toList()).contains("document")

            val document = deserialised.obj.javaClass.getDeclaredField("document").also { it.trySetAccessible() }
                .get(deserialised.obj)
            assertThat(document?.javaClass?.declaredFields?.map { it.name }?.toList()).contains("content")

            val deserialisedValue =
                document?.javaClass?.getDeclaredField("content").also { it?.trySetAccessible() }?.get(document)
            assertThat(deserialisedValue).isEqualTo(content)

            assertThat(deserialised.envelope.metadata.values).hasSize(5)
            assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle1.Cash")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle2.Document")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle3.Obligation")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle5.Container")
            assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle4.Transfer")
        }
    }

    private fun getCpkResource(cpkFilename: String) =
        testingBundle.getResource(cpkFilename) ?: fail("$cpkFilename is missing")

    @Test
    fun `amqp to be serialized objects can only live in cpk's main bundle`() {
        val cpk = getCpkResource("TestSerializableCpk-using-lib-$cordappVersion-cordapp.cpk")

        val cpi = assembleCPI(cpk)
        val cpks = installService.getCpb(cpi.metadata.id)!!.cpks
        val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks)
        val factory = testDefaultFactoryNoEvolution(sandboxGroup)
        val context = testSerializationContext.withSandboxGroup(sandboxGroup)

        val mainBundleItemClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle.MainBundleItem")
        val mainBundleItemInstance = mainBundleItemClass.getMethod("newInstance").invoke(null)

        assertThrows<SandboxException>(
            "Attempted to create evolvable class tag for cpk private bundle com.example.serialization.serialization-cpk-library."
        ) {
            SerializationOutput(factory).serialize(mainBundleItemInstance, context)
        }
    }

    // Based on writeTestResource from AMQPTestUtils.kt which is not available as an OSGi exported package
    private fun Any.writeIntegrationTestResource(bytes: OpaqueBytes, testResourceName: String) {
        // Change to the full path of the repository to regenerate resources
        val projectRootDir = "/full-path-to-repo-dir"
        val dir = projectRootDir / "libs" / "serialization" / "serialization-amqp"/"src"/"integrationTest"/"resources" / javaClass.packageName_.replace('.', File.separatorChar)
        bytes.open().copyTo(dir / testResourceName, StandardCopyOption.REPLACE_EXISTING)
    }

    @Test
    fun `amqp evolution works when upgrading cpks`() {
        // Build and save original state
        val cpk = getCpkResource("TestSerializableEvolutionNewer-workflows-$cordappVersion-cordapp.cpk")
        val cpi = assembleCPI(cpk)
        val cpks = installService.getCpb(cpi.metadata.id)!!.cpks
        val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks)
        val factory = testDefaultFactory(sandboxGroup)
        val context = testSerializationContext.withSandboxGroup(sandboxGroup)

        val testResourceName = "UpgradingCpk.bin"
        val uuid = UUID.fromString("8a1a7d89-20b1-412e-bba2-c8612210284f")
        // Uncomment to rebuild resource file + also uncomment older version of SerializableStateToNewerVersion class
        // and the version number in serializable-cpk-evolution-newer/build.gradle
//        val originalStateClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle.evolution.newer.SerializableStateToNewerVersion")
//        val originalStateInstance = originalStateClass.getConstructor(UUID::class.java).newInstance(uuid)
//        val serialize = SerializationOutput(factory).serialize(originalStateInstance, context)
//        this.writeIntegrationTestResource(serialize, testResourceName)

        // Load saved state
        // Test with current version of CPK
        val resource = this.javaClass.getResourceAsStream(testResourceName)
            ?: throw RuntimeException("$testResourceName not found")
        val deserialize: Any =
            DeserializationInput(factory).deserialize(
            ByteSequence.of(resource.readAllBytes()),
            Any::class.java,
            context
            )
        val actualId = deserialize::class.java.getMethod("getId").invoke(deserialize) as UUID
        assertEquals(uuid, actualId)

        val actualAddedField = deserialize::class.java.getMethod("getAddedField").invoke(deserialize) as String?
        assertEquals(null, actualAddedField)
    }

    @Test
    fun `amqp evolution works when downgrading cpks`() {
        // Build and save original state
        val cpk = getCpkResource("TestSerializableEvolutionOlder-workflows-$cordappVersion-cordapp.cpk")
        val cpi = assembleCPI(cpk)
        val cpks = installService.getCpb(cpi.metadata.id)!!.cpks
        val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks)
        val factory = testDefaultFactory(sandboxGroup)
        val context = testSerializationContext.withSandboxGroup(sandboxGroup)

        val testResourceName = "OlderCpk.bin"
        val uuid = UUID.fromString("8a1a7d89-20b1-412e-bba2-c8612210284f")
        // Uncomment to rebuild resource file + also uncomment newer version of SerializableStateToOlderVersion class
        // and the version number in serializable-cpk-evolution-older/build.gradle
//        val originalStateClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle.evolution.older.SerializableStateToOlderVersion")
//        val originalStateInstance = originalStateClass.getConstructor(UUID::class.java, String::class.java).newInstance(uuid, "TEST")
//        val serialize = SerializationOutput(factory).serialize(originalStateInstance, context)
//        this.writeIntegrationTestResource(serialize, testResourceName)

        // Load saved state
        // Test with current version of CPK
        val resource = this.javaClass.getResourceAsStream(testResourceName)
            ?: throw RuntimeException("$testResourceName not found")
        val deserialized: Any =
            DeserializationInput(factory).deserialize(
                ByteSequence.of(resource.readAllBytes()),
                Any::class.java,
                context
            )
        val actual = deserialized::class.java.getMethod("getId").invoke(deserialized) as UUID
        assertEquals(uuid, actual)
    }

    @Test
    fun `amqp evolution blocks swapping cpks`() {
        // Build original state and serialize
        val originalCpk = getCpkResource("TestSerializableEvolutionDifferentOriginal-workflows-$cordappVersion-cordapp.cpk")

        val originalCpi = assembleCPI(originalCpk)
        val originalCpks = installService.getCpb(originalCpi.metadata.id)!!.cpks
        val originalSandboxGroup = sandboxCreationService.createSandboxGroup(originalCpks)
        val originalFactory = testDefaultFactoryNoEvolution(originalSandboxGroup)
        val originalContext = testSerializationContext.withSandboxGroup(originalSandboxGroup)

        val originalStateClass = originalSandboxGroup.loadClassFromMainBundles("net.corda.bundle.evolution.different.SerializableStateForDifferentCpk")
        val originalStateInstance = originalStateClass.getConstructor(UUID::class.java).newInstance(UUID.randomUUID())

        val serializedBytes = SerializationOutput(originalFactory).serialize(originalStateInstance, originalContext)

        // Test with replacement CPK
        val replacementCpk = getCpkResource("TestSerializableEvolutionDifferentReplacement-workflows-$cordappVersion-cordapp.cpk")
        val replacementCpi = assembleCPI(replacementCpk)
        val replacementCpks = installService.getCpb(replacementCpi.metadata.id)!!.cpks
        val replacementSandboxGroup = sandboxCreationService.createSandboxGroup(replacementCpks)
        val replacementFactory = testDefaultFactoryNoEvolution(replacementSandboxGroup)
        val replacementContext = testSerializationContext.withSandboxGroup(replacementSandboxGroup)
        val deserializationInput = DeserializationInput(replacementFactory)

        assertThrows<NotSerializableException> {
            deserializationInput.deserialize(serializedBytes, replacementContext)
        }
    }
}