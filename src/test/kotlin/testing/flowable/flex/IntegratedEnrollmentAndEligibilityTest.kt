package testing.flowable.flex

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.flowable.engine.HistoryService
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

@ExtendWith(FlowableSpringExtension::class)
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [
        FlowableConfiguration::class,
        SimpleConfiguration::class,
        IntegratedEnrollmentAndEligibilityTest.MySpecificConfig::class
    ]
)
class IntegratedEnrollmentAndEligibilityTest {

    @TestConfiguration
    class MySpecificConfig

    companion object {
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
            WireMock.post("/api/eligibility/healthcare")
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
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var historyService: HistoryService

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun simpleProcessTest() {
        // Create and start process instance
        val processVariables: Map<String, Any> = mapOf(
            "applicationId" to applicationId,
            "applicationIncome" to 350000,
            "householdSize" to 2
        )
        runtimeService.startProcessInstanceByKey("integratedEnrollmentAndEligibilityDemo", processVariables)

        // Process instance is created and is waiting for completion
        assertThat(runtimeService.createProcessInstanceQuery().list()).isNotEmpty()

        // Get the single active task in the process
        val makeDeterminationTask = taskService.createTaskQuery().taskName("Makes determination").singleResult()
        taskService.complete(makeDeterminationTask.id)

//        val verifyIncomeTask = taskService.createTaskQuery().taskName("Verifies income").singleResult()

//        // Verify that a method on the TestService 'someService' is called (for serviceTask1)
//        verify(someService).logMessage("Hello")
//        // Verify that a method on the SomeApiClient is called (for serviceTask2)
//        verify(apiClient2).callApiEndpoint()

        val expectedActivities = arrayOf(
            "applicationSubmitted",
            "bpmnSequenceFlow_2", "splitOnProgramType",
            "bpmnSequenceFlow_15", "bpmnTask_8",
            "bpmnSequenceFlow_18", "bpmnGateway_17",
            "bpmnSequenceFlow_19", "makesDetermination",
            "bpmnSequenceFlow_3", "sendApprovalNotification",
            "bpmnSequenceFlow_12", "applicationProcessed"
        )
        assertCompletion(userTasksRan = 1, expectedActivities)
    }

    private fun assertCompletion(userTasksRan: Int, expectedActivities: Array<String>) {
        // All tasks are complete, so there are no active tasks
        assertThat(taskService.createTaskQuery().list()).isEmpty()
        // Process instance is now completed
        assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty()

        // Check history
        assertThat(historyService.createHistoricProcessInstanceQuery().count()).isEqualTo(1)

        // Apparently this only returns UserTasks?
        val tasks = historyService.createHistoricTaskInstanceQuery().orderByHistoricTaskInstanceStartTime().asc().list()
        println("tasks: $tasks")
        assertThat(tasks.size).isEqualTo(userTasksRan)

        // Activities have tasks and sequenceFlows that were executed
        val activities = historyService.createHistoricActivityInstanceQuery().orderByHistoricActivityInstanceStartTime().asc().list()
        val activityIds = activities.map { it.activityId }
        assertThat(activityIds).containsSequence(*expectedActivities)
    }
}
