package net.corda.kryoserialization.test

import net.cordapp.bundle1.Cash
import net.corda.sandbox.SandboxException
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.concurrent.Executors

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class KryoCheckpointTest {

    companion object {
        @JvmStatic
        @RegisterExtension
        private val lifecycle = AllTestsLifecycle()
    }

    @InjectService(timeout = 1000)
    lateinit var checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory

    private lateinit var sandboxManagementService: SandboxManagementService

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        baseDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, baseDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            sandboxManagementService = setup.fetchService(timeout = 1500)
        }
    }

    @Test
    fun `correct serialization of a simple object`() {
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
        val builder1 =
            checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxManagementService.group1)
        val serializerSandbox1 = builder1
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .build()

        val cash = sandboxManagementService.group1
            .loadClassFromMainBundles("net.cordapp.bundle1.Cash")
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
