package net.corda.crypto.core.aes.ecdh

import com.fasterxml.jackson.databind.ObjectMapper

// needs to be Avro serialisation for predictable order

fun Any.asBytes(): ByteArray = ObjectMapper().writeValueAsBytes(this)

inline fun <reified T> ByteArray.fromBytes(): T = ObjectMapper().readValue(this, T::class.java)