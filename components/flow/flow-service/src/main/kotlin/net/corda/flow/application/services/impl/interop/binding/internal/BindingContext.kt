package net.corda.flow.application.services.impl.interop.binding.internal

import net.corda.flow.application.services.impl.interop.binding.BoundParameter
import net.corda.flow.application.services.impl.interop.binding.DataClassPropertyBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeInParameterBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeInterfaceBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeMethodBinding
import net.corda.flow.application.services.impl.interop.binding.FacadeOutParameterBindings
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.BindsFacadeMethod
import net.corda.v5.application.interop.binding.BindsFacadeParameter
import net.corda.v5.application.interop.binding.FacadeVersions
import net.corda.v5.application.interop.binding.InteropAction
import net.corda.v5.application.interop.binding.QualifiedWith
import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeMethod
import net.corda.v5.application.interop.parameters.ParameterType
import net.corda.v5.application.interop.parameters.ParameterTypeLabel
import net.corda.v5.application.interop.parameters.TypeQualifier
import net.corda.v5.application.interop.parameters.TypedParameter
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.util.*
import java.util.UUID

class FacadeInterfaceBindingException(message: String) : RuntimeException(message)

/**
 * A [BindingContext] holds the context of an attempt to bind some part of an interface (class, method, parameters)
 * to some part of a facade, and provides some utility functions for checking the validity of the binding and raising
 * informative [FacadeInterfaceBindingException]s if a valid binding cannot be created.
 *
 * These are temporary objects which create a "working context" for analysis of the interface and facade that are being
 * bound together - the application developer should never need to use anything here directly.
 * @param T The type of binding this context can produce
 */
internal abstract class BindingContext<T> {

    /**
     * Create a binding of type [T].
     */
    abstract fun createBinding(): T

    /**
     * Test that a boolean condition is met, and throw a [FacadeInterfaceBindingException] with a message describing the
     * current binding context if it is not.
     * @param passed Whether the condition was met or not.
     * @param reason Function that will generate the "reason" part of the error message.
     */
    protected fun test(passed: Boolean, reason: () -> String) {
        if (!passed) throw FacadeInterfaceBindingException("[$this] ${reason()}")
    }

    /**
     * Extension function for any type of value that may be null. Will either throw a [FacadeInterfaceBindingException]
     * if the value is null, or return the non-null value if it is not.
     * @param reason Function that will generate the "reason" part of the error message, if the value is null.
     */
    protected fun <T : Any> T?.orFail(reason: () -> String): T =
        this ?: throw FacadeInterfaceBindingException("[${this@BindingContext}] ${reason()}")

}

/**
 * [BindingContext] for binding a JVM interface to a [Facade] - the entry-point for interface/facade binding.
 * @param facade the [Facade] to be bound.
 * @param boundInterface The JVM interface to be bound.
 */
