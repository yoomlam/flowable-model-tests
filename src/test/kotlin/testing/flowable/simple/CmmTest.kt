package testing.flowable.simple

import org.flowable.cmmn.engine.test.CmmnDeployment
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import testing.flowable.VarValueMap
import kotlin.test.assertEquals

@TestConfiguration
private class CmmTestConfig {
    // `someService` is referenced in the CMM XML file
    @Bean
    fun someService(): TestService = TestService("in ${this::class.simpleName}")
}

@Import(CmmTestConfig::class)
class CmmTest : FlowableSpringTestBase() {

    @Test
    @CmmnDeployment(resources = ["processes/simpleCaseManagement.cmmn"])
    fun healthcareProgram() {
        runToCompletion(
            "Healthcare",
            userTasks = linkedMapOf(
                "assessApplications" to taskOutputMap(
                    "benefitProgramName" to "healthcare"
                )
            ),
            expectedStagePlanItems = listOf("applicationEntryStage", "healthcareSubmissionStage"),
            expectedTaskPlanItems = listOf("assessApplications", "healthcareSvc", "varHandler"),
            expectedEventPlanItems = listOf("varEventLnr")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/simpleCaseManagement.cmmn"])
    fun energyProgram() {
        val eligibilityResponseValue = "Energy"
        runToCompletion(
            eligibilityResponseValue,
            userTasks = linkedMapOf(
                "assessApplications" to taskOutputMap(
                    "benefitProgramName" to "energy"
                ),
                "energySvc" to taskOutputMap(
                    "eligibilityResponse" to eligibilityResponseValue
                )
            ),
            expectedStagePlanItems = listOf("applicationEntryStage", "energySubmissionStage"),
            expectedTaskPlanItems = listOf("assessApplications", "energySvc", "varHandler"),
            expectedEventPlanItems = listOf("varEventLnr")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/simpleCaseManagement.cmmn"])
    fun foodProgram() {
        runToCompletion(
            "Food",
            userTasks = linkedMapOf(
                "assessApplications" to taskOutputMap(
                    "benefitProgramName" to "food"
                )
            ),
            expectedStagePlanItems = listOf("applicationEntryStage", "foodSubmissionStage"),
            expectedTaskPlanItems = listOf("assessApplications", "foodSvc", "varHandler"),
            expectedEventPlanItems = listOf("foodMS", "varEventLnr"),
            expectedMilestones = listOf("food MS")
        )
    }

    private fun runToCompletion(
        eligibilityResponseValue: String,
        processVariables: VarValueMap = defaultProcessVariables(),
        userTasks: LinkedHashMap<String, VarValueMap> = LinkedHashMap(),
        expectedStagePlanItems: List<String>,
        expectedTaskPlanItems: List<String>,
        expectedEventPlanItems: List<String>,
        expectedMilestones: List<String> = listOf()
    ) {
        val caseInstance = startCmmCase("simpleCmmn", processVariables)

        assertCmmnPlanItems(8)
        assertCmmnActiveStage(listOf("applicationEntryStage"))
        val vars = getVars()
        assertEquals(1, vars.size)

        if (userTasks.isNotEmpty()) {
            // assert no milestones has occurred
            assertCmmnMilestonesOccurred()
            assertCmmnCaseNotComplete()
        }

        userTasks.forEach {
            assertCmmnActiveUserTasks(caseInstance, it.key)
            completeCmmnTask(it.key, it.value)
        }

        assertUserTasksOccurred(userTasks.keys.toList())
        // assert 0 active UserTasks since they've all been completed
        assertCmmnActiveUserTasks(caseInstance)

        assertVarEquals("eligibilityResponse", eligibilityResponseValue)
        assertCmmnMilestonesOccurred(expectedMilestones)

        // PlanItems executed as a result of UserTasks being completed
        assertCmmnStagesCompleted(expectedStagePlanItems)
        // "foodMS" and "varEventLnr" items occur at the same time, so ignore ordering
        assertCmmnEventOccurred(expectedEventPlanItems, inOrder = false)
        assertCmmnTasksCompleted(expectedTaskPlanItems)
        assertCmmnPlanItems()

        assertCmmnCaseComplete()

        // Why is it 0?
        assertCmmnSentryPartCount(caseInstance, 0)

        // What's a TaskLog? Why is it 0?
        val taskLog = cmmnHistoryService.createHistoricTaskLogEntryQuery().list()
        assertEquals(0, taskLog.size)
    }
}
