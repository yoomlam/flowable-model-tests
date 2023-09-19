package testing.flowable

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

private val log = mu.KotlinLogging.logger {}

@SpringBootApplication
class FlowableModelsTestApplication

fun main(args: Array<String>) {
    runApplication<FlowableModelsTestApplication>(*args)
}

// Mockable opens classes so Mockito can be used on them
// https://medium.com/21buttons-tech/mocking-kotlin-classes-with-mockito-the-fast-way-631824edd5ba
annotation class Mockable
