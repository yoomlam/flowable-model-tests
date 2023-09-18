package flowable.tests

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

private val log = mu.KotlinLogging.logger {}

@SpringBootApplication
class FlowableModelsTestApplication

fun main(args: Array<String>) {
    runApplication<FlowableModelsTestApplication>(*args)
}