internal class InterfaceBindingContext(val facade: Facade, private val boundInterface: Class<*>) :
    BindingContext<FacadeInterfaceBinding>() {

    override fun createBinding(): FacadeInterfaceBinding {
        // The interface must be annotated with @BindsFacade
        val boundFacadeName = boundInterface.readAnnotation<BindsFacade>().orFail {
            "Interface is not annotated with @BindsFacade"
        }.value

        // The value of the @BindsFacade annotation must be equal to the facade's unversioned name.
        test(facade.facadeId.unversionedName == boundFacadeName) {
            "Mismatch: interface's @BindsFacade annotation declares that it is bound to $boundFacadeName"
        }

        val defaultBoundVersions = boundInterface.readAnnotation<FacadeVersions>()?.value?.toSet() ?: emptySet()

        test(defaultBoundVersions.isEmpty() || facade.facadeId.version in defaultBoundVersions) {
            "Mismatch: interface explicitly declares binding to versions $defaultBoundVersions of " +
                    "${facade.facadeId.unversionedName}, but facade has version ${facade.facadeId.version}"
        }

        val boundMethods = boundInterface.declaredMethods.mapNotNull { method ->
            getMethodBinding(method, defaultBoundVersions)
        }

        return FacadeInterfaceBinding(
            facade,
            boundInterface,
            boundMethods
        )
    }

    private fun getMethodBinding(method: Method, defaultBoundVersions: Set<String>):
            FacadeMethodBinding? {
        // Ignore methods that are not annotated with @BindsFacadeMethod.
        val annotation = method.readAnnotation<BindsFacadeMethod>() ?: return null

        // If the name is not given in the value parameter of the annotation, translate the method name to kebab-case.
        val facadeMethodName = annotation.value.ifEmpty { method.name.kebabCase }

        val versions = method.readAnnotation<FacadeVersions>()?.value?.toSet() ?: defaultBoundVersions

        // Ignore methods that do not bind the current facade's version.
        if (versions.isNotEmpty() && facade.facadeId.version !in versions) return null

        // The bound facade must contain a method matching this name.
        val facadeMethod = facade.methodsByName[facadeMethodName].orFail {
            "Bound method $facadeMethodName does not exist in this facade"
        }

        return MethodBindingContext(this, facadeMethod, method).createBinding()
    }

    override fun toString(): String =
        "Binding facade ${facade.facadeId} to interface ${boundInterface.name}"
}

// BindingContext in which we are binding an interface method to a facade method.
private class MethodBindingContext(
    val parent: InterfaceBindingContext,
    val facadeMethod: FacadeMethod,
    val boundMethod: Method
) : BindingContext<FacadeMethodBinding>() {

    override fun createBinding(): FacadeMethodBinding = FacadeMethodBinding(
        facadeMethod,
        boundMethod,
        createInParameterBindings(),
        createOutParameterBindings()
    )

    private fun createInParameterBindings(): List<FacadeInParameterBinding> {
        // There must be as many parameters to the interface method as there are to the facade method.
        test(boundMethod.parameters.size == facadeMethod.inParameters.size) {
            "Interface method has ${boundMethod.parameters.size} parameters, " +
                    "but facade method has ${facadeMethod.inParameters.size}"
        }

        return boundMethod.parameters.mapIndexed { index, parameter ->
            // We must be able to obtain a parameter name to match the interface and facade parameters together.
            val boundParameter = ParameterInfo.forMethodParameter(index, parameter).orFail {
                "Cannot obtain name of parameter #$index - " +
                        "either enable -parameters compiler option, or annotate the parameter with @BindFacadeParameter"
            }

            // Qualifiers must agree
            test(
                boundParameter.typeQualifiers.isEmpty() ||
                        boundParameter.typeQualifiers.containsAll(boundParameter.parameterQualifiers)
            ) {
                "Parameter qualification mismatch: parameter $boundParameter has a type with qualifiers " +
                        "${boundParameter.typeQualifiers}, " +
                        "but is annotated with qualifiers ${boundParameter.parameterQualifiers}"
            }

            // The interface parameter name must match an in-parameter name in the facade.
            val facadeParameter = facadeMethod.untypedInParameter(boundParameter.boundName).orFail {
                "There is no input parameter named ${boundParameter.boundName} in this facade method"
            }

            InParameterBindingContext(this, boundParameter, facadeParameter).createBinding()
        }
    }

    private fun createOutParameterBindings(): FacadeOutParameterBindings {
        // The method must return an InteropAction<T>, rather than the return type itself directly.
        test(boundMethod.returnType == InteropAction::class.java) {
            "Method return type must be InteropAction<T>"
        }

        // Obtain the wrapped return type by reflecting over the InteropAction<T> type and obtaining its first type
        // argument, e.g. InteropAction<String> wraps String.
        val wrappedReturnType = (boundMethod.genericReturnType as ParameterizedType).actualTypeArguments[0]
                as Class<*>

        /*
        Depending on the number of out parameters, we are either binding:

        * 0 parameters to Void/Unit
        * 1 parameter directly to the wrapped return type (in this case its name is not checked).
        * 2 or more parameters to properties of a data class.

        We build a suitable context depending on which is the case, and let it do the necessary validation and/or
        reflection to create the binding.
         */
        val bindingContext = when (facadeMethod.outParameters.size) {
            0 -> NoOutParametersBindingContext(this, wrappedReturnType)
            1 -> SingletonOutParameterBindingContext(
                this,
                wrappedReturnType,
                facadeMethod.outParameters.first()
            )

            else -> DataClassOutParametersBindingContext(
                this,
                wrappedReturnType,
                facadeMethod.outParameters
            )
        }

        return bindingContext.createBinding()
    }

    override fun toString(): String =
        "${parent}, binding facade method ${facadeMethod.name} to interface method ${boundMethod.name}"
}

