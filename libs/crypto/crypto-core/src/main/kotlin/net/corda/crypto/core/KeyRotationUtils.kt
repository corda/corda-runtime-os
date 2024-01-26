package net.corda.crypto.core

/**
 * Metadata keys used when storing key rotation status in state manager
 */
object KeyRotationMetadataValues {
    const val ROOT_KEY_ALIAS: String = "rootKeyAlias" // only for unmanaged key rotation
    const val TENANT_ID: String = "tenantId"
    const val STATUS: String = "status"
    const val STATUS_TYPE: String = "type"
    const val KEY_TYPE: String = "type"
}

object KeyRotationStatus {
    const val IN_PROGRESS: String = "inProgress"
    const val DONE: String = "done"
}

/**
 * Specifies the type of the record stored in state manager
 */
object KeyRotationRecordType {
    const val KEY_ROTATION: String = "keyRotation"
}

/**
 * Specifies the type of key for which the status is stored in state manager
 */
object KeyRotationKeyType {
    const val MANAGED: String = "managed"
    const val UNMANAGED: String = "unmanaged"
}

fun getKeyRotationStatusRecordKey(keyAlias: String, tenantId: String) =
    keyAlias + tenantId + KeyRotationRecordType.KEY_ROTATION

