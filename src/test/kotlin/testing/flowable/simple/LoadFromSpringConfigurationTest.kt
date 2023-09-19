package testing.flowable.simple

import org.assertj.core.api.Assertions.assertThat
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.engine.test.Deployment
import org.flowable.engine.test.DeploymentId
import org.flowable.engine.test.FlowableTestHelper
import org.flowable.spring.impl.test.FlowableSpringExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import testing.flowable.simple.service.MyApiClient
import kotlin.test.BeforeTest

// This is provided to demonstrate loading a Process Engine configuration from a Spring Configuration.
// This is preferred (over LoadFromCfgXmlTest) due to type-checking and autocompletion provided by IDEs.
// Based on https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-spring/src/test/java/org/flowable/spring/test/jupiter/SpringJunitJupiterTest.java
@ExtendWith(FlowableSpringExtension::class)
@ExtendWith(SpringExtension::class)
// Use only the listed Configuration classes; a subsequent Configuration overrides prior Configuration elements
@ContextConfiguration(classes = [SimpleConfiguration::class, LoadFromSpringConfigurationTest.MySpecificConfig::class])
class LoadFromSpringConfigurationTest {

    // Create TestConfiguration specifically for this test class
    // Demonstrates different ways to overrides beans originally defined in SimpleTestConfiguration
    @TestConfiguration
    class MySpecificConfig {
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

    @Autowired
    lateinit var someService: TestService

    @Autowired
    lateinit var apiClient2: MyApiClient

    @BeforeTest
    fun setup(){
        `when`(apiClient2.callApiEndpoint()).thenCallRealMethod();
    }

    @Autowired
    lateinit var processEngine: ProcessEngine

    @Autowired
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Test
    @Deployment(resources = ["processes/simpleProcess-serviceCall.bpmn"])
    fun simpleProcessTest(
        flowableTestHelper: FlowableTestHelper,
        @DeploymentId deploymentId: String
    ) {
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
        val processVariables: Map<String, Any> = mapOf()
        runtimeService.startProcessInstanceByKey("simpleProcess-mocking", processVariables)

        // Process instance is created and is waiting for completion
        assertThat(runtimeService.createProcessInstanceQuery().list()).isNotEmpty()

        // Get the single active task in the process
        val task = taskService.createTaskQuery().singleResult()
        assertThat(task.name).isEqualTo("First UserTask")

        // Since it's a UserTask, it must be manually completed
        taskService.complete(task.id)

        // Verify that a method on the TestService 'someService' is called (for serviceTask1)
        verify(someService).logMessage("Hello")
        // Verify that a method on the SomeApiClient is called (for serviceTask2)
        verify(apiClient2).callApiEndpoint()

        // Process instance is now completed
        assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty()
    }
}
