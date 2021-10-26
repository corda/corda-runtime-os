package net.corda.serialization.amqp.test

import net.corda.classinfo.ClassInfoService
import net.corda.install.InstallService
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.amqp.DefaultDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.ObjectAndEnvelope
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactory
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.amqpMagic
import net.corda.packaging.CPI
import net.corda.sandbox.SandboxCreationService
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import java.io.NotSerializableException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Hashtable
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@Disabled("Need to fix file cleanup on Windows")
class AMQPwithOSGiSerializationTests {

    private val testingBundle = FrameworkUtil.getBundle(this::class.java)

    companion object {
        lateinit var installService: InstallService
        lateinit var classInfoService: ClassInfoService
        lateinit var sandboxCreationService: SandboxCreationService

        private val cordappVersion = System.getProperty("test.cordapp.version") ?: fail("Version number missing")

        @TempDir
        lateinit var testDirectory: Path

        @BeforeAll
        @JvmStatic
        fun setUp() {
            val configurationAdmin = ServiceLocator.getConfigurationService()
            installService = ServiceLocator.getInstallService()
            classInfoService = ServiceLocator.getClassInfoService()
            sandboxCreationService = ServiceLocator.getSandboxCreationService()

            assertThat(configurationAdmin).isNotNull
            assertThat(installService).isNotNull
            assertThat(sandboxCreationService).isNotNull
            assertThat(classInfoService).isNotNull

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

        private fun assembleCpb(cpkUrls: List<URL>): CPI {
            val cpks = cpkUrls.map { url ->
                val urlAsString = url.toString()
                val cpkName = urlAsString.substring(urlAsString.lastIndexOf("/") + 1)
                val cpkFile = Files.createTempFile(cpkName, ".cpk")
                Files.newOutputStream(cpkFile).use {
                    url.openStream().copyTo(it)
                }
                cpkFile.toAbsolutePath()
            }.toList()

            val tempFile = Files.createTempFile("dummy-cordapp-bundle", ".cpb")
            return try {
                Files.newOutputStream(tempFile).use { outputStream ->
                    CPI.assemble(outputStream, "dummy-cordapp-bundle", "1.0", cpks)
                }
                installService.loadCpb(Files.newInputStream(tempFile))
            } finally {
                Files.delete(tempFile)
                cpks.map(Files::delete)
            }
        }
    }

    @JvmOverloads
    fun testDefaultFactoryNoEvolution(descriptorBasedSerializerRegistry: DescriptorBasedSerializerRegistry =
                                              DefaultDescriptorBasedSerializerRegistry()): SerializerFactory =
            SerializerFactoryBuilder.build(
                    AllWhitelist,
                    descriptorBasedSerializerRegistry = descriptorBasedSerializerRegistry,
                    allowEvolution = false)

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> DeserializationInput.deserializeAndReturnEnvelope(
            bytes: SerializedBytes<T>,
            serializationContext: SerializationContext
    ): ObjectAndEnvelope<T> = deserializeAndReturnEnvelope(bytes, T::class.java, serializationContext)

    @Test
    fun `successfully deserialise when composed bundle class is installed`() {
        val cpk1 = testingBundle.getResource("TestSerializable1-workflows-$cordappVersion-cordapp.cpk")
                ?: fail("TestSerializable1-workflows-$cordappVersion-cordapp.cpk is missing")
        val cpk2 = testingBundle.getResource("TestSerializable2-workflows-$cordappVersion-cordapp.cpk")
                ?: fail("TestSerializable2-workflows-$cordappVersion-cordapp.cpk is missing")
        val cpk3 = testingBundle.getResource("TestSerializable3-workflows-$cordappVersion-cordapp.cpk")
                ?: fail("TestSerializable3-workflows-$cordappVersion-cordapp.cpk is missing")
        val cpk4 = testingBundle.getResource("TestSerializable4-workflows-$cordappVersion-cordapp.cpk")
                ?: fail("TestSerializable4-workflows-$cordappVersion-cordapp.cpk is missing")

        val cpb = assembleCpb(listOf(cpk1, cpk2, cpk3, cpk4))
        val cpks = installService.getCpb(cpb.metadata.id)!!.cpks

        // Create sandbox group
        val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks.map {it.metadata.hash})
        assertThat(sandboxGroup).isNotNull
        assertThat(sandboxGroup.sandboxes).hasSize(4)

        // Initialised two serialisation factories to avoid having successful tests due to caching
        val factory1 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactoryNoEvolution()

        // Initialise the serialisation context
        val testSerializationContext = SerializationContextImpl(
                preferredSerializationVersion = amqpMagic,
                whitelist = AllWhitelist,
                properties = mutableMapOf(),
                objectReferencesEnabled = false,
                useCase = SerializationContext.UseCase.Testing,
                encoding = null,
                classInfoService = classInfoService,
                sandboxGroup = sandboxGroup)

        // Serialise our object
        val cashClass = sandboxGroup.loadClassFromCordappBundle(
                cpkIdentifier = installService.getCpb(cpb.metadata.id)!!.cpks.find {
                    it.metadata.id.name == "net.corda.serializable-cpk-one" }!!.metadata.id,
                className = "net.corda.bundle1.Cash")
        val cashInstance = cashClass.getConstructor(Int::class.java).newInstance(100)

        val obligationClass = sandboxGroup.loadClassFromCordappBundle(
                cpkIdentifier = installService.getCpb(cpb.metadata.id)!!.cpks.find {
                    it.metadata.id.name == "net.corda.serializable-cpk-three" }!!.metadata.id,
                className = "net.corda.bundle3.Obligation")

        val obligationInstance = obligationClass.getConstructor(
                cashInstance.javaClass
        ).newInstance(cashInstance)

        val content = "This is a transfer document"

        val documentClass = sandboxGroup.loadClassFromCordappBundle(
                cpkIdentifier = installService.getCpb(cpb.metadata.id)!!.cpks.find {
                    it.metadata.id.name == "net.corda.serializable-cpk-two" }!!.metadata.id,
                className = "net.corda.bundle2.Document")
        val documentInstance = documentClass.getConstructor(String::class.java).newInstance(content)

        val transferClass = sandboxGroup.loadClassFromCordappBundle(
                cpkIdentifier = installService.getCpb(cpb.metadata.id)!!.cpks.find {
                    it.metadata.id.name == "net.corda.serializable-cpk-four" }!!.metadata.id,
                className = "net.corda.bundle4.Transfer")

        val transferInstance = transferClass.getConstructor(
                obligationInstance.javaClass, documentInstance.javaClass
        ).newInstance(obligationInstance, documentInstance)

        val serialised = SerializationOutput(factory1).serialize(transferInstance, testSerializationContext)

        // Perform deserialisation and check if the correct class is deserialised
        val deserialised = DeserializationInput(factory2).deserializeAndReturnEnvelope(serialised, testSerializationContext)

        assertThat(deserialised.obj.javaClass.name).isEqualTo("net.corda.bundle4.Transfer")
        assertThat(deserialised.obj.javaClass.declaredFields.map { it.name }.toList()).contains("document")

        val document = deserialised.obj.javaClass.getDeclaredField("document").also { it.trySetAccessible() }.get(deserialised.obj)
        assertThat(document?.javaClass?.declaredFields?.map { it.name }?.toList()).contains("content")

        val deserialisedValue = document?.javaClass?.getDeclaredField("content").also { it?.trySetAccessible() }?.get(document)
        assertThat(deserialisedValue).isEqualTo(content)

        assertThat(deserialised.envelope.metadata.values).hasSize(4)
        assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle1.Cash")
        assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle2.Document")
        assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle3.Obligation")
        assertThat(deserialised.envelope.metadata.values).containsKey("net.corda.bundle4.Transfer")
    }
}