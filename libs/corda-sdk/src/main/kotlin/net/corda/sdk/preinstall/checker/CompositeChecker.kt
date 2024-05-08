package net.corda.sdk.preinstall.checker

class CompositeChecker : Checker {

    private val checkers: MutableList<Checker> = mutableListOf()

    fun addChecker(checker: Checker) {
        checkers.add(checker)
    }

    override fun check(): Int {
        return if (checkers.sumOf { it.check() } == 0) 0 else 1
    }
}
