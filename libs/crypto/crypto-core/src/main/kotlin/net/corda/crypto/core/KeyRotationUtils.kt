package net.corda.crypto.core

object KeyRotationMetadataValues {

    /**
     *
     */
    const val ROOT_KEY_ALIAS: String = "rootKeyAlias"

    /**
     *
     */
    const val TENANT_ID: String = "tenantId"

    /**
     *
     */
    const val STATUS: String = "status"
    const val TYPE: String = "type"

}

object KeyRotationStatus {
    const val IN_PROGRESS: String = "inProgress"
    const val DONE: String = "done"
}

object KeyRotationRecordType {
    const val KEY_ROTATION: String = "keyRotation"
}

fun getKeyRotationStatusRecordKey(keyAlias: String, tenantId: String) =
    keyAlias + tenantId + KeyRotationRecordType.KEY_ROTATION

