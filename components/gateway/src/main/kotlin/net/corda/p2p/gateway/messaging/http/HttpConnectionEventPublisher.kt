package net.corda.p2p.gateway.messaging.http

import java.util.concurrent.Executors
import java.util.concurrent.SubmissionPublisher

class HttpConnectionEventPublisher : SubmissionPublisher<ConnectionChangeEvent>(Executors.newSingleThreadExecutor(), 5) {

}