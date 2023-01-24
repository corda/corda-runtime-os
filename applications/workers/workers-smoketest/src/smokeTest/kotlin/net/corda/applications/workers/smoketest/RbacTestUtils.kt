package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.cluster
import java.time.Instant

object RbacTestUtils {

    fun getAllRbacRoles(): List<RbacRole> {
        return cluster {
            endpoint(
                net.corda.e2etest.utilities.CLUSTER_URI,
                net.corda.e2etest.utilities.USERNAME,
                net.corda.e2etest.utilities.PASSWORD
            )

            val bodyAsString = assertWithRetry {
                command { getRbacRoles() }
                condition { it.code == 200 }
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