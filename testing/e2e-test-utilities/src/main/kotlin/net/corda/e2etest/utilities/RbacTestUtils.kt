package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.rest.ResponseCode
import java.time.Instant

object RbacTestUtils {

    fun getAllRbacRoles(): List<RbacRole> {
        return DEFAULT_CLUSTER.cluster {
            val bodyAsString = assertWithRetryIgnoringExceptions {
                command { getRbacRoles() }
                condition { it.code == ResponseCode.OK.statusCode }
                failMessage("Failed to get all the RBAC roles in the cluster")
            }.body
            ObjectMapper()
                .registerModule(JavaTimeModule())
                .readerForListOf(RbacRole::class.java).readValue(bodyAsString)
        }
    }

    class RbacRole {

        /**
         * Id of the Role.
         */
        var id: String = ""

        /**
         * Version of the Role.
         */
        var version: Int = 0

        /**
         * Time the Role was last updated.
         */
        var updateTimestamp: Instant = Instant.MIN

        /**
         * Name of the Role.
         */
        var roleName: String = ""

        /**
         * Group visibility of the Role.
         */
        var groupVisibility: String? = null

        /**
         * List of permission associations the Role has.
         */
        var permissions: List<RbacPermAssociation> = emptyList()
    }

    class RbacPermAssociation {

        /**
         * Id of the Permission.
         */
        var id: String = ""

        /**
         * Time when the Permission association was created.
         */
        var createdTimestamp: Instant = Instant.MIN
    }
}