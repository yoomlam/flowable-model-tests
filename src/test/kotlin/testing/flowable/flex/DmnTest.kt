package testing.flowable.flex

import org.assertj.core.api.Assertions.assertThat
import org.flowable.dmn.engine.DmnEngine
import org.flowable.dmn.engine.test.DmnDeployment
import org.flowable.dmn.engine.test.DmnDeploymentId
import org.flowable.dmn.engine.test.FlowableDmnTestHelper
import org.flowable.engine.test.Deployment
import org.flowable.engine.test.DeploymentId
import org.flowable.engine.test.FlowableTestHelper
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase

@TestConfiguration
private class DmnTestConfig

@Import(DmnTestConfig::class)
class DmnTest : FlowableSpringTestBase() {

    @Test
    @DmnDeployment(resources = ["processes/simpleDecisionTable.dmn"])
    fun simpleDmnTest(
        flowableTestHelper: FlowableDmnTestHelper,
        @DmnDeploymentId deploymentId: String,
        extensionDmnEngine: DmnEngine
    ) {
        val executionResult = decisionService.createExecuteDecisionBuilder()
            .decisionKey("decision1")
            .variable("input1", "Stringtest")
            .executeWithSingleResult()
        assertThat(executionResult).containsEntry("output1", "test2")

        val executionResult2 = decisionService.createExecuteDecisionBuilder()
            .decisionKey("decision1")
            .variable("input1", "testString")
            .executeWithSingleResult()
        assertThat(executionResult2).containsEntry("output1", "test1")

        assertThat(flowableTestHelper.deploymentIdFromDeploymentAnnotation)
            .isEqualTo(deploymentId)
            .isNotNull()
        assertThat(flowableTestHelper.dmnEngine)
            .`as`("Spring injected dmn engine")
            .isSameAs(dmnEngine)
            .`as`("Extension injected dmn engine")
            .isSameAs(extensionDmnEngine)

        val deployment = dmnRepositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult()
        assertThat(deployment).isNotNull()
    }

    @Test
    @Deployment(resources = ["processes/simpleProcess-withDmn.bpmn"])
    @DmnDeployment(resources = ["processes/simpleDecisionTable.dmn"])
    fun simpleProcessWithDmnTest(
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
        val processVariables: Map<String, Any> = mapOf(
            "processVar" to "Bye",
            "input1" to "testString"
        )
        runtimeService.startProcessInstanceByKey("simpleProcess-withDmn", processVariables)

        // Process instance is created and is waiting for completion
        assertThat(runtimeService.createProcessInstanceQuery().list()).isNotEmpty()

        // Check process variables
        with(historyService.createHistoricVariableInstanceQuery().list()) {
            assertThat(this.size).isEqualTo(2)
            assertThat(this.find { it.variableName == "processVar" }!!.value).isEqualTo("Bye")
            assertThat(this.find { it.variableName == "input1" }!!.value).isEqualTo("testString")
        }

        // Get the single active task in the process
        val task = taskService.createTaskQuery().singleResult()
        assertThat(task.name).isEqualTo("First UserTask")

        // Since it's a UserTask, it must be manually completed
        taskService.complete(task.id)

        // All tasks are complete, so there are no active tasks
        assertThat(taskService.createTaskQuery().list()).isEmpty()
        // Process instance is now completed
        assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty()

        with(historyService.createHistoricVariableInstanceQuery().list()) {
            assertThat(this.size).isEqualTo(3)
            assertThat(this.find { it.variableName == "input1" }!!.value).isEqualTo("string test")
            assertThat(this.find { it.variableName == "output1" }!!.value).isEqualTo("test2")
        }

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
            "flow2", "setInput",
            "flow2.1", "decisionTask1",
            "flow3", "theEnd"
        )
    }
}
