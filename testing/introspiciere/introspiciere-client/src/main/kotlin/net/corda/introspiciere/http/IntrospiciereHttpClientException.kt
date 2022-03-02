package net.corda.introspiciere.http

import net.corda.introspiciere.domain.IntrospiciereException

class IntrospiciereHttpClientException internal constructor(request: Any, response: Any, result: Any) :
    IntrospiciereException("\n$request\n$response\n$result ")