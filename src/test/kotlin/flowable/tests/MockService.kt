package flowable.tests

class MockService(private val initMessage: String) {
    private val log = mu.KotlinLogging.logger {}
    init {
        log.info { "Creating MockService: $initMessage" }
    }
    fun logMessage(message: String) {
        log.info { "MockService ($initMessage): $message" }
    }
}
