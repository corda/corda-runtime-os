package net.corda.serialization

/*
 Marker interface used to guard against accidental serialisation of types
 that should never be serialised in a checkpoint.
 */
interface NonSerializable