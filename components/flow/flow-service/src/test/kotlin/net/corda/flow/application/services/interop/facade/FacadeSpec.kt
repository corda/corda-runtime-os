package net.corda.flow.application.services.interop.facade

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.parameters.TypeParameters
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterImpl
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeMethod
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class FacadeSpec {
    val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade.json")!!)
    val getBalance = facade.method("get-balance")
    val denomination = getBalance.inParameter("denomination", String::class.java)
    val balance = getBalance.outParameter("balance", BigDecimal::class.java)

    @Test
    fun `facade can be configured through YAML`() {
        assertEquals(facade.facadeId, FacadeId.of("org.corda.interop/platform/tokens/v1.0"))

        assertEquals(getBalance.facadeId, facade.facadeId)
        assertEquals(getBalance.name, "get-balance")
        assertEquals(getBalance.type, FacadeMethod.FacadeMethodType.QUERY)

        assertEquals(
            denomination, TypedParameterImpl(
                "denomination",
                TypeParameters.of("string (org.corda.interop/platform/tokens/types/denomination/1.0) ")
            )
        )

        assertEquals(
            balance, TypedParameterImpl(
                "balance",
                TypeParameters.of(ParameterTypeLabel.DECIMAL.name)
            )
        )
    }

    @Test
    fun `can contain methods with no parameters`() {
        val facade = FacadeReaders.JSON.read(
            """
                { "id": "org.corda.interop/platform/serialisation/v1.0",
                  "commands": { "dummy": {} }
                }
            """.trimIndent()
        )
        val method = facade.method("dummy")
        assertTrue(method.inParameters.isEmpty())
        assertTrue(method.outParameters.isEmpty())
    }

    @Test
    fun `can create a facade request`() {
        val request = getBalance.request(denomination.of("USD"))
        assertEquals(request.facadeId, facade.facadeId)
        assertEquals(request.methodName, "get-balance")
        assertEquals(request[denomination], "USD")

        val mapper = ObjectMapper().registerKotlinModule().configure(SerializationFeature.INDENT_OUTPUT, true)
        println(mapper.writeValueAsString(request))
    }

    @Test
    fun `can create a facade response`() {
        val response = getBalance.response(balance.of(BigDecimal("100.00")))
        assertEquals(response.facadeId, facade.facadeId)
        assertEquals(response.methodName, "get-balance")
        assertEquals(response[balance], BigDecimal("100.00"))
    }

    @Test
    fun `enforces type discipline for in parameters`() {
        assertThrows<IllegalArgumentException> {
            getBalance.inParameter("denomination", BigDecimal::class.java)
        }.message.equals("Parameter denomination is of type class java.lang.String, not class java.math.BigDecimal")
    }

    @Test
    fun `enforces type discipline for out parameters`() {
        assertThrows<IllegalArgumentException> {
            getBalance.outParameter("balance", String::class.java)
        }.message.equals("Parameter balance is of type class java.math.BigDecimal, not class java.lang.String")
    }

    @Test
    fun `throws exception for unknown parameter`() {
        assertThrows<IllegalArgumentException> {
            getBalance.inParameter("nonExistingParameter", BigDecimal::class.java)
        }
        assertThrows<IllegalArgumentException> {
            getBalance.outParameter("nonExistingParameter", BigDecimal::class.java)
        }
    }

    @Test
    fun `throws exception for unknown command`() {
        assertThrows<IllegalArgumentException> {
            facade.method("nonExisting")
        }
    }
}