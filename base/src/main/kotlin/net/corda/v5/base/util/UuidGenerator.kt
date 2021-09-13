package net.corda.v5.base.util

import java.util.UUID

class UuidGenerator {

    companion object {
        fun next(): UUID = UUID.randomUUID()
    }
}