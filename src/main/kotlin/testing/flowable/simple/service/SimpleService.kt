package testing.flowable.simple.service

import testing.flowable.Mockable

private val log = mu.KotlinLogging.logger {}

@Mockable
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
