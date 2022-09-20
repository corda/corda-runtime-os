package net.corda.libs.permissions.common.exception

import net.corda.httprpc.ResponseCode
import net.corda.httprpc.exception.HttpApiException


class EntityAssociationAlreadyExistsException(message: String) : HttpApiException(ResponseCode.CONFLICT, message)