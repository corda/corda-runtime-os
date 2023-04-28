package net.corda.flow.application.services.interop.facade

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.impl.interop.parameters.TypeParameters
import net.corda.flow.application.services.impl.interop.parameters.TypedParameterImpl
import net.corda.v5.application.interop.facade.FacadeId
import net.corda.v5.application.interop.facade.FacadeMethodType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import java.math.BigDecimal

class FacadeSpec : DescribeSpec({

    describe("A facade") {

        val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade.json")!!)
        val getBalance = facade.method("get-balance")
        val denomination = getBalance.inParameter("denomination", String::class.java)
        val balance = getBalance.outParameter("balance", BigDecimal::class.java)

        it("can be configured through YAML") {
            facade.facadeId shouldBe FacadeId.of("org.corda.interop/platform/tokens/v1.0")

            getBalance should {
                it.facadeId shouldBe facade.facadeId
                it.name shouldBe "get-balance"
                it.type shouldBe FacadeMethodType.QUERY
            }

            denomination shouldBe TypedParameterImpl(
                "denomination",
                TypeParameters<Any>().of("string (org.corda.interop/platform/tokens/types/denomination/1.0) ")
            )

            balance shouldBe TypedParameterImpl(
                "balance",
                TypeParameters<Any>().of(ParameterTypeLabel.DECIMAL.name)
            )
        }

        it("can contain methods with no parameters") {
            @Suppress("NAME_SHADOWING") val facade = FacadeReaders.JSON.read("""
                { "id": "org.corda.interop/platform/serialisation/v1.0",
                  "commands": { "dummy": {} }
                }
            """.trimIndent())
                val method = facade.method("dummy")
                method.inParameters shouldBe listOf()
                method.outParameters shouldBe listOf()
        }

        it("can create a facade request") {
            val request = getBalance.request(denomination.of("USD"))

            request should {
                it.facadeId shouldBe facade.facadeId
                it.methodName shouldBe "get-balance"
                it[denomination] shouldBe "USD"
            }

            val mapper = ObjectMapper().registerKotlinModule().configure(SerializationFeature.INDENT_OUTPUT, true)
            println(mapper.writeValueAsString(request))
        }

        it("can create a facade response") {
            val response = getBalance.response(balance.of(BigDecimal("100.00")))

            response.should {
                it.facadeId shouldBe facade.facadeId
                it.methodName shouldBe "get-balance"
                it[balance] shouldBe BigDecimal("100.00")
            }
        }

        it("enforces type discipline for in parameters") {
            shouldThrow<IllegalArgumentException> {
                getBalance.inParameter("denomination", BigDecimal::class.java)
            }.message shouldBe "Parameter denomination is of type class java.lang.String, not class java.math.BigDecimal"
        }

        it("enforces type discipline for out parameters") {
            shouldThrow<IllegalArgumentException> {
                getBalance.outParameter("balance", String::class.java)
            }.message shouldBe "Parameter balance is of type class java.math.BigDecimal, not class java.lang.String"
        }

        it("throws exception for unknown parameter") {
            shouldThrow<IllegalArgumentException> {
                getBalance.inParameter("nonExistingParameter", BigDecimal::class.java)
            }
            shouldThrow<IllegalArgumentException> {
                getBalance.outParameter("nonExistingParameter", BigDecimal::class.java)
            }
        }

        it("throws exception for unknown command ") {
            shouldThrow<IllegalArgumentException> {
                facade.method("nonExisting")
            }
        }
    }

})