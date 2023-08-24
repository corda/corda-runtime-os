package net.corda.membership.datamodel

import net.corda.db.schema.DbSchema
import java.io.Serializable
import java.util.Objects
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Table

/**
 * An entity representing approval rules applied to registration requests for determining the approval method (manual
 * or automatic).
 */
@Entity
@Table(name = DbSchema.VNODE_GROUP_APPROVAL_RULES)
@IdClass(ApprovalRulesEntityPrimaryKey::class)
class ApprovalRulesEntity(
    @Id
    @Column(name = "rule_id", nullable = false, updatable = false)
    var ruleId: String,

    @Id
    @Column(name = "rule_type", nullable = false, updatable = false)
    var ruleType: String,

    @Column(name = "rule_regex", nullable = false, updatable = false)
    var ruleRegex: String,

    @Column(name = "rule_label", nullable = true, updatable = false)
    var ruleLabel: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is ApprovalRulesEntity) return false
        return other.ruleId == this.ruleId
                && other.ruleType == this.ruleType
    }

    override fun hashCode(): Int {
        return Objects.hash(ruleId, ruleType)
    }
}

@Embeddable
data class ApprovalRulesEntityPrimaryKey(
    var ruleId: String,
    var ruleType: String
) : Serializable