// Collection of things it is useful to know about a parameter
private data class ParameterInfo(
    val index: Int,
    val nativeName: String,
    val annotatedName: String?,
    val type: Class<*>,
    val parameterQualifiers: Set<TypeQualifier>,
    val typeQualifiers: Set<TypeQualifier>
) {

    companion object {
        fun forMethodParameter(index: Int, parameter: Parameter): ParameterInfo? {
            val annotatedName = parameter.readAnnotation<BindsFacadeParameter>()?.value

            // If we don't have an annotation or the native parameter name, we can't construct this object.
            if (annotatedName == null && !parameter.isNamePresent) return null

            return ParameterInfo(
                index,
                parameter.name,
                annotatedName,
                parameter.type,
                qualifiers(parameter),
                qualifiers(parameter.type)
            )
        }
    }

    val boundName: String get() = annotatedName ?: nativeName.kebabCase

    // Parameter qualifiers must always be a subset of type qualifiers (if the latter is not empty), and the "smaller"
    // set must always be chosen.
    val qualifiers: Set<TypeQualifier>
        get() = parameterQualifiers.ifEmpty { typeQualifiers }

    override fun toString(): String =
        (nativeName + (annotatedName?.let { "<$it>" } ?: "") + "(#$index) " + parameterQualifiers.showIfNonEmpty() +
                "of type $type " + typeQualifiers.showIfNonEmpty()).trim()

    private fun Set<TypeQualifier>.showIfNonEmpty() = if (isNotEmpty()) {
        joinToString(", ", "[", "] ")
    } else ""
}

// Binding context in which we are binding an interface method parameter to a facade method in-parameter.
private class InParameterBindingContext(
    val parent: MethodBindingContext,
    val parameterInfo: ParameterInfo,
    val facadeParameter: TypedParameter<*>
) : BindingContext<FacadeInParameterBinding>() {

    override fun createBinding(): FacadeInParameterBinding {
        // Check the type-compatibility of the interface method parameter type and the facade method parameter type.
        test(validateTypeAgreement(parameterInfo.type, parameterInfo.qualifiers, facadeParameter.type)) {
            "Type of parameter is not compatible with facade in-parameter type"
        }

        return FacadeInParameterBinding(
            facadeParameter,
            BoundParameter(parameterInfo.index, parameterInfo.type)
        )
    }

    override fun toString(): String =
        "$parent, binding method parameter $parameterInfo to facade in-parameter ${facadeParameter.name} " +
                "of type ${facadeParameter.type}"


}

// BindingContext in which we are binding no out parameters to a Void/Unit wrapped return type.
private class NoOutParametersBindingContext(
    val parent: MethodBindingContext,
    val wrappedReturnType: Class<*>
) : BindingContext<FacadeOutParameterBindings>() {

    override fun createBinding(): FacadeOutParameterBindings {
        // If the caller is expecting anything other than Void/Unit, something is wrong.
        test(wrappedReturnType == Unit::class.java || wrappedReturnType == Void::class.java) {
            "Return type $wrappedReturnType is not Void/Unit"
        }

        return FacadeOutParameterBindings.NoOutParameters
    }

    override fun toString(): String = "$parent, no out-parameters to bind"
}

