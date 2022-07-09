package net.corda.crypto.core.aes

import java.security.SecureRandom

const val GCM_NONCE_LENGTH = 12 // in bytes
const val GCM_TAG_LENGTH = 16 // in bytes
const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
const val AES_DERIVE_ITERATION_COUNT = 65536
const val AES_KEY_ALGORITHM = "AES"
const val AES_KEY_SIZE = 256 // in bits
const val AES_PROVIDER = "SunJCE"

internal val secureRandom = SecureRandom()
