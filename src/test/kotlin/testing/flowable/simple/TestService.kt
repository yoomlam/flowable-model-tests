package testing.flowable.simple

import testing.flowable.Mockable

@Mockable
class TestService(private val initMessage: String = "default") {
    private val log = mu.KotlinLogging.logger {}
    init {
        log.info { "Creating TestService: $initMessage" }
    }
    fun logMessage(message: String): String {
        // Add artificial delay so that completion time is not exactly the same for various model elements
        Thread.sleep(500)
        log.info { "TestService ($initMessage): $message" }
        return message
    }
}
