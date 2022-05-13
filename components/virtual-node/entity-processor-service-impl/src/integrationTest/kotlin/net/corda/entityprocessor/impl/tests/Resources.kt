package net.corda.entityprocessor.impl.tests

object Resources {
    const val EXTENDABLE_CPB = "/META-INF/extendable-cpb.cpb"
    // adding this to test calculator CPB sandbox can't load entities defined in the above.
    const val CALCULATOR_CPB = "/META-INF/calculator.cpb"
}
