package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.time.Instant

object RbacTestUtils {

    fun createVNodeCreatorUser() {
        ClusterBInfo.cluster {
            val vNodeCreatorUser = "vnodecreatoruser"
            println("hello")
            println(getRbacUser(vNodeCreatorUser))
            if (getRbacUser(vNodeCreatorUser ).body.contains("User '$vNodeCreatorUser' not found")) {
                assertWithRetry {
                    command {
                        createRbacUser(
                            true,
                            vNodeCreatorUser,
                            vNodeCreatorUser,
                            vNodeCreatorUser,
                            null,
                            null
                        )
                    }
                    condition {
                        println(it)
                        it.code == 201
                    }
                }
                val roles = getRbacRoles()
                println(roles.body.toJson())
                val vNodeCreatorRoleId =
                    roles.body.toJson().first { it["roleName"].toString().contains("VNodeCreatorRole") }["id"].textValue()
                println(vNodeCreatorRoleId)
                assertWithRetry {
                    command {
                        assignRoleToUser(vNodeCreatorUser, vNodeCreatorRoleId)
                    }
                    condition {
                        println(it)
                        it.code == 200
                    }
                }
            }
        }
    }

    fun getAllRbacRoles(): List<RbacRole> {
        return DEFAULT_CLUSTER.cluster {
            val bodyAsString = assertWithRetryIgnoringExceptions {
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