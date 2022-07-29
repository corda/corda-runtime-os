package net.corda.sandbox.internal.utilities.dot.lang

/**
 * ID '=' ID
 *
 * See https://graphviz.org/doc/info/lang.html
 */
class IdStatement(val key: String, val value: String) : Statement {
    override fun render(): String = "$key=\"$value\""
}
