package net.corda.kryoserialization

import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.*

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

            val privateBundleNames = FrameworkUtil.getBundle(this::class.java).bundleContext.bundles.filter { bundle ->
                bundle.symbolicName !in PLATFORM_PUBLIC_BUNDLE_NAMES
            }.map(Bundle::getSymbolicName)

            // Initialise configurationAdmin
            val properties = Hashtable<String, Any>()
            properties["platformVersion"] = 999
            properties["blacklistedKeys"] = emptyList<Any>()
            properties["baseDirectory"] = testDirectory.toAbsolutePath().toString()
            properties[PLATFORM_SANDBOX_PUBLIC_BUNDLES_KEY] = PLATFORM_PUBLIC_BUNDLE_NAMES
            properties[PLATFORM_SANDBOX_PRIVATE_BUNDLES_KEY] = privateBundleNames
            val conf = configurationAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
            conf?.update(properties)
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
}