// Binding context in which we are binding a single out-parameter to the wrapped return value of a method.
private class SingletonOutParameterBindingContext(
    val parent: MethodBindingContext,
    val wrappedReturnType: Class<*>,
    val outParameter: TypedParameter<*>
) : BindingContext<FacadeOutParameterBindings>() {

    private val qualifiers = qualifiers(wrappedReturnType)

    override fun createBinding(): FacadeOutParameterBindings {
        // Check agreement of the facade and interface types.
        test(validateTypeAgreement(wrappedReturnType, qualifiers, outParameter.type)) {
            "Return type is not compatible with facade out-parameter type"
        }

        return FacadeOutParameterBindings.SingletonOutParameterBinding(
            outParameter,
            wrappedReturnType
        )
    }

    override fun toString(): String = "$parent, binding return value of type ${wrappedReturnType.name} " +
            qualifiers.joinToString(", ", "[", "]") +
            " to single out-parameter ${outParameter.name} of type ${outParameter.type}"

}

// Binding context in which we are binding multiple out-parameters
private class DataClassOutParametersBindingContext(
    val parent: MethodBindingContext,
    val wrappedReturnType: Class<*>,
    val outParameters: List<TypedParameter<*>>
) : BindingContext<FacadeOutParameterBindings>() {

    override fun createBinding(): FacadeOutParameterBindings {
        val constructor = wrappedReturnType.constructors.singleOrNull().orFail {
            "Return type does not have a unique constructor"
        }

        val facadeParametersByName = outParameters.associateBy(TypedParameter<*>::getName)
        val constructorParameters =
            constructor.parameters.mapIndexedNotNull(ParameterInfo.Companion::forMethodParameter)
        constructorParameters.forEach {
            test(it.typeQualifiers.isEmpty() || it.typeQualifiers.containsAll(it.parameterQualifiers)) {
                "Parameter qualification mismatch: parameter $it has a type with qualifiers ${it.typeQualifiers}, " +
                        "but is annotated with qualifiers ${it.parameterQualifiers}"
            }
        }
        val constructorParametersByName = constructorParameters.associateBy(ParameterInfo::boundName)

        // We need this lookup because Kotlin data classes will have un-annotated getters which we want to match up
        // to potentially annotated constructor parameters by native name.
        val boundNameLookup = constructorParameters.associate { it.nativeName to it.boundName }

        test(facadeParametersByName.keys == constructorParametersByName.keys) {
            "Out parameters are ${facadeParametersByName.keys}, " +
                    "but constructor parameters are ${constructorParametersByName.keys}"
        }

        // Check that the types all agree
        outParameters.forEach { outParameter ->
            val constructorParameter = constructorParametersByName[outParameter.name]!!
            test(
                validateTypeAgreement(
                    constructorParameter.type,
                    constructorParameter.qualifiers,
                    outParameter.type
                )
            ) {
                "Constructor parameter $constructorParameter does not match type of facade out parameter $outParameter"
            }
        }

        // Find the getter methods corresponding to the constructor parameters, and create property bindings.
        val beanInfo = Introspector.getBeanInfo(wrappedReturnType)
        val properties = beanInfo.propertyDescriptors.mapNotNull { property ->
            bindDataClassProperty(property, facadeParametersByName, constructorParametersByName, boundNameLookup)
        }

        // There must be properties for every out parameter (we can just check collection sizes here, as names have
        // already been correlated).
        test(properties.size == outParameters.size) {
            val propertyNames = properties.map { it.facadeOutParameter.name }.toSet()
            val missingProperties = outParameters.map(TypedParameter<*>::getName).toSet() - propertyNames

            "Cannot find properties with both a constructor parameter and a getter method for out parameters $missingProperties"
        }

        return FacadeOutParameterBindings.DataClassOutParameterBindings(constructor, properties)
    }

    private fun bindDataClassProperty(
        property: PropertyDescriptor,
        facadeParametersByName: Map<String, TypedParameter<*>>,
        constructorParametersByName: Map<String, ParameterInfo>,
        boundNameLookup: Map<String, String>
    ): DataClassPropertyBinding? {
        // Ignore the property if it doesn't have a read method.
        val method = property.readMethod ?: return null

        val nativeName = property.name
        val annotatedName = property.readMethod.readAnnotation<BindsFacadeParameter>()?.value
        val boundName = annotatedName ?: boundNameLookup[nativeName] ?: nativeName.kebabCase

        // Ignore the method if it doesn't correspond to a facade parameter.
        val facadeParameter = facadeParametersByName[boundName] ?: return null

        // We tested that the two collections had identical keysets earlier, so this will always succeed.
        val constructorParameter = constructorParametersByName[boundName]!!

        // Check that constructor argument and getter return types are the same.
        test(method.returnType == constructorParameter.type) {
            "Type mismatch: property read method ${method.name} has type ${method.returnType}, " +
                    "but matched constructor parameter $constructorParameter takes ${constructorParameter.type}"
        }

        return DataClassPropertyBinding(
            facadeParameter,
            BoundParameter(constructorParameter.index, constructorParameter.type),
            method
        )
    }

    override fun toString(): String =
        "$parent"

}

