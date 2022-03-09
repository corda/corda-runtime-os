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
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET("/test")
            fun test() {
            }

            @HttpRpcGET("/test")
            fun test2() {
            }
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictOnDifferentMethodType errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET("/test")
            fun test() {
            }

            @HttpRpcPOST("/test")
            fun test2() {
            }
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictOnMissingEndpointAnnotation errorListIsEmpty`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            fun test() {}

            @HttpRpcPOST("/test")
            fun test2() {
            }
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(0, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictOnOverload errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET(path = "test")
            fun test() {
            }

            @HttpRpcGET(path = "test")
            fun test(@HttpRpcQueryParameter foo: String) {
                foo.toLowerCase()
            }

            @HttpRpcGET(path = "test")
            fun test(@HttpRpcQueryParameter foo: Int, @HttpRpcQueryParameter bar: String = "") {
                foo + 1
                bar.toLowerCase()
            }
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(2, result.errors.size)
        assertThat(result.errors).allMatch { it.equals("Duplicate endpoint path 'test' for GET method.") }
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithDefaultName errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET
            fun test() {
            }

            @HttpRpcGET("test")
            fun test2() {
            }
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(1, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictWithCapitalization errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1
            
            @HttpRpcGET
            fun teSt() {
            }

            @HttpRpcGET("tEst")
            fun test2() {
            }

            @HttpRpcGET("test")
            fun test3() {
            }

            @HttpRpcGET("TEST")
            fun test4() {
            }
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(3, result.errors.size)
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithStaticMethod errorListContainsError`() {
        @HttpRpcResource
        class TestInterface : RpcOps {
            override val protocolVersion: Int
                get() = 1

            @HttpRpcGET("Getprotocolversion")
            fun test() {
            }

            @HttpRpcPOST("Getprotocolversion")
            fun test2() {
            }
        }

        val result = HttpRpcInterfaceValidator.validate(TestInterface::class.java)

        assertEquals(1, result.errors.size)
        assert(result.errors.single().contains(EndpointNameConflictValidator.error("getprotocolversion", EndpointType.GET)))
    }
}