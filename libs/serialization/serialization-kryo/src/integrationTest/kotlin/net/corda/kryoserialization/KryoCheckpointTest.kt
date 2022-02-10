package net.corda.kryoserialization

import net.corda.bundle1.Cash
import net.corda.sandbox.SandboxException
import net.corda.serialization.checkpoint.factory.CheckpointSerializerBuilderFactory
import net.corda.testing.sandboxes.SandboxSetup
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.concurrent.Executors

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class KryoCheckpointTest {

    companion object {
        @InjectService(timeout = 1000)
        lateinit var checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory

        @InjectService(timeout = 1000)
        lateinit var sandboxSetup: SandboxSetup

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup(@InjectBundleContext bundleContext: BundleContext, @TempDir baseDirectory: Path) {
            sandboxSetup.configure(bundleContext, baseDirectory)
        }

        @Suppress("unused")
        @JvmStatic
        @AfterAll
        fun done() {
            sandboxSetup.shutdown()
        }
    }

    @InjectService(timeout = 1500)
    lateinit var sandboxManagementService: SandboxManagementService

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
            .loadClassFromMainBundles("net.corda.bundle1.Cash")
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