private inline fun <reified T : Annotation> AnnotatedElement.readAnnotation(): T? =
    if (isAnnotationPresent(T::class.java)) getAnnotation(T::class.java) else null

/*
A parameter or property may be qualified either directly, via the @QualifiedWith annotation, or indirectly, via a
developer-defined annotation that is itself annotated with @QualifiedWith - this enables application developers to
use custom annotations in a similar way to the way aliases are used in the facade YAML file, as a shorthand for common
types.
 */
private fun qualifiers(element: AnnotatedElement): Set<TypeQualifier> =
    element.readAnnotation<QualifiedWith>()?.run {
        value.map(TypeQualifier::of).toSet()
    } ?: element.annotations.asSequence().mapNotNull {
        it.annotationClass.java.readAnnotation<QualifiedWith>()?.run {
            value.map(TypeQualifier::of).toSet()
        }
    }.firstOrNull() ?: emptySet()

private val numberTypes = setOf(
    Int::class.javaObjectType,
    Int::class.javaPrimitiveType,
    Long::class.javaObjectType,
    Long::class.javaPrimitiveType,
    Float::class.javaObjectType,
    Float::class.javaPrimitiveType,
    Double::class.javaObjectType,
    Double::class.javaPrimitiveType,
    BigDecimal::class.java
)

// The complete set of rules for when a JVM type matches up with a Facade type.
private fun validateTypeAgreement(
    parameterType: Class<*>,
    qualifiers: Set<TypeQualifier>,
    expectedType: ParameterType<*>
): Boolean =
    if (expectedType.isQualified) {
        (qualifiers.isEmpty() || expectedType.qualifier in qualifiers)
                && validateTypeAgreement(parameterType, emptySet(), expectedType.rawParameterType)
    } else {
        when (expectedType.typeLabel) {
            ParameterTypeLabel.DECIMAL -> parameterType in numberTypes
            ParameterTypeLabel.STRING -> parameterType == String::class.java
            ParameterTypeLabel.BOOLEAN -> parameterType == Boolean::class.javaPrimitiveType
            ParameterTypeLabel.TIMESTAMP -> parameterType == ZonedDateTime::class.java
            ParameterTypeLabel.UUID -> parameterType == UUID::class.java
            ParameterTypeLabel.BYTES -> parameterType == ByteArray::class.javaPrimitiveType ||
                    parameterType == ByteBuffer::class.java

            ParameterTypeLabel.JSON -> true
        }
    }

private val String.kebabCase: String
    get() {
        val builder = StringBuilder()
        var wasLowercase = false
        forEach { c ->
            if (c.isUpperCase() && wasLowercase) {
                builder.append("-")
            }
            builder.append(c.lowercaseChar())
            wasLowercase = c.isLowerCase()
        }
        return builder.toString()
    }