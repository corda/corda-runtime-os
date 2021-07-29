package net.corda.v5.serialization.annotations

/**
 * Annotation indicating a constructor to be used to reconstruct instances of a class during deserialization.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConstructorForDeserialization