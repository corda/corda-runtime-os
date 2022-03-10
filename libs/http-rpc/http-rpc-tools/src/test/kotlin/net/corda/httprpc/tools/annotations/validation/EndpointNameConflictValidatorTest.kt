package net.corda.httprpc.tools.annotations.validation

import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcResource
import net.corda.httprpc.tools.annotations.validation.utils.EndpointType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EndpointNameConflictValidatorTest {

    @Test
    fun `validate withEndpointNameConflictOnSamePath errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RpcOps {
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
        abstract class TestInterface : RpcOps {
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
        abstract class TestInterface : RpcOps {
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
        abstract class TestInterface : RpcOps {
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

        assertEquals(2, result.errors.size)
        assertThat(result.errors).allMatch { it.equals("Duplicate endpoint path 'test' for GET method.") }
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithDefaultName errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RpcOps {
            @HttpRpcGET
            @Suppress("unused")
            abstract fun test()

            @HttpRpcGET("")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(1, result.errors.size)
        assertThat(result.errors).allMatch { it == "Duplicate endpoint path 'null' for GET method." }
    }

    @Test
    fun `validate withEndpointNameConflictWithCapitalization errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RpcOps {
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

        assertEquals(3, result.errors.size)
        assertThat(result.errors).allMatch { it == "Duplicate endpoint path 'test' for GET method." }
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithStaticMethod errorListContainsError`() {
        @HttpRpcResource
        abstract class TestInterface : RpcOps {
            @HttpRpcGET("Getprotocolversion")
            @Suppress("unused")
            abstract fun test()

            @HttpRpcPOST("Getprotocolversion")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(1, result.errors.size)
        assert(result.errors.single().contains(EndpointNameConflictValidator.error("getprotocolversion", EndpointType.GET)))
    }
}