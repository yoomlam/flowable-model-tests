package testing.flowable.simple.service

private val log = mu.KotlinLogging.logger {}

// Open the class and method so tests can spy on it
open class MyApiClient(private val initMessage: String) {
    open fun callApiEndpoint(){
        log.info { "SomeApiClient ($initMessage)" }
    }
}

class SimpleService(private val apiClient: MyApiClient) {
    init {
        log.info { "Creating SimpleService: $apiClient" }
    }
    fun logMessage(message: String) {
        log.info { "SimpleService: $message" }
        apiClient.callApiEndpoint()
    }
}
