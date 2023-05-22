package net.corda.flow.application.services.interop.binding

import net.corda.flow.application.services.impl.interop.binding.BoundParameter
import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.example.TokensFacade
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.BindsFacadeParameter
import net.corda.v5.application.interop.binding.InteropAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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

class FacadeInParameterBindingSpec {
    val facadeV2 =
        FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)
    val bindingV2 = facadeV2.bindTo<TokensFacade>()

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV2.assertBindingFails2(java, expectedMessage)

    @Test
    fun `should bind all in-parameters in the example Tokens facade`() {
        val reserveTokensV2 = facadeV2.method("reserve-tokens")

        assertNotNull(bindingV2.bindingFor(TokensFacade::reserveTokensV2))
        if (bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(0) != null) {
            assertEquals(
                BoundParameter(0, String::class.java),
                bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(0)!!.boundParameter
            )
            assertEquals(
                reserveTokensV2.inParameter("denomination", String::class.java),
                bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(0)!!.facadeParameter
            )
        }

        if (bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(1) != null) {
            assertEquals(
                BoundParameter(1, BigDecimal::class.java),
                bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(1)!!.boundParameter
            )
            assertEquals(
                reserveTokensV2.inParameter("amount", BigDecimal::class.java),
                bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(1)!!.facadeParameter
            )
        }

        if (bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(2) != null) {
            assertEquals(
                BoundParameter(2, Long::class.java),
                bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(2)!!.boundParameter
            )
            assertEquals(
                reserveTokensV2.inParameter("ttl-ms", BigDecimal::class.java),
                bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.bindingForMethodParameter(2)!!.facadeParameter
            )
        }
    }

    @Test
    fun `should fail if an interface method parameter has the wrong type`() {
        ParameterHasIncorrectType::class shouldFailToBindWith
                "Type of parameter is not compatible with facade in-parameter type"
    }

    @Test
    fun `should fail if an interface method parameter has the wrong name`() {
        ParameterHasIncorrectType::class shouldFailToBindWith
                "Type of parameter is not compatible with facade in-parameter type"

        ParameterIsAnnotatedWithIncorrectName::class shouldFailToBindWith
                "There is no input parameter named currency-name in this facade method"
    }
}