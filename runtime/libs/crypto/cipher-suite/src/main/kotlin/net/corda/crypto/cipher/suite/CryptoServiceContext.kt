@file:JvmName("CryptoServiceContext")

package net.corda.crypto.cipher.suite

/**
 * Standard [CryptoService] context key defining the tenant id on behalf of which the corresponding operation is called.
 */
const val CRYPTO_TENANT_ID = "tenantId"

/**
 * Standard [CryptoService] context key defining the category for which the key is generated.
 */
const val CRYPTO_CATEGORY = "category"

/**
 * Standard [CryptoService] context key defining the type of the key for the key deletion operation. The values should
 * be either [CRYPTO_KEY_TYPE_WRAPPING] or [CRYPTO_KEY_TYPE_KEYPAIR].
 */
const val CRYPTO_KEY_TYPE = "keyType"

/**
 * Standard [CryptoService] context value defining that the delete operation is performed for the wrapping key. Used
 * together with the [CRYPTO_KEY_TYPE].
 */
const val CRYPTO_KEY_TYPE_WRAPPING = "wrapping"


/**
 * Standard [CryptoService] context value defining that the delete operation is performed for the key pair. Used
 * together with the [CRYPTO_KEY_TYPE].
 */
const val CRYPTO_KEY_TYPE_KEYPAIR = "key-pair"