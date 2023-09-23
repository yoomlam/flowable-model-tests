package testing.flowable.simple

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.flowable.engine.HistoryService
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.engine.test.Deployment
import org.flowable.engine.test.DeploymentId
import org.flowable.engine.test.FlowableTestHelper
import org.flowable.spring.impl.test.FlowableSpringExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import testing.flowable.FlowableConfiguration
import testing.flowable.simple.service.MyApiClient
import kotlin.test.BeforeTest

// Create TestConfiguration specifically for this test class
// Demonstrates different ways to overrides beans originally defined in SimpleTestConfiguration
@TestConfiguration
private class MyTestConfig {
    // Overrides 'someService' bean referenced directly by serviceTask1 in simpleProcess-serviceCall.bpmn
    @Bean
    fun someService(): TestService = spy(TestService("in ${this::class.simpleName}"))

    // Overrides underlying 'apiClient2' used by 'someService2' bean for serviceTask2 in simpleProcess-serviceCall.bpmn
    @Bean
    fun apiClient2(): MyApiClient = mock(MyApiClient::class.java)

    // Flowable provides a Mocks class but it hasn't been needed since the above are sufficient.
    // https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-engine/src/main/java/org/flowable/engine/test/mock/Mocks.java
    // https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-engine/src/test/java/org/flowable/standalone/testing/MockSupportWithFlowableJupiterTest.java
}

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
        MyTestConfig::class
    ]
)
class LoadFromSpringConfigurationTest {

    @Autowired
    lateinit var someService: TestService

    @Autowired
    lateinit var apiClient2: MyApiClient

    companion object {
        // Refer to https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic
        @RegisterExtension
        @JvmStatic
        val mockApi: WireMockExtension = WireMockExtension.newInstance()
            .failOnUnmatchedRequests(true)
            .options(wireMockConfig().port(3000))
            .build()
    }

    @BeforeTest
    fun setup() {
        `when`(apiClient2.callApiEndpoint()).thenCallRealMethod()

        mockApi.stubFor(
            WireMock.post("/api/notifications")
                .willReturn(WireMock.unauthorized())
        )
    }

    @Autowired
    lateinit var processEngine: ProcessEngine

    @Autowired
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var historyService: HistoryService

    @Test
    @Deployment(resources = ["processes/simpleProcess-serviceCall.bpmn"])
    fun simpleProcessTest(
        flowableTestHelper: FlowableTestHelper,
        @DeploymentId deploymentId: String
    ) {
        // Check WireMock
        assertThat(mockApi.runtimeInfo.httpPort).isEqualTo(3000)
        assertThat(mockApi.runtimeInfo.httpBaseUrl).isEqualTo("http://localhost:3000")

        // Check deployment
        assertThat(flowableTestHelper.deploymentIdFromDeploymentAnnotation)
            .isEqualTo(deploymentId)
            .isNotNull()

        val deployedProcessDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId).singleResult()
        assertThat(deployedProcessDefinition).isNotNull()

        // Check process engine
        assertThat(processEngine.name).isEqualTo("default")
        assertThat(flowableTestHelper.processEngine)
            .`as`("Spring injected process engine")
            .isSameAs(processEngine)

        // Create and start process instance
        val processVariables: Map<String, Any> = mapOf(
            "processVar" to "Bye"
        )
        runtimeService.startProcessInstanceByKey("simpleProcess-serviceCall-springDemo", processVariables)

        // Process instance is created and is waiting for completion
        assertThat(runtimeService.createProcessInstanceQuery().list()).isNotEmpty()

        // Check process variables
        val vars = historyService.createHistoricVariableInstanceQuery().list()
        assertThat(vars.size).isEqualTo(1)
        assertThat(vars.find { it.variableName == "processVar" }!!.value).isEqualTo("Bye")

        // Get the single active task in the process
        val task = taskService.createTaskQuery().singleResult()
        assertThat(task.name).isEqualTo("First UserTask")

        // Since it's a UserTask, it must be manually completed
        taskService.complete(task.id)

        // Verify that a method on the TestService 'someService' is called (for serviceTask1)
        verify(someService).logMessage("Hello")
        // Verify that a method on the SomeApiClient is called (for serviceTask2)
        verify(apiClient2).callApiEndpoint()

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
        assertThat(activityIds).containsSequence(
            "start",
            "flow1", "firstUserTask",
            "flow2", "serviceTask1",
            "flow3", "serviceTask2",
            "flow4", "httpServiceTask",
            "flow5", "theEnd"
        )
    }
}
