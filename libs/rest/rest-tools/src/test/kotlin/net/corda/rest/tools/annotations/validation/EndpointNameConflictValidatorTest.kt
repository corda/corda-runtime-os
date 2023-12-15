package net.corda.rest.tools.annotations.validation

import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpGET
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.HttpRestResource
import net.corda.rest.annotations.RestApiVersion
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.tools.annotations.validation.EndpointNameConflictValidator.Companion.error
import net.corda.rest.tools.annotations.validation.utils.EndpointType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaMethod

class EndpointNameConflictValidatorTest {

    @Test
    fun `validate withEndpointNameConflictOnSamePathDifferentVersions errorListIsEmpty`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("/test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_0)
            @Suppress("unused")
            abstract fun test()

            @HttpGET("/test", minVersion = RestApiVersion.C5_1, maxVersion = RestApiVersion.C5_2)
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathDefaultVersions errorListContainsError`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("/test")
            @Suppress("unused")
            abstract fun test()

            @HttpGET("/test")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        val expectedError = error("/test", EndpointType.GET, TestInterface::test2.javaMethod!!, TestInterface::test.javaMethod!!)
        assertEquals(listOf(expectedError), result.errors)
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathOverlappingVersions errorListContainsError`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("/test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_1)
            @Suppress("unused")
            abstract fun test()

            @HttpGET("/test", minVersion = RestApiVersion.C5_1, maxVersion = RestApiVersion.C5_2)
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        val expectedError = error("/test", EndpointType.GET, TestInterface::test2.javaMethod!!, TestInterface::test.javaMethod!!)
        assertEquals(listOf(expectedError), result.errors)
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathMultipleConflicts errorListContainsErrors`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("/test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_0)
            @Suppress("unused")
            abstract fun test()

            @HttpGET("/test", minVersion = RestApiVersion.C5_2, maxVersion = RestApiVersion.C5_2)
            @Suppress("unused")
            abstract fun test2()

            @HttpGET("/test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_2)
            @Suppress("unused")
            abstract fun test3()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        val expectedErrors = listOf(
            error("/test", EndpointType.GET, TestInterface::test3.javaMethod!!, TestInterface::test.javaMethod!!),
            error("/test", EndpointType.GET, TestInterface::test3.javaMethod!!, TestInterface::test2.javaMethod!!)
        )
        assertThat(result.errors).hasSize(2).allMatch { it in expectedErrors }
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathContainedVersions errorListContainsError`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("/test", minVersion = RestApiVersion.C5_0, maxVersion = RestApiVersion.C5_2)
            @Suppress("unused")
            abstract fun test()

            @HttpGET("/test", minVersion = RestApiVersion.C5_1, maxVersion = RestApiVersion.C5_1)
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        val expectedError = error("/test", EndpointType.GET, TestInterface::test2.javaMethod!!, TestInterface::test.javaMethod!!)
        assertEquals(listOf(expectedError), result.errors)
    }

    @Test
    fun `validate withEndpointNameConflictOnDifferentMethodType errorListIsEmpty`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("/test")
            @Suppress("unused")
            abstract fun test()

            @HttpPOST("/test")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun `validate withEndpointNameConflictOnMissingEndpointAnnotation errorListIsEmpty`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @Suppress("unused")
            abstract fun test()

            @HttpPOST("/test")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun `validate withEndpointNameConflictOnOverload errorListContainsError`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET(path = "test")
            @Suppress("unused")
            abstract fun test()

            @HttpGET(path = "test")
            @Suppress("unused")
            abstract fun test(@RestQueryParameter foo: String)

            @HttpGET(path = "test")
            @Suppress("unused")
            abstract fun test(@RestQueryParameter foo: Int, @RestQueryParameter bar: String = "")
        }

        val result = EndpointNameConflictValidator(TestInterface::class.java).validate()

        assertThat(result.errors).hasSize(2).allMatch { error ->
            error == error(
                "test", EndpointType.GET, TestInterface::class.java.methods.first { it.name == "test" },
                TestInterface::class.java.methods.last { it.name == "test" }
            )
        }
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithDefaultName errorListContainsError`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET
            @Suppress("unused")
            abstract fun test()

            @HttpGET("")
            abstract fun test2()
        }

        val result = RestInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors)
            .hasSize(1)
            .contains(
                error("null", EndpointType.GET, TestInterface::test2.javaMethod!!, TestInterface::test.javaMethod!!)
            )
    }

    @Test
    fun `validate withEndpointNameConflictWithCapitalization errorListContainsError`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("teSt")
            @Suppress("unused")
            abstract fun teSt()

            @HttpGET("tEst")
            @Suppress("unused")
            abstract fun test2()

            @HttpGET("test")
            @Suppress("unused")
            abstract fun test3()

            @HttpGET("TEST")
            @Suppress("unused")
            abstract fun test4()
        }

        val result = RestInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors).hasSize(3).contains(
            error("test", EndpointType.GET, TestInterface::test2.javaMethod!!, TestInterface::teSt.javaMethod!!),
            error("test", EndpointType.GET, TestInterface::test3.javaMethod!!, TestInterface::teSt.javaMethod!!),
            error("test", EndpointType.GET, TestInterface::test4.javaMethod!!, TestInterface::teSt.javaMethod!!)
        )
    }

    @Test
    fun `validate withEndpointNameConflictOnSamePathWithStaticMethod errorListContainsError`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET("Getprotocolversion")
            @Suppress("unused")
            abstract fun test()

            @HttpPOST("Getprotocolversion")
            @Suppress("unused")
            abstract fun test2()
        }

        val result = RestInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors).hasSize(1).contains(
            error(
                "getprotocolversion",
                EndpointType.GET,
                TestInterface::test.javaMethod!!,
                RestResource::protocolVersion.javaGetter!!
            )
        )
    }

    @Test
    fun `validate double GET and POST with default path`() {
        @HttpRestResource
        abstract class TestInterface : RestResource {
            @HttpGET
            @Suppress("unused")
            abstract fun test()

            @HttpGET
            @Suppress("unused")
            abstract fun test2()

            @HttpPOST
            @Suppress("unused")
            abstract fun test3()

            @HttpPOST
            @Suppress("unused")
            abstract fun test4()
        }

        val result = RestInterfaceValidator.validate(TestInterface::class.java)

        assertThat(result.errors).hasSize(2)
            .contains(
                error("null", EndpointType.GET, TestInterface::test2.javaMethod!!, TestInterface::test.javaMethod!!),
                error("null", EndpointType.POST, TestInterface::test4.javaMethod!!, TestInterface::test3.javaMethod!!)
            )
    }
}
