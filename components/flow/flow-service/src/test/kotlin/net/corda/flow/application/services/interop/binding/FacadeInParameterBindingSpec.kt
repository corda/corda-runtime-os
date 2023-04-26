package net.corda.flow.application.services.interop.binding

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.binding.BoundParameter
import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.example.TokensFacade
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.BindsFacadeParameter
import net.corda.v5.application.interop.binding.InteropAction
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass

// Binding will fail because denomination should be a String
@BindsFacade("org.corda.interop/platform/tokens")
interface ParameterHasIncorrectType {
    @BindsFacadeMethod
    fun getBalance(denomination: Long): InteropAction<Long>
}

// Binding will fail because currencyName is not the name of the parameter
@BindsFacade("org.corda.interop/platform/tokens")
interface ParameterHasIncorrectName {

    @BindsFacadeMethod
    fun getBalance(currencyName: String): InteropAction<Long>
}

// Binding will fail because the annotation gives an incorrect alias to the parameter
@BindsFacade("org.corda.interop/platform/tokens")
interface ParameterIsAnnotatedWithIncorrectName {

    @BindsFacadeMethod
    fun getBalance(@BindsFacadeParameter("currency-name") denomination: String): InteropAction<Long>
}

class FacadeInParameterBindingSpec : DescribeSpec({

    val facadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade_v2.json")!!)
    val bindingV2 = facadeV2.bindTo<TokensFacade>()

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV2.assertBindingFails(java, expectedMessage)

    describe("Facade in-parameter binding") {
        it("should bind all in-parameters in the example Tokens facade") {
            val reserveTokensV2 = facadeV2.method("reserve-tokens")

            bindingV2.bindingFor(TokensFacade::reserveTokensV2) should {
                it.shouldNotBeNull()
                it.bindingForMethodParameter(0) should {
                    it.shouldNotBeNull()
                    it.boundParameter shouldBe BoundParameter(0, String::class.java)
                    it.facadeParameter shouldBe reserveTokensV2.inParameter<String>("denomination")
                }

                it.bindingForMethodParameter(1) should {
                    it.shouldNotBeNull()
                    it.boundParameter shouldBe BoundParameter(1, BigDecimal::class.java)
                    it.facadeParameter shouldBe reserveTokensV2.inParameter<BigDecimal>("amount")
                }

                it.bindingForMethodParameter(2) should {
                    it.shouldNotBeNull()

                    it.boundParameter shouldBe BoundParameter(2, Long::class.java)
                    it.facadeParameter shouldBe reserveTokensV2.inParameter<BigDecimal>("ttl-ms")
                }
            }
        }

        it("should fail if an interface method parameter has the wrong type") {
            ParameterHasIncorrectType::class shouldFailToBindWith
                    "Type of parameter is not compatible with facade in-parameter type"
        }

        it("should fail if an interface method parameter has the wrong name") {
            ParameterHasIncorrectName::class shouldFailToBindWith
                    "There is no input parameter named currency-name in this facade method"

            ParameterIsAnnotatedWithIncorrectName::class shouldFailToBindWith
                    "There is no input parameter named currency-name in this facade method"
        }
    }

})