package net.corda.kryoserialization

import net.corda.serialization.factory.CheckpointSerializerBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class KryoCheckpointTest {

    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxManagementService: SandboxManagementService

        @InjectService
        lateinit var checkpointSerializerBuilderFactory: CheckpointSerializerBuilderFactory
    }

    @Test
    fun `correct serialization of a simple object`() {
        val builder = checkpointSerializerBuilderFactory.createCheckpointSerializerBuilder(sandboxManagementService.group1)
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
