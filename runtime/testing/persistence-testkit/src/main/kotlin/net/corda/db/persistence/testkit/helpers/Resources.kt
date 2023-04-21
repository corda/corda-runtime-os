package net.corda.db.persistence.testkit.helpers

object Resources {
    const val EXTENDABLE_CPB = "/META-INF/extendable-cpb.cpb"
    // adding this to test calculator CPB sandbox can't load entities defined in the above.
    const val FISH_CPB = "/META-INF/testing-fish.cpb"
}
