package net.corda.sandbox.internal.utilities.dot.lang

interface AttributeList {
    /**
     * Add attributes specifically for this statement, i.e. something { X=Y; A=B; ... }
     * Convenience for adding add(IdStatement(X,Y))
     */
    fun attrs(attributes: List<Pair<String, String>>)
}
