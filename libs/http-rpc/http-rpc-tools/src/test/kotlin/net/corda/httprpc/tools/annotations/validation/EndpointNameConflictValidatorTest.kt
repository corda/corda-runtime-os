package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.tools.annotations.validation.EndpointNameConflictValidator.Companion.error
import net.corda.httprpc.tools.annotations.validation.utils.EndpointType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

class EndpointNameConflictValidatorTest {

    @Test
    fun `validate withEndpointNameConflictOnSamePath errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @HttpRpcGET("/test")
            @Suppress("unused")
            abstract fun test()

            @HttpRpcGET("/test")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictOnDifferentMethodType errorListIsEmpty`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @HttpRpcGET("/test")
            @Suppress("unused")
            abstract fun test()

            @HttpRpcPOST("/test")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictOnMissingEndpointAnnotation errorListIsEmpty`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @Suppress("unused")
            abstract fun test()

            @HttpRpcPOST("/test")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictOnOverload errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @HttpRpcGET(path = "test")
            @Suppress("unused")
            abstract fun test()

            @HttpRpcGET(path = "test")
            @Suppress("unused")
            abstract fun test(@HttpRpcQueryParameter foo: String)

            @HttpRpcGET(path = "test")
            @Suppress("unused")
            abstract fun test(@HttpRpcQueryParameter foo: Int, @HttpRpcQueryParameter bar: String = "")
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertThat(result.errors).hasSize(2).allMatch { error ->
            error == error("test", EndpointType.GET, TestInterface::class.java.methods.first { it.name == "test" }) }
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithDefaultName errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @HttpRpcGET
            @Suppress("unused")
            abstract fun test()

            @HttpRpcGET("")
            abstract fun test2()
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors).hasSize(1).contains(error("null", EndpointType.GET, TestInterface::test2.javaMethod!!))
    }

    @Test
    fun `validate withEndpointNameConflictWithCapitalization errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @HttpRpcGET("teSt")
            @Suppress("unused")
            abstract fun teSt()

            @HttpRpcGET("tEst")
            @Suppress("unused")
            abstract fun test2()

            @HttpRpcGET("test")
            @Suppress("unused")
            abstract fun test3()

            @HttpRpcGET("TEST")
            @Suppress("unused")
            abstract fun test4()
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors).hasSize(3).contains(
            error("test", EndpointType.GET, TestInterface::test2.javaMethod!!),
            error("test", EndpointType.GET, TestInterface::test3.javaMethod!!),
            error("test", EndpointType.GET, TestInterface::test4.javaMethod!!)
        )
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithStaticMethod errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @HttpRpcGET("Getprotocolversion")
            @Suppress("unused")
            abstract fun test()

            @HttpRpcPOST("Getprotocolversion")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors).hasSize(1).contains(error("getprotocolversion", EndpointType.GET,
            TestInterface::test.javaMethod!!))
    }

    @Test
    fun `validate double GET and POST with default path`() {
        @HttpRpcResource
        abstract class TestInterface : RestResource {
            @HttpRpcGET
            @Suppress("unused")
            abstract fun test()

            @HttpRpcGET
            @Suppress("unused")
            abstract fun test2()

            @HttpRpcPOST
            @Suppress("unused")
            abstract fun test3()

            @HttpRpcPOST
            @Suppress("unused")
            abstract fun test4()
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors).hasSize(2).
            contains(
                error("null", EndpointType.GET, TestInterface::test2.javaMethod!!),
                error("null", EndpointType.POST, TestInterface::test4.javaMethod!!))
    }
}