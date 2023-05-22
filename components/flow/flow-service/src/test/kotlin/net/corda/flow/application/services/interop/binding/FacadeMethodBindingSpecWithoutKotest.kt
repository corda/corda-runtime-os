package net.corda.flow.application.services.interop.binding

import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.example.TokensFacade
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.InteropAction
import org.junit.jupiter.api.Test
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
    val facadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)
    val bindingV1 = facadeV1.bindTo<TokensFacade>()
    val bindingV2 = facadeV2.bindTo<TokensFacade>()

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV2.assertBindingFails2(java, expectedMessage)

}