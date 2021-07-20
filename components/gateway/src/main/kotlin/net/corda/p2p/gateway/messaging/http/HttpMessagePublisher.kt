package net.corda.p2p.gateway.messaging.http

import java.util.concurrent.Executors
import java.util.concurrent.SubmissionPublisher

class HttpMessagePublisher : SubmissionPublisher<HttpMessage>(Executors.newSingleThreadExecutor(), 5) {
}