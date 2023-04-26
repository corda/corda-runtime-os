package net.corda.flow.application.services.interop.binding

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import net.corda.flow.application.services.impl.interop.binding.BoundParameter
import net.corda.flow.application.services.impl.interop.binding.FacadeOutParameterBindings
import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.example.TokenReservation
import net.corda.flow.application.services.interop.example.TokensFacade
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.InteropAction
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaMethod

// Binding will fail because there is no method with this name
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

class FacadeMethodBindingSpec : DescribeSpec({

    val facadeV1 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade.json")!!)
    val facadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade_v2.json")!!)
    val bindingV1 = facadeV1.bindTo<TokensFacade>()
    val bindingV2 = facadeV2.bindTo<TokensFacade>()

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV2.assertBindingFails(java, expectedMessage)

    describe("Facade method binding") {
        it("should bind methods to all declared facade versions") {
            bindingV1.bindingFor(TokensFacade::getBalance) should {
                it.shouldNotBeNull()
                it.facadeMethod shouldBe facadeV1.method("get-balance")
            }

            bindingV2.bindingFor(TokensFacade::getBalance) should {
                it.shouldNotBeNull()
                it.facadeMethod shouldBe facadeV2.method("get-balance")
            }

            bindingV1.bindingFor(TokensFacade::reserveTokensV1) should {
                it.shouldNotBeNull()
                it.facadeMethod shouldBe facadeV1.method("reserve-tokens")
            }

            bindingV2.bindingFor(TokensFacade::reserveTokensV2) should {
                it.shouldNotBeNull()
                it.facadeMethod shouldBe facadeV2.method("reserve-tokens")
            }
        }

        it("should not bind methods to any undeclared facade versions") {
            bindingV1.bindingFor(TokensFacade::reserveTokensV2).shouldBeNull()
            bindingV2.bindingFor(TokensFacade::reserveTokensV1).shouldBeNull()
        }

        it("should bind methods with no out parameters") {
            bindingV2.bindingFor(TokensFacade::releaseReservedTokens) should {
                it.shouldNotBeNull()
                it.outParameterBindings shouldBe FacadeOutParameterBindings.NoOutParameters
            }
        }

        it("should bind methods with a single out parameter") {
            val getBalance = facadeV2.method("get-balance")

            bindingV2.bindingFor(TokensFacade::getBalance) should {
                it.shouldNotBeNull()

                (it.outParameterBindings as? FacadeOutParameterBindings.SingletonOutParameterBinding) should {
                    it.shouldNotBeNull()

                    it.outParameter shouldBe getBalance.outParameter<BigDecimal>("balance")
                    it.returnType shouldBe Double::class.javaObjectType
                }
            }
        }

        it("should bind methods with multiple out-parameters to a Kotlin data class") {
            val reserveTokens = facadeV2.method("reserve-tokens")
            val refParameter = reserveTokens.outParameter<UUID>("reservation-ref")
            val expiryParameter = reserveTokens.outParameter<ZonedDateTime>("expiration-timestamp")

            bindingV2.bindingFor(TokensFacade::reserveTokensV2) should {
                it.shouldNotBeNull()

                (it.outParameterBindings as? FacadeOutParameterBindings.DataClassOutParameterBindings) should {
                    it.shouldNotBeNull()

                    it.bindingFor(refParameter) should {
                        it.shouldNotBeNull()

                        it.facadeOutParameter shouldBe refParameter
                        it.constructorParameter shouldBe BoundParameter(0, UUID::class.java)
                        it.readMethod shouldBe TokenReservation::reservationRef.getter.javaMethod
                    }

                    it.bindingFor(expiryParameter) should {
                        it.shouldNotBeNull()

                        it.facadeOutParameter shouldBe expiryParameter
                        it.constructorParameter shouldBe BoundParameter(1, ZonedDateTime::class.java)
                        it.readMethod shouldBe TokenReservation::expires.getter.javaMethod
                    }
                }
            }
        }

        it("should fail if there is no method in the facade with the interface method's name") {
            MethodHasIncorrectName::class shouldFailToBindWith "Bound method get-bananas does not exist in this facade"

            MethodIsAnnotatedWithIncorrectName::class shouldFailToBindWith
                    "Bound method get-bananas does not exist in this facade"
        }

        it("should fail if an interface method has too few parameters") {
            MethodSignatureHasTooFewParameters::class shouldFailToBindWith
                    "Interface method has 0 parameters, but facade method has 1"
        }

        it("should fail if an interface method has too many parameters") {
            MethodSignatureHasTooManyParameters::class shouldFailToBindWith
                "Interface method has 2 parameters, but facade method has 1"
        }

        it("should fail if an interface method has a non InteropAction return type") {
            MethodSignatureHasNonInteropActionReturnType::class shouldFailToBindWith
                "Method return type must be InteropAction<T>"
        }
    }
})