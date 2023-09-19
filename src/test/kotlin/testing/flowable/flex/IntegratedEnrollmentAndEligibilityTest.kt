package testing.flowable.flex

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.flowable.engine.HistoryService
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.engine.test.Deployment
import org.flowable.spring.impl.test.FlowableSpringExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import testing.flowable.FlowableConfiguration
import testing.flowable.simple.SimpleConfiguration
import kotlin.test.BeforeTest

// This is provided to demonstrate loading a Process Engine configuration from a Spring Configuration.
// This is preferred (over LoadFromCfgXmlTest) due to type-checking and autocompletion provided by IDEs.
// Based on https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-spring/src/test/java/org/flowable/spring/test/jupiter/SpringJunitJupiterTest.java
@ExtendWith(FlowableSpringExtension::class)
@ExtendWith(SpringExtension::class)
// Use only the listed Configuration classes; a subsequent Configuration overrides prior Configuration elements
@ContextConfiguration(
    classes = [
        FlowableConfiguration::class,
        SimpleConfiguration::class,
        IntegratedEnrollmentAndEligibilityTest.MySpecificConfig::class
    ]
)
class IntegratedEnrollmentAndEligibilityTest {

    // Create TestConfiguration specifically for this test class
    // Demonstrates different ways to overrides beans originally defined in SimpleTestConfiguration
    @TestConfiguration
    class MySpecificConfig

    companion object {
        // Refer to https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic
        @RegisterExtension
        @JvmStatic
        val mockApi: WireMockExtension = WireMockExtension.newInstance()
            .failOnUnmatchedRequests(true)
            .options(wireMockConfig().port(3000))
            .build()

        const val applicationId = 1234
    }

    @BeforeTest
    fun setup() {
        mockApi.stubFor(
            WireMock.post("/api/applications/$applicationId/notifications")
                .willReturn(WireMock.badRequest())
        )
        mockApi.stubFor(
            WireMock.post("/api/eligibility")
                .willReturn(
                    WireMock.aResponse()
                        .withBody("{ \"eligible\": true}")
                )
        )
        mockApi.stubFor(
            WireMock.post("/api/notifications")
                .willReturn(WireMock.badRequest())
        )
    }

    @Autowired
    lateinit var processEngine: ProcessEngine

    @Autowired
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var historyService: HistoryService

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun simpleProcessTest() {
        // Check WireMock
        assertThat(mockApi.runtimeInfo.httpPort).isEqualTo(3000)
        assertThat(mockApi.runtimeInfo.httpBaseUrl).isEqualTo("http://localhost:3000")

        // Create and start process instance
        val processVariables: Map<String, Any> = mapOf(
            "applicationIncome" to 2000,
            "applicationId" to applicationId
        )
        runtimeService.startProcessInstanceByKey("integratedEnrollmentAndEligibilityDemo", processVariables)

        // Process instance is created and is waiting for completion
        assertThat(runtimeService.createProcessInstanceQuery().list()).isNotEmpty()

        // Get the single active task in the process
        val verifyIncomeTask = taskService.createTaskQuery().taskName("Verifies income").singleResult()
        // Since it's a UserTask, it must be manually completed
        taskService.complete(verifyIncomeTask.id)

//        // Verify that a method on the TestService 'someService' is called (for serviceTask1)
//        verify(someService).logMessage("Hello")
//        // Verify that a method on the SomeApiClient is called (for serviceTask2)
//        verify(apiClient2).callApiEndpoint()

        // All tasks are complete, so there are no active tasks
        assertThat(taskService.createTaskQuery().list()).isEmpty()
        // Process instance is now completed
        assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty()

        // Check history
        assertThat(historyService.createHistoricProcessInstanceQuery().count()).isEqualTo(1)

        // Apparently this only returns UserTasks?
        val tasks = historyService.createHistoricTaskInstanceQuery().orderByHistoricTaskInstanceStartTime().asc().list()
        println("tasks: $tasks")
        assertThat(tasks.size).isEqualTo(1)

        // Activities have tasks and sequenceFlows that were executed
        val activities = historyService.createHistoricActivityInstanceQuery().orderByHistoricActivityInstanceStartTime().asc().list()
        val activityIds = activities.map { it.activityId }
        println(activityIds)
        assertThat(activityIds).containsSequence(
            "applicationSubmitted",
            "bpmnSequenceFlow_2", "splitOnProgramType",
            "bpmnSequenceFlow_6", "checksEligibility",
            "bpmnSequenceFlow_8", "bpmnGateway_7",
            "bpmnSequenceFlow_9", "verifiesIncome",
            "bpmnSequenceFlow_11", "bpmnGateway_8",
            "bpmnSequenceFlow_14", "sendDenialNotification",
            "bpmnSequenceFlow_5", "applicationProcessed"
        )
    }
}
