package net.corda.kryoserialization

import net.corda.bundle1.Cash
import net.corda.sandbox.SandboxException
import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors

@ExtendWith(ServiceExtension::class)
class KryoCheckpointTest {

    companion object {
        @InjectService
        lateinit var checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory

        @TempDir
        lateinit var testDirectory: Path

        @BeforeAll
        @JvmStatic
        fun setUp() {
            val configurationAdmin = ServiceLocator.getConfigurationService()
            assertThat(configurationAdmin).isNotNull

            // Initialise configurationAdmin
            configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name)?.also { config ->
                val properties = Properties()
                properties[BASE_DIRECTORY_KEY] = testDirectory.toString()
                properties[BLACKLISTED_KEYS_KEY] = emptyList<String>()
                properties[PLATFORM_VERSION_KEY] = 999
                config.update(uncheckedCast(properties))
            }

            val allBundles = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles
            val (publicBundles, privateBundles) = allBundles.partition { bundle ->
                bundle.symbolicName in PLATFORM_PUBLIC_BUNDLE_NAMES
            }
            ServiceLocator.getSandboxCreationService().createPublicSandbox(publicBundles, privateBundles)
        }
    }

    @Test
    fun `correct serialization of a simple object`() {
        val sandboxManagementService = SandboxManagementService()

        val builder =
            checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxManagementService.group1)
        val serializer = builder
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .build()

        val tester = TestClass(TestClass.TEST_INT, TestClass.TEST_STRING)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(tested).isNotSameAs(tester)
        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
    }

    @Test
    fun `cross sandbox serialization of a simple object`() {
        val sandboxManagementService = SandboxManagementService()

        val builder1 =
            checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxManagementService.group1)
        val serializerSandbox1 = builder1
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .build()

        val cash = sandboxManagementService.group1
            .loadClassFromCordappBundle("net.corda.bundle1.Cash", Any::class.java)
            .constructors.first().newInstance(1)

        // Serialize with serializerSandbox1
        val bytes = serializerSandbox1.serialize(cash)

        // Try (and fail) to serialize with serializerSandbox2
        Executors.newSingleThreadExecutor().submit {
            val builder2 =
                checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxManagementService.group2)
            val serializerSandbox2 = builder2
                .addSerializer(TestClass::class.java, TestClass.Serializer())
                .build()
            assertThatExceptionOfType(SandboxException::class.java).isThrownBy {
                serializerSandbox2.serialize(cash)
            }
        }.get()

        // Try (and fail) to deserialize with serializerSandbox2
        Executors.newSingleThreadExecutor().submit {
            val builder2 =
                checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxManagementService.group2)
            val serializerSandbox2 = builder2
                .addSerializer(TestClass::class.java, TestClass.Serializer())
                .build()
            assertThatExceptionOfType(SandboxException::class.java).isThrownBy {
                serializerSandbox2.deserialize(bytes, Cash::class.java)
            }
        }.get()
    }
}
