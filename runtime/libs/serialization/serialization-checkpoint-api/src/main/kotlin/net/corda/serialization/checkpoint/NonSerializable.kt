package net.corda.serialization.checkpoint

/*
 Marker interface used to guard against accidental serialisation of types
 that should never be serialised in a checkpoint.
 */
interface NonSerializable