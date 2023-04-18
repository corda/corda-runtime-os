package net.corda.flow.application.services.impl.interop.binding

import net.corda.v5.application.interop.facade.Facade
import net.corda.v5.application.interop.facade.FacadeMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod


/**
 * Represents the binding of a [Facade] to a JVM interface.
 *
 * @param facade The bound [Facade]
 * @param boundInterface The bound JVM interface.
 * @param methodBindings Bindings of Facade methods to interface methods.
 */
data class FacadeInterfaceBinding(
    val facade: Facade,
    val boundInterface: Class<*>,
    val methodBindings: List<FacadeMethodBinding>
) {

    private val bindingsByMethod = methodBindings.associateBy { it.interfaceMethod }
    private val bindingsByFacadeMethodName = methodBindings.associateBy { it.facadeMethod.name }

    /**
     * Obtain the [FacadeMethodBinding] for a given [Method] on the bound interface.
     *
     * @param interfaceMethod The [Method] to get the binding for
     */
    fun bindingFor(interfaceMethod: Method): FacadeMethodBinding? = bindingsByMethod[interfaceMethod]

    /**
     * Utility method for writing tests in Kotlin - enables us to request the binding for a method by passing in a
     * Kotlin method reference, e.g. `facadeBindings.bindingFor(MyInterface::myMethod)`.
     *
     * @param method A Kotlin method reference pointing to the method to gether the binding for
     */
    fun bindingFor(method: KFunction<*>) = bindingFor(method.javaMethod!!)
    fun bindingFor(facadeMethodName: String): FacadeMethodBinding? = bindingsByFacadeMethodName[facadeMethodName]
}

/**
 * Binding of a [FacadeMethod] to a [Method] on a JVM interface.
 *
 * @param facadeMethod The bound [FacadeMethod]
 * @param interfaceMethod The bound JVM [Method]
 * @param inParameterBindings Bindings of method parameters to [TypedParameter] in-parameters
 * @param outParameterBindings Bindings of [TypedParameter] out-parameters, either to a single value or to fields in a
 * data class
 */
data class FacadeMethodBinding(
    val facadeMethod: FacadeMethod,
    val interfaceMethod: Method,
    val inParameterBindings: List<FacadeInParameterBinding>,
    val outParameterBindings: FacadeOutParameterBindings
) {

    private val inParameterBindingsByName = inParameterBindings.associateBy { it.facadeParameter.name }

    /**
     * Obtain the [FacadeInParameterBinding] for the method parameter at the given index
     *
     * @param parameterIndex The index of the method parameter to get the binding for
     */
    fun bindingForMethodParameter(parameterIndex: Int): FacadeInParameterBinding? =
        inParameterBindings.getOrNull(parameterIndex)

    fun bindingForInParameter(name: String): FacadeInParameterBinding? = inParameterBindingsByName[name]

}

/**
 * Binding of a [TypedParameter] in-parameter to a [BoundParameter] method parameter
 *
 * @param facadeParameter The bound facade in-parameter
 * @param boundParameter The bound method parameter
 */
data class FacadeInParameterBinding(
    val facadeParameter: TypedParameter<*>,
    val boundParameter: BoundParameter
)

/**
 * Represents a parameter of a method, by index and JVM [Class]
 *
 * @param index The index of the parameter
 * @param type The JVM [Class] (non-generic / erased) of the parameter
 */
data class BoundParameter(val index: Int, val type: Class<*>)

/**
 * Bindings for the out-parameters of a facade method. There are three flavours:
 *
 * - NoOutParameters - when the method returns `InteropAction<Void>` and there are no out-parameters to bind
 * - SingletonOutParameterBinding - when there is only one out-parameter to bind, and it is bound directly to the
 *   return type (wrapped with [org.corda.weft.binding.api.InteropAction]) of the method
 * - DataClassOutParameterBinding - used when there are multiple out-parameters to bind, and they are bound to the
 *   properties of a Kotlin data class or Java POJO.
 *
 * Note that in the third case, if a Java POJO is used it must obey the following conventions:
 *
 * - All property values must be passed in through the constructor, rather than set with "setter" methods.
 * - Every property value must gave a 'getter' method through which it can be read, e.g. for the property "balance" a
 *   "getBalance" method.
 * - Matching of constructor arguments to getters is by the facade parameter method name to which they are bound.
 */
sealed class FacadeOutParameterBindings {

    object NoOutParameters : FacadeOutParameterBindings()

    data class SingletonOutParameterBinding(val outParameter: TypedParameter<*>, val returnType: Class<*>)
        : FacadeOutParameterBindings()

    data class DataClassOutParameterBindings(val constructor: Constructor<*>, val bindings: List<DataClassPropertyBinding>)
        : FacadeOutParameterBindings() {

        private val bindingsByParameterName = bindings.associateBy { it.facadeOutParameter.name }

        fun bindingFor(parameter: TypedParameter<*>): DataClassPropertyBinding? = bindingsByParameterName[parameter.name]
    }
}

/**
 * Binding of a [TypedParameter] out-parameter to a property of a data class
 *
 * @param facadeOutParameter The bound parameter
 * @param constructorParameter The bound parameter of the data class constructor, used when populating a result
 * @param readMethod The "getter" method corresponding to the bound property, used when translating a result object into
 * a set of out-parameter values
 */
data class DataClassPropertyBinding(
    val facadeOutParameter: TypedParameter<*>,
    val constructorParameter: BoundParameter,
    val readMethod: Method)
