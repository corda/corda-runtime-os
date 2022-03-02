package net.corda.testdoubles

import kotlin.reflect.KProperty0

class GlobalVariableNotInitialised(name: String): Exception("Global variable $name has not been initialised") {
    constructor(property: KProperty0<*>) : this(property.name)
}