package net.corda.sandbox.internal.utilities.dot.lang

/**
 * Supports statements
 */
interface Statements {
    /**
     * Add a statement.
     *
     * @return the statement that was added, i.e. [statement]
     */
    fun add(statement: Statement) : Statement

    /**
     * Add a list of statements.
     */
    fun add(statements: List<Statement>)
}
