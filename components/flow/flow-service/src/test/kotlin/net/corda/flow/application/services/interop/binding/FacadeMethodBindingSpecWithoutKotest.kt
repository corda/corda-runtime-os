package net.corda.flow.application.services.interop.binding

import net.corda.flow.application.services.impl.interop.binding.FacadeOutParameterBindings
import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.example.TokensFacade
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.InteropAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass

@BindsFacade("org.corda.interop/platform/tokens")
interface MethodHasIncorrectName {
    @BindsFacadeMethod
    fun getBananas(): InteropAction<Long>
}

// Binding will fail because there is no method with the name given in the annotation
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodIsAnnotatedWithIncorrectName {
    @BindsFacadeMethod("get-bananas")
    fun getBalance(): InteropAction<Long>
}

// Binding will fail because the method has too few parameters
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodSignatureHasTooFewParameters {
    @BindsFacadeMethod
    fun getBalance(): InteropAction<Long>
}

// Binding will fail because the method has too many parameters
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodSignatureHasTooManyParameters {
    @BindsFacadeMethod
    fun getBalance(denomination: String, superogatoryParameter: UUID): InteropAction<Long>
}

// Binding will fail because the return type must be wrapped with InteropAction
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodSignatureHasNonInteropActionReturnType {
    @BindsFacadeMethod
    fun getBalance(denomination: String): UUID
}

class FacadeMethodBindingSpecWithoutKotest {
    val facadeV1 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade.json")!!)
    val facadeV2 =
        FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)
    val bindingV1 = facadeV1.bindTo<TokensFacade>()
    val bindingV2 = facadeV2.bindTo<TokensFacade>()

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV2.assertBindingFails2(java, expectedMessage)

    @Test
    fun `should bind methods to all declared facade versions`() {
        assertNotNull(bindingV1.bindingFor(TokensFacade::getBalance))
        assertEquals(facadeV1.method("get-balance"), bindingV1.bindingFor(TokensFacade::getBalance)!!.facadeMethod)

        assertNotNull(bindingV2.bindingFor(TokensFacade::getBalance))
        assertEquals(facadeV2.method("get-balance"), bindingV2.bindingFor(TokensFacade::getBalance)!!.facadeMethod)

        assertNotNull(bindingV1.bindingFor(TokensFacade::reserveTokensV1))
        assertEquals(
            facadeV1.method("reserve-tokens"),
            bindingV1.bindingFor(TokensFacade::reserveTokensV1)!!.facadeMethod
        )

        assertNotNull(bindingV2.bindingFor(TokensFacade::reserveTokensV2))
        assertEquals(
            facadeV2.method("reserve-tokens"),
            bindingV2.bindingFor(TokensFacade::reserveTokensV2)!!.facadeMethod
        )
    }

    @Test
    fun `should not bind methods to any undeclared facade versions`() {
        assertNull(bindingV1.bindingFor(TokensFacade::reserveTokensV2))
        assertNull(bindingV2.bindingFor(TokensFacade::reserveTokensV1))
    }

    @Test
    fun `should bind methods with no out parameters`() {
        assertNotNull(bindingV2.bindingFor(TokensFacade::releaseReservedTokens))
        assertEquals(
            FacadeOutParameterBindings.NoOutParameters,
            bindingV2.bindingFor(TokensFacade::releaseReservedTokens)!!.outParameterBindings
        )
    }

    @Test
    fun `should bind methods with a single out parameter`() {
        val getBalance = facadeV2.method("get-balance")
        assertNotNull(bindingV2.bindingFor(TokensFacade::getBalance))
        assertNotNull(bindingV2.bindingFor(TokensFacade::getBalance)!!.outParameterBindings
                as? FacadeOutParameterBindings.SingletonOutParameterBinding)
        assertEquals(
            getBalance.outParameter("balance", BigDecimal::class.java),
            (bindingV2.bindingFor(TokensFacade::getBalance)!!.outParameterBindings
                    as? FacadeOutParameterBindings.SingletonOutParameterBinding)!!.outParameter
        )
        assertEquals(
            Double::class.javaObjectType,
            (bindingV2.bindingFor(TokensFacade::getBalance)!!.outParameterBindings
                    as? FacadeOutParameterBindings.SingletonOutParameterBinding)!!.returnType
        )
    }
}