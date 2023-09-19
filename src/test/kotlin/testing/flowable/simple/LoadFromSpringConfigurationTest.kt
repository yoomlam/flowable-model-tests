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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

// https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-spring/src/test/java/org/flowable/spring/test/jupiter/SpringJunitJupiterTest.java
@ExtendWith(FlowableSpringExtension::class)
@ExtendWith(SpringExtension::class)
// Use only the listed Configuration classes; a subsequent Configuration overrides prior Configuration elements
@ContextConfiguration(classes = [SimpleConfiguration::class, LoadFromSpringConfigurationTest.MySpecificConfig::class])
class LoadFromSpringConfigurationTest {

    // Create TestConfiguration specifically for this test class
    @TestConfiguration
    class MySpecificConfig {
        // Overrides 'someService' bean originally defined in SimpleTestConfiguration
        @Bean
        fun someService(): MockService = spy(MockService("in ${this::class.simpleName}"))

        // TODO: investigate: flowableTestHelper.mockSupport.setAllServiceTasksNoOp()
        // TODO: investigate: mockSupport.mockServiceTaskByIdWithClassDelegate("serviceTask1", MockServiceTask::class.java)
    }

    @Autowired
    lateinit var someService: MockService

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

        // Verify that a method on the MockService 'someService' is called
        verify(someService).logMessage("Hello")

        // Process instance is now completed
        assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty()
    }
}
