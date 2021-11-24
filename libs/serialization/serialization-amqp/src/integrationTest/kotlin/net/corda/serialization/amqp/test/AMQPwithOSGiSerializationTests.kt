package net.corda.serialization.amqp.test

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
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxCreationService
import net.corda.serialization.SerializationContext
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.io.NotSerializableException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Hashtable
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
@ExtendWith(ServiceExtension::class)
class AMQPwithOSGiSerializationTests {

    private val testingBundle = FrameworkUtil.getBundle(this::class.java)

    companion object {
        @InjectService
        lateinit var configurationAdmin: ConfigurationAdmin

        @InjectService
        lateinit var scr: ServiceComponentRuntime

        @InjectService
        lateinit var installService: InstallService

        @InjectService
        lateinit var sandboxContextService: SandboxContextService

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

        private fun assembleCPI(cpkUrls: List<URL>): CPI {
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
                    AllWhitelist,
                    sandboxGroup,
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

        assembleCPI(listOf(cpk1, cpk2, cpk3, cpk4)).use { cpi ->
            val cpks = installService.getCpb(cpi.metadata.id)!!.cpks

            // Create sandbox group
            val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks)
            assertThat(sandboxGroup).isNotNull

            // Initialised two serialisation factories to avoid having successful tests due to caching
            val factory1 = testDefaultFactoryNoEvolution(sandboxGroup)
            val factory2 = testDefaultFactoryNoEvolution(sandboxGroup)

            // Initialise the serialisation context
            val testSerializationContext = SerializationContextImpl(
                preferredSerializationVersion = amqpMagic,
                whitelist = AllWhitelist,
                properties = mutableMapOf(),
                objectReferencesEnabled = false,
                useCase = SerializationContext.UseCase.Testing,
                encoding = null,
                classInfoService = sandboxContextService,
                sandboxGroup = sandboxGroup
            )

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

    @Test
    fun `amqp to be serialized objects can only live in cpk's main bundle`() {
        val cpk = testingBundle.getResource("TestSerializableCpk-using-lib-$cordappVersion-cordapp.cpk")
            ?: fail("TestSerializableCpk-using-lib-$cordappVersion-cordapp.cpk is missing")

        val cpi = assembleCPI(listOf(cpk))
        val cpks = installService.getCpb(cpi.metadata.id)!!.cpks
        val sandboxGroup = sandboxCreationService.createSandboxGroup(cpks)
        val factory = testDefaultFactoryNoEvolution(sandboxGroup)
        val context = SerializationContextImpl(
            preferredSerializationVersion = amqpMagic,
            whitelist = AllWhitelist,
            properties = mutableMapOf(),
            objectReferencesEnabled = false,
            useCase = SerializationContext.UseCase.Testing,
            encoding = null,
            classInfoService = sandboxContextService,
            sandboxGroup = sandboxGroup
        )

        val mainBundleItemClass = sandboxGroup.loadClassFromMainBundles("net.corda.bundle.MainBundleItem")
        val mainBundleItemInstance = mainBundleItemClass.getMethod("newInstance").invoke(null)

        assertThrows<SandboxException>(
            "Attempted to create evolvable class tag for cpk private bundle com.example.serialization.serialization-cpk-library."
        ) {
            SerializationOutput(factory).serialize(mainBundleItemInstance, context)
        }
    }
}