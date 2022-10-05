package net.cordapp.testing.calculator

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "calculator")
class CalculatorEntity(
    @Id
    val id: String,
    @Column
    val numberFormat: String,
    @Column
    val scientific: Boolean,
    @Column
    val graphing: Boolean,
    @Column
    val resolution: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalculatorEntity) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}