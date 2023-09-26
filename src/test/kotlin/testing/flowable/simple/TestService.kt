package testing.flowable.simple

import testing.flowable.Mockable

@Mockable
class TestService(private val initMessage: String = "default") {
    private val log = mu.KotlinLogging.logger {}
    init {
        log.info { "Creating TestService: $initMessage" }
    }
    fun logMessage(message: String): String {
        log.info { "TestService ($initMessage): $message" }
        return message
    }
}
