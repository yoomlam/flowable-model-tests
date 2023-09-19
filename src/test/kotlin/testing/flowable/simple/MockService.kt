package testing.flowable.simple

import testing.flowable.Mockable

@Mockable
class MockService(private val initMessage: String = "default") {
    private val log = mu.KotlinLogging.logger {}
    init {
        log.info { "Creating MockService: $initMessage" }
    }
    fun logMessage(message: String) {
        log.info { "MockService ($initMessage): $message" }
    }
}
