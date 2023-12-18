package net.corda.rest.tools.annotations.validation.utils

import net.corda.rest.annotations.RestApiVersion

internal tailrec fun RestApiVersion.isEqualOrChildOf(earlierVersion: RestApiVersion): Boolean {
    if (this == earlierVersion) {
        return true
    }

    this.parentVersion.let {
        if (it == null) {
            return false
        }
        return it.isEqualOrChildOf(earlierVersion)
    }
}
