package net.corda.libs.packaging.core

/**
 * [CordappType] main purpose is currently to distinguish between contract and workflow CPKs
 */
enum class CordappType {
    CONTRACT,
    WORKFLOW;

    companion object{
        fun fromAvro(other: net.corda.data.packaging.CorDappType) : CordappType = when (other) {
            net.corda.data.packaging.CorDappType.CONTRACT -> CONTRACT
            net.corda.data.packaging.CorDappType.WORKFLOW -> WORKFLOW
        }
    }

    fun toAvro(): net.corda.data.packaging.CorDappType = when (this) {
        CONTRACT -> net.corda.data.packaging.CorDappType.CONTRACT
        WORKFLOW -> net.corda.data.packaging.CorDappType.WORKFLOW
    }

}