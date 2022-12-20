package net.corda.persistence.common

import java.io.Serializable
import javax.persistence.Embeddable

@Embeddable
data class PairStringInt(
    val first: String,
    val second: Int
) : Serializable