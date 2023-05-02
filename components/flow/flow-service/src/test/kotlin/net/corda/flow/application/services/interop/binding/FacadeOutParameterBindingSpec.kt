package net.corda.flow.application.services.interop.binding

import io.kotest.core.spec.style.DescribeSpec
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.InteropAction
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*
import kotlin.reflect.KClass

// Binding will fail because the facade method has no out-parameters, and we are returning a Long
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodReturnTypeIsIncorrectlyNonVoid {
    @BindsFacadeMethod
    fun releaseReservedTokens(reservationRef: UUID): InteropAction<Long>
}

// Binding will fail because the facade method has an out parameter, and we are returning Void
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodReturnTypeIsIncorrectlyVoid {
    @BindsFacadeMethod
    fun getBalance(denomination: String): InteropAction<Unit>
}

// Binding will fail because the return type should be BigDecimal, Double, Long or Int
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodSignatureHasIncompatibleReturnType {
    @BindsFacadeMethod
    fun getBalance(denomination: String): InteropAction<UUID>
}

// Binding will fail because the facade method has multiple out parameters, and we are returning a primitive type
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodReturnTypeIsIncorrectlyScalar {
    @BindsFacadeMethod
    fun reserveTokens(denomination: String, amount: BigDecimal, ttlMs: Long): InteropAction<UUID>
}

class DataClassMissingGetters(
    @Suppress("UNUSED_PARAMETER") reservationRef: UUID,
    @Suppress("UNUSED_PARAMETER") expirationTimestamp: ZonedDateTime
)

// Binding will fail because the return type is not a well-formed data class (no getters corresponding to constructor args)
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodReturnTypeIsNotDataClass {
    @BindsFacadeMethod
    fun reserveTokens(denomination: String, amount: BigDecimal, ttlMs: Long): InteropAction<DataClassMissingGetters>
}

data class DataClassWithMultipleConstructors(val reservationRef: UUID, val expirationTimestamp: ZonedDateTime) {
    constructor(reservationRefString: String) :
            this(UUID.fromString(reservationRefString), ZonedDateTime.now())
}

// Binding will fail because the return type is not a well-formed data class (no getters corresponding to constructor args)
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodReturnTypeDoesNotHaveUniquePrimaryConstructor {
    @BindsFacadeMethod
    fun reserveTokens(denomination: String, amount: BigDecimal, ttlMs: Long): InteropAction<DataClassWithMultipleConstructors>
}

data class IncompleteTokenReference(val reservationRef: UUID)

// Binding will fail because the returned data class doesn't have properties binding all the out parameters
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodReturnTypeIsDataClassWithTooFewProperties {
    @BindsFacadeMethod
    fun reserveTokens(denomination: String, amount: BigDecimal, ttlMs: Long): InteropAction<IncompleteTokenReference>
}

data class IncorrectlyTypedTokenReference(val reservationRef: Long, val expirationTimestamp: UUID)

// Binding will fail because the properties of the data class have the wrong types.
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodReturnTypeIsDataClassWithIncorrectTypes {
    @BindsFacadeMethod
    fun reserveTokens(
        denomination: String,
        amount: BigDecimal,
        ttlMs: Long
    ): InteropAction<IncorrectlyTypedTokenReference>
}

class FacadeOutParameterBindingSpec : DescribeSpec({

    val facadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV2.assertBindingFails(java, expectedMessage)

    describe("Facade out parameter binding") {

        it("should fail if there are no out-parameters but the return type is non-void") {
            MethodReturnTypeIsIncorrectlyNonVoid::class shouldFailToBindWith
                    "Return type class java.lang.Long is not Void/Unit"
        }

        it("should fail if there are out-parameters but the return type is void") {
            MethodReturnTypeIsIncorrectlyVoid::class shouldFailToBindWith
                    "Return type is not compatible with facade out-parameter type"
        }

        it("should fail if an interface method has an incompatible scalar return type") {
            MethodSignatureHasIncompatibleReturnType::class shouldFailToBindWith
                    "Return type is not compatible with facade out-parameter type"
        }

        it("should fail if an interface method has a scalar return type but there are multiple out parameters") {
            MethodReturnTypeIsIncorrectlyScalar::class shouldFailToBindWith
                    "Out parameters are [reservation-ref, expiration-timestamp], " +
                    "but constructor parameters are []"
        }

        it("should fail if the return type is not a well-formed data class") {
            MethodReturnTypeIsNotDataClass::class shouldFailToBindWith
                    "Cannot find properties with both a constructor parameter and a getter method " +
                    "for out parameters [reservation-ref, expiration-timestamp]"
        }

        it("should fail if the return type does not have a unique primary constructor") {
            MethodReturnTypeDoesNotHaveUniquePrimaryConstructor::class shouldFailToBindWith
                    "Return type does not have a unique constructor"
        }

        it("should fail if the data class return type does not have all expected properties") {
            MethodReturnTypeIsDataClassWithTooFewProperties::class shouldFailToBindWith
                    "Out parameters are [reservation-ref, expiration-timestamp], " +
                    "but constructor parameters are [reservation-ref]"
        }

        it("should fail if a data class property has the wrong type") {
            MethodReturnTypeIsDataClassWithIncorrectTypes::class shouldFailToBindWith
                    "Constructor parameter reservationRef(#0) of type long does not match type of " +
                    "facade out parameter TypedParameter(name=reservation-ref, type=uuid)"
        }
    }
})