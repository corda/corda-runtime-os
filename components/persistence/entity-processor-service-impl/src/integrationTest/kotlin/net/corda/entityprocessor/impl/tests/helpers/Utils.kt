package net.corda.entityprocessor.impl.tests.helpers

sealed class QuerySetup {
    data class NamedQuery(val params: Map<String, String>, val query: String = "Dog.summon") : QuerySetup()
    data class All(val className: String) : QuerySetup()
}