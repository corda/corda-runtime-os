package net.corda.membership.impl.registration.verifiers

internal class OrderVerifier {
    /**
     * Checks if [keys] are numbered correctly (0, 1, ..., n).
     *
     * @param keys List of property keys to validate.
     * @param position Position of numbering in each of the provided [keys]. For example, [position] is 2 in
     * "corda.endpoints.0.connectionURL".
     */
    fun isOrdered(keys: List<String>, position: Int): Boolean =
        keys.map { it.split(".")[position].toInt() }
            .sorted()
            .mapIndexed { value, index ->
                value == index
            }.all { it }
}
