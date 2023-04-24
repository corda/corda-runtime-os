package org.corda.weft.facade

import org.corda.weft.api.HierarchicalName
import org.corda.weft.parameters.TypedParameterValue
import java.util.*

/**
 * A [FacadeId3] identifies a version of a facade.
 *
 * @param owner The name of the owner of the facade, e.g. "org.corda".
 * @param name The name of the facade, e.g. "platform/tokens", expressed as a [HierarchicalName].
 * @param version The version identifier of the facade, e.g. "1.0".
 */
data class FacadeId3(val owner: String, val name: String, val version: String) {

    companion object {

        /**
         * Construct a [FacadeId3] from a string of the form "org.owner/hierarchical/name/version".
         *
         * @param idString The string to build a [FacadeId3] from.
         */
        @JvmStatic
        fun of(idString: String): FacadeId3 {
            val parts = idString.split("/")
            if (parts.size < 3) {
                throw IllegalArgumentException("Invalid Facade ID: $idString")
            }
            return FacadeId3(parts[0], parts.subList(1, parts.size - 1).joinToString("/"), parts.last())
        }
    }

    val unversionedName: String = "$owner/${name}"

    override fun toString(): String = "$unversionedName/$version"

}

/**
 * A [Facade] is a collection of [FacadeMethod]s that can be invoked.
 *
 * @param name The name of the facade, e.g. "org.corda.interop/platform/tokens/1.0".
 * @param methods The methods that can be invoked on the facade.
 */
data class Facade(val facadeId: FacadeId3, val methods: List<FacadeMethod>) {

    val methodsByName: Map<String, FacadeMethod> = methods.associateBy { it.name }

    /**
     * Get the method with the given name.
     *
     * @param name The name of the method to get.
     */
    fun method(name: String): FacadeMethod =
        methodsByName[name] ?: throw IllegalArgumentException("No such method: $name")

    /**
     * Obtain a request to invoke the method with the given name, with the given parameter values.
     *
     * @param methodName The name of the method to invoke.
     * @param inParameters The parameter values to pass to the method.
     */
    fun request(methodName: String, vararg inParameters: TypedParameterValue<*>): FacadeRequest {
        return method(methodName).request(*inParameters);
    }

    /**
     * Obtain a response to an invocation of the method with the given name, with the given parameter values.
     *
     * @param methodName The name of the method that was invoked.
     * @param outParameters The values of the out parameters of the method.
     */
    fun response(methodName: String, vararg outParameters: TypedParameterValue<*>): FacadeResponse {
        return method(methodName).response(*outParameters)
    }
}