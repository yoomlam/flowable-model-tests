package testing.flowable.simple.service

class SimpleService(private val initMessage: String) {
    private val log = mu.KotlinLogging.logger {}
    init {
        log.info { "Creating SimpleService: $initMessage" }
    }
    fun logMessage(message: String) {
        log.info { "SimpleService ($initMessage): $message" }
    }
}
