package net.corda.libs.permissions.storage.writer.impl.user

import net.corda.data.permissions.management.user.AddPropertyToUserRequest
import net.corda.data.permissions.management.user.AddRoleToUserRequest
import net.corda.data.permissions.management.user.ChangeUserPasswordRequest
import net.corda.data.permissions.management.user.CreateUserRequest
import net.corda.data.permissions.management.user.DeleteUserRequest
import net.corda.data.permissions.management.user.RemovePropertyFromUserRequest
import net.corda.data.permissions.management.user.RemoveRoleFromUserRequest
import net.corda.data.permissions.User as AvroUser

/**
 * Responsible for writing user operations to data storage.
 */
interface UserWriter {
    /**
     * Create and persist a User entity and return its Avro representation.
     *
     * @param request CreateUserRequest containing the information of the User to create.
     * @param requestUserId ID of the user who made the request.
     */
    fun createUser(request: CreateUserRequest, requestUserId: String): AvroUser

    /**
     * Delete a User entity and return its Avro representation.
     *
     * @param request DeleteUserRequest containing the information of the User to delete.
     * @param requestUserId ID of the user who made the request.
     */
    fun deleteUser(request: DeleteUserRequest, requestUserId: String): AvroUser

    /**
     * Change the password field of a User entity and return its Avro representation.
     *
     * @param request ChangeUserPasswordRequest containing the information of the password change request.
     * @param requestUserId ID of the user who made the request.
     */
    fun changeUserPassword(request: ChangeUserPasswordRequest, requestUserId: String): AvroUser

    /**
     * Associate a Role to a User and return its Avro representation.
     *
     * @param request AddRoleToUserRequest containing the information of the Role and User to associate.
     * @param requestUserId ID of the user who made the request.
     */
    fun addRoleToUser(request: AddRoleToUserRequest, requestUserId: String): AvroUser

    /**
     * Dissociate a Role from a User and return its Avro representation.
     *
     * @param request RemoveRoleFromUserRequest containing the information of the Role and User to dissociate.
     * @param requestUserId ID of the user who made the request.
     */
    fun removeRoleFromUser(request: RemoveRoleFromUserRequest, requestUserId: String): AvroUser

    /**
     * Add a property to a User and return its Avro representation.
     *
     * @param request AddPropertyToUserRequest containing the Property to add to a User.
     * @param requestUserId ID of the user who made the request.
     */
    fun addPropertyToUser(request: AddPropertyToUserRequest, requestUserId: String): AvroUser

    /**
     * Remove a property from a User and return its Avro representation.
     *
     * @param request RemovePropertyFromUserRequest containing the Property to remove from a User.
     * @param requestUserId ID of the user who made the request.
     */
    fun removePropertyFromUser(request: RemovePropertyFromUserRequest, requestUserId: String): AvroUser
}
