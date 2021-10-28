package net.corda.applications.examples.amqp.typeevolution

import net.corda.v5.serialization.annotations.CordaSerializationTransformRename
import net.corda.v5.serialization.annotations.CordaSerializationTransformRenames
import net.corda.v5.serialization.annotations.DeprecatedConstructorForDeserialization

// Before
//
//// Add nullable property
//data class AddNullableProperty(val a: Int)
//
//// Add non-nullable property
//data class AddNonNullableProperty(val a: Int)
//
//// Multiple evolutions
//data class MultipleEvolutions(val a: Int)
//data class MultipleEvolutions(val a: Int, val b: Int) {
//    @DeprecatedConstructorForDeserialization(1)
//    constructor(a: Int) : this(a, 0)
//}
//
//// Removing properties
//data class RemovingProperties(val a: Int, val b: Int)
//
//// Reorder constructor parameters
//data class ReorderConstructorParameters(val a: Int, val b: Int)
//
//// Rename enum
//enum class RenameEnum {
//    A, B
//}
//
//// Add enum value
//@CordaSerializationTransformEnumDefaults(CordaSerializationTransformEnumDefault(new = "C", old = "A"))
//enum class AddEnumValue {
//    A, B, C
//}


// After

// Add nullable property
data class AddNullableProperty(val a: Int, val b: Int?)

// Add non-nullable property
data class AddNonNullableProperty(val a: Int, val b: Int) {
    @DeprecatedConstructorForDeserialization(1)
    constructor(a: Int) : this(a, 0)
}

// Multiple evolutions
data class MultipleEvolutions(val a: Int, val b: Int, val c: Int) {
    @DeprecatedConstructorForDeserialization(1)
    constructor(a: Int) : this(a, 0, 0)
    @DeprecatedConstructorForDeserialization(2)
    constructor(a: Int, b: Int) : this(a, b, 0)
}

// Removing properties
data class RemovingProperties(val a: Int)

// Reorder constructor parameters
data class ReorderConstructorParameters(val b: Int, val a: Int)

// Rename enum
@CordaSerializationTransformRenames(CordaSerializationTransformRename(to = "C", from = "B"))
enum class RenameEnum {
    A, C
}

// Add enum value
enum class AddEnumValue {
    A, B
}
