package testing.flowable.simple

import org.assertj.core.api.Assertions.assertThat
import org.flowable.dmn.engine.test.DmnDeployment
import org.flowable.dmn.engine.test.DmnDeploymentId
import org.flowable.dmn.engine.test.FlowableDmnTestHelper
import org.flowable.engine.test.Deployment
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import kotlin.test.assertEquals

@TestConfiguration
private class DmnTestConfig

@Import(DmnTestConfig::class)
class DmnTest : FlowableSpringTestBase() {

    @Test
    @DmnDeployment(resources = ["processes/simpleDecisionTable.dmn"])
    fun simpleDmnTest(
        flowableTestHelper: FlowableDmnTestHelper,
        @DmnDeploymentId deploymentId: String
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

        assertProcessesComplete()

        val deployment = dmnRepositoryService.createDeploymentQuery().deploymentId(deploymentId).singleResult()
        assertThat(deployment).isNotNull()
    }

    @Test
    @Deployment(resources = ["processes/simpleProcess-withDmn.bpmn"])
    @DmnDeployment(resources = ["processes/simpleDecisionTable.dmn"])
    fun simpleProcessWithDmnTest() {
        startProcess(
            "simpleProcess-withDmn",
            mapOf(
                "processVar" to "Bye",
                "input1" to "testString"
            )
        )
        assertProcessNotComplete()

        // Check process variables
        assertEquals(2, getVars().size)
        assertVarValues(
            "processVar" to "Bye",
            "input1" to "testString"
        )

        completeTask("firstUserTask")

        assertProcessesComplete()
        assertProcessCount()
        assertUserTasksOccurred(listOf("firstUserTask"))

        assertEquals(3, getVars().size)
        assertVarValues(
            "input1" to "string test",
            "output1" to "test2"
        )

        assertActivitiesOccurred(
            listOf(
                "start",
                "flow1", "firstUserTask",
                "flow2", "setInput",
                "flow2.1", "decisionTask1",
                "flow3", "theEnd"
            )
        )
    }
}
