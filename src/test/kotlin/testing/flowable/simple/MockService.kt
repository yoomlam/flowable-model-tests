package testing.flowable.simple

// Open the class and method so tests can spy on it
open class MockService(private val initMessage: String = "default") {
    private val log = mu.KotlinLogging.logger {}
    init {
        log.info { "Creating MockService: $initMessage" }
    }
    open fun logMessage(message: String) {
        log.info { "MockService ($initMessage): $message" }
    }
}
