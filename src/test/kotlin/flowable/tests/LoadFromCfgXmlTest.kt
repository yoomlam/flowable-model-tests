package flowable.tests

import org.assertj.core.api.Assertions.assertThat
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.engine.test.ConfigurationResource
import org.flowable.engine.test.Deployment
import org.flowable.engine.test.DeploymentId
import org.flowable.engine.test.FlowableTest
import org.flowable.engine.test.FlowableTestHelper
import org.junit.jupiter.api.Test

// Refer to https://www.flowable.com/open-source/docs/bpmn/ch04-API/#unit-testing
@FlowableTest
@ConfigurationResource("processEngine.cfg.xml")
class LoadFromCfgXmlTest {
    // Refer to https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-engine/src/test/java/org/flowable/standalone/testing/FlowableJupiterTest.java
    @Test
    @Deployment(resources = ["processes/simpleProcess.bpmn"])
    fun testSimpleProcess(
        @DeploymentId deploymentId: String,
        processEngine: ProcessEngine,
        flowableTestHelper: FlowableTestHelper,
        // For services descriptions, see https://www.flowable.com/open-source/docs/bpmn/ch04-API/#the-process-engine-api-and-services
        repositoryService: RepositoryService,
        runtimeService: RuntimeService,
        taskService: TaskService
    ) {
        // Check deployment
        assertThat(flowableTestHelper.deploymentIdFromDeploymentAnnotation)
            .isEqualTo(deploymentId)
            .isNotNull()

        val deployedProcessDefinition = repositoryService.createProcessDefinitionQuery().deploymentId(deploymentId).singleResult()
        assertThat(deployedProcessDefinition).isNotNull()

        // Check process engine
        assertThat(processEngine.name).isEqualTo("Minimal process engine")
        assertThat(flowableTestHelper.processEngine)
            .`as`("Spring injected process engine")
            .isSameAs(processEngine)

        // Create and start process instance
        val processVariables: Map<String, Any> = mapOf()
        runtimeService.startProcessInstanceByKey("simpleProcess", processVariables)

        // Process instance is created and is waiting for completion
        assertThat(runtimeService.createProcessInstanceQuery().list()).isNotEmpty()

        // Get the single active task in the process
        val task = taskService.createTaskQuery().singleResult()
        assertThat(task.name).isEqualTo("First UserTask")

        // Since it's a UserTask, it must be manually completed
        taskService.complete(task.id)

        // Process instance is now completed
        assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty()
    }
}
