package net.corda.flow.application.services.impl.interop.facade

import net.corda.v5.application.interop.parameters.TypeQualifier

/**
 * A [FacadeTypeQualifier] qualifies a [ParameterType] with a versioned identity, which may be linked to a schema
 * or validation rules for that type.
 *
 * @param owner The owner of the type, e.g. "org.corda".
 * @param name The name of the type, e.g. "platform/tokens/Amount".
 * @param version The version of the type, e.g. "1.0".
 */
data class FacadeTypeQualifierImpl(val owner: String, val name: List<String>, val version: String): TypeQualifier(owner, name, version) {

    companion object {

        /**
         * Construct a [FacadeTypeQualifier] from a string of the form "org.owner/hierarchical/name/version".
         *
         * @param qualifierString The string to build a [FacadeTypeQualifier] from.
         */
        fun of(qualifierString: String): TypeQualifier {
            val parts = qualifierString.split("/")
            if (parts.size < 3) {
                throw IllegalArgumentException("Invalid Facade Type Qualifier: $qualifierString")
            }
            return TypeQualifier(parts[0], parts.subList(1, parts.size - 1), parts.last())
        }
    }

    override fun toString() = "$owner/${name.joinToString("/")}/$version"
}