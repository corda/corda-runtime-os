package net.corda.flow.application.services.interop.binding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.binding.internal.FacadeInterfaceBindingException
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.interop.binding.QualifiedWith
import java.util.*
import kotlin.reflect.KClass

// Binding will fail because we specify that we accept denomination v2, but the facade declares it as v1
@BindsFacade("org.corda.interop/platform/tokens")
interface MethodSignatureHasWronglyQualifiedParameter {
    @BindsFacadeMethod
    fun getBalance(
        @QualifiedWith("org.corda.interop/platform/tokens/denomination/v2.0") denomination: String): InteropAction<Long>
}

class UnqualifiedFruit
class UnqualifiedPastry

class MismatchedConstructorAndGetterTypes(name: String, val ingredients: IngredientsV1) {
    fun getName(): UUID = UUID.randomUUID()
}

@QualifiedWith("org.corda.test/test/ingredients/v1.0")
data class IngredientsV1(val ingredients: List<String>)

data class CorrectlyQualifiedPudding(val name: String, val ingredients: IngredientsV1)

@QualifiedWith("org.corda.test/test/ingredients/v2.0")
data class IngredientsV2(val ingredients: List<String>)

data class IncorrectlyQualifiedPudding(val name: String, val ingredients: IngredientsV2)
data class InconsistentlyQualifiedPudding(
    val name: String,
    @QualifiedWith("org.corda.test/test/ingredients/v2.0") // mismatch with type qualifier
    val ingredients: IngredientsV1
)

@QualifiedWith("org.corda.test/test/fruit/v1.0")
class FruitV1

@QualifiedWith("org.corda.test/test/pastry/v2.0")
class PastryV2


@BindsFacade("org.corda.interop/test/qualification")
interface MismatchedParameterQualifier {

    /*
    This should be refused because parameter qualifiers must be a subset of type qualifiers (if these are stated).

    It's possible to have conflicting @QualifiedWith annotations in two places, on the type of the parameter and on the
    parameter itself. The rule is that annotation should always _narrow_, not extend, the accepted types.
     */
    @BindsFacadeMethod
    fun makePie(
        @QualifiedWith("org.corda.test/test/fruit/v2.0") fruit: FruitV1,
        pastry: UnqualifiedPastry
    ): InteropAction<CorrectlyQualifiedPudding>

}

@BindsFacade("org.corda.interop/test/qualification")
interface MismatchedTypeQualifier {

    // This should be refused because the type qualifier on FruitV2 doesn't match the expected qualifier
    @BindsFacadeMethod
    fun makePie(fruit: UnqualifiedFruit, pastry: PastryV2): InteropAction<CorrectlyQualifiedPudding>
}

@BindsFacade("org.corda.interop/test/qualification")
interface MismatchedTypeQualifierInOutput {

    // This should be refused because the type qualifier on the ingredients property of IncorrectlyQualifiedPudding
    // does not match that of the corresponding out parameter
    @BindsFacadeMethod
    fun makePie(fruit: UnqualifiedFruit, pastry: UnqualifiedPastry): InteropAction<IncorrectlyQualifiedPudding>
}

@BindsFacade("org.corda.interop/test/qualification")
interface InconsistentlyQualifiedOutParameter {

    // This should be refused because there are inconsistent annotations on the parameter and type of
    // InconsistentlyQualifiedPudding::ingredients
    @BindsFacadeMethod
    fun makePie(fruit: UnqualifiedFruit, pastry: UnqualifiedPastry): InteropAction<InconsistentlyQualifiedPudding>
}

@BindsFacade("org.corda.interop/test/qualification")
interface IllFormedDataClassOutput {

    // This should be refused because the data class used for the return type is ill=formed - the getter has a
    // different return type to the corresponding constructor parameter
    @BindsFacadeMethod
    fun makePie(fruit: UnqualifiedFruit, pastry: UnqualifiedPastry): InteropAction<MismatchedConstructorAndGetterTypes>
}

class TypeQualificationSpec : DescribeSpec({

    val facade = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/qualification-test-facade.json")!!)

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facade.assertBindingFails(java, expectedMessage)

    describe("In-parameter binding") {
        val tokensFacadeV2 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/tokens-facade_v2.json")!!)

        it("should fail if a primitive parameter type with a qualifier does not match the facade qualifier") {
            shouldThrow<FacadeInterfaceBindingException> {
                tokensFacadeV2.bindTo<MethodSignatureHasWronglyQualifiedParameter>()
            }.message shouldContain "Type of parameter is not compatible with facade in-parameter type"
        }

        it("should fail if a parameter with a qualified JSON type is incorrectly qualified") {
            MismatchedParameterQualifier::class shouldFailToBindWith
                    "has a type with qualifiers [org.corda.test/test/fruit/v1.0], " +
                    "but is annotated with qualifiers [org.corda.test/test/fruit/v2.0]"
        }

        it("should fail if a parameter's type is qualified with a qualifier that doesn't match the facade type") {
            MismatchedTypeQualifier::class shouldFailToBindWith
                    "Type of parameter is not compatible with facade in-parameter type"
        }

        it("should fall if a property of a data class mapped to an out parameter has a mismatched qualifier") {
            MismatchedTypeQualifierInOutput::class shouldFailToBindWith
                    "does not match type of facade out parameter"
        }

        it("should fail if an out parameter is inconsistently qualified") {
            InconsistentlyQualifiedOutParameter::class shouldFailToBindWith "Parameter qualification mismatch"
        }

        it("should fail if a data class output type has mismatched constructor and getter types") {
            IllFormedDataClassOutput::class shouldFailToBindWith "Type mismatch"
        }
    }
})