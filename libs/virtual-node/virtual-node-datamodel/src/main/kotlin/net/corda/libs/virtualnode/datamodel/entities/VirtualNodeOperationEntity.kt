package net.corda.libs.virtualnode.datamodel.entities

import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Version
import net.corda.db.schema.DbSchema.CONFIG
import net.corda.libs.virtualnode.datamodel.dto.VirtualNodeOperationType

@Entity
@Table(name = "virtual_node_operation", schema = CONFIG)
@Suppress("LongParameterList")
internal class VirtualNodeOperationEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: String,

    @Column(name = "request_id", nullable = false)
    var requestId: String,

    @Column(name = "data", nullable = false)
    var data: String,

    @Enumerated(value = EnumType.STRING)
    @Column(name = "state", nullable = false)
    var state: VirtualNodeOperationState,

    @Enumerated(value = EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    var operationType: OperationType,

    @Column(name = "request_timestamp")
    var requestTimestamp: Instant,

    @Column(name = "latest_update_timestamp", insertable = false)
    var latestUpdateTimestamp: Instant? = null,

    @Column(name = "heartbeat_timestamp")
    var heartbeatTimestamp: Instant? = null,

    @Column(name = "errors")
    var errors: String? = null,

    @Version
    @Column(name = "entity_version", nullable = false)
    var entityVersion: Int = 0,
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VirtualNodeOperationEntity) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

enum class VirtualNodeOperationState {
    IN_PROGRESS, COMPLETED, ABORTED, VALIDATION_FAILED, MIGRATIONS_FAILED
}

enum class OperationType {
    CREATE, UPGRADE;

    companion object {
        fun from(operationType: VirtualNodeOperationType): OperationType {
            return valueOf(operationType.name)
        }
    }
}