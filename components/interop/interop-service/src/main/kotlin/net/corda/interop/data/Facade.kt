package net.corda.interop.data


/**
 * A [HierarchicalName] is a sequence of [String] identifiers similar to the path section of a URI.
 */
typealias HierarchicalName = List<String>

/**
 * A [FacadeId] identifies a version of a facade.
 *
 * @param owner The name of the owner of the facade, e.g. "org.corda".
 * @param name The name of the facade, e.g. "platform/tokens", expressed as a [HierarchicalName].
 * @param version The version identifier of the facade, e.g. "1.0".
 */
data class FacadeId(val owner: String, val name: HierarchicalName, val version: String) {

    companion object {

        /**
         * Construct a [FacadeId] from a string of the form "org.owner/hierarchical/name/version".
         *
         * @param idString The string to build a [FacadeId] from.
         */
        fun of(idString: String): FacadeId {
            val parts = idString.split("/")
            if (parts.size < 3) {
                throw IllegalArgumentException("Invalid Facade ID: $idString")
            }
            return FacadeId(parts[0], parts.subList(1, parts.size - 1), parts.last())
        }
    }

    override fun toString(): String = "$owner/${name.joinToString("/")}/$version"

}