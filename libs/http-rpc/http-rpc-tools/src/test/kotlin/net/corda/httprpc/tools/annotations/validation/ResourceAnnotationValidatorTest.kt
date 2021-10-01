package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResourceAnnotationValidatorTest {
    @Test
    fun `validate withoutResourceAnnotation errorListContainsMessage`() {
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1
        }

        val result = ResourceAnnotationValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
        assertEquals(ResourceAnnotationValidator.error, result.errors.single())
    }
}