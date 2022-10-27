package net.corda.httprpc.response

import net.corda.httprpc.ResponseCode

class AsyncOperationResponse(val requestId: String) : ResponseEntity<String>(ResponseCode.ACCEPTED, requestId)






