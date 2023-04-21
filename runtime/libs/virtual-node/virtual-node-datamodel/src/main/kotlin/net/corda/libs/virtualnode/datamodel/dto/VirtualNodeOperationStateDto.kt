package net.corda.libs.virtualnode.datamodel.dto

enum class VirtualNodeOperationStateDto {
    IN_PROGRESS, COMPLETED, ABORTED, VALIDATION_FAILED, LIQUIBASE_DIFF_CHECK_FAILED, MIGRATIONS_FAILED, UNEXPECTED_FAILURE
}