package testing.flowable.flex

import org.flowable.cmmn.engine.test.CmmnDeployment
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import testing.flowable.VarValueMap
import testing.flowable.simple.TestService
import kotlin.test.assertEquals

@TestConfiguration
private class BasicIeeCmmConfig {
    // `someService` is referenced in the CMM XML file
    @Bean
    fun someService(): TestService = TestService("in ${this::class.simpleName}")
}

@Import(BasicIeeCmmConfig::class)
class BasicIeeCmmTest : FlowableSpringTestBase() {

    @Test
    @CmmnDeployment(resources = ["processes/basicIee.cmmn"])
    fun healthcareProgram() {
        runToCompletion(
            "passed",
            userTasks = linkedMapOf(
                "assessApplications" to taskOutputMap(
                    "benefitProgramName" to "healthcare"
                )
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("assessApplications", "healthcareSvc", "approvalProcess"),
            expectedEventPlanItems = listOf("approvalSentMS"),
            expectedMilestones = listOf("approval sent")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/basicIee.cmmn"])
    fun energyProgram() {
        val assessmentResultValue = "passed"
        runToCompletion(
            assessmentResultValue,
            userTasks = linkedMapOf(
                "assessApplications" to taskOutputMap(
                    "benefitProgramName" to "energy"
                ),
                "energySvc" to taskOutputMap(
                    "assessmentResult" to assessmentResultValue
                )
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("assessApplications", "energySvc", "approvalProcess"),
            expectedEventPlanItems = listOf("approvalSentMS"),
            expectedMilestones = listOf("approval sent")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/basicIee.cmmn"])
    fun energyProgramFailed() {
        val assessmentResultValue = "failed"
        runToCompletion(
            assessmentResultValue,
            userTasks = linkedMapOf(
                "assessApplications" to taskOutputMap(
                    "benefitProgramName" to "energy"
                ),
                "energySvc" to taskOutputMap(
                    "assessmentResult" to assessmentResultValue
                )
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("assessApplications", "energySvc", "denialProcess"),
            expectedEventPlanItems = listOf("denialSentMS"),
            expectedMilestones = listOf("denial sent")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/basicIee.cmmn"])
    fun foodProgram() {
        runToCompletion(
            "passed",
            userTasks = linkedMapOf(
                "assessApplications" to taskOutputMap(
                    "benefitProgramName" to "food"
                )
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("assessApplications", "foodSvc", "approvalProcess"),
            expectedEventPlanItems = listOf("approvalSentMS"),
            expectedMilestones = listOf("approval sent")
        )
    }

    private fun runToCompletion(
        assessmentResultValue: String,
        processVariables: VarValueMap = defaultProcessVariables(),
        userTasks: LinkedHashMap<String, VarValueMap>,
        expectedStagePlanItems: List<String>,
        expectedTaskPlanItems: List<String>,
        expectedEventPlanItems: List<String>,
        expectedMilestones: List<String> = listOf()
    ) {
        val caseInstance = startCmmCase("basicIee", processVariables)

        assertCmmnPlanItems(4)
        assertCmmnActiveStage(listOf("submissionStage"))
        assertEquals(1, getVars().size)

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

        assertVarEquals("assessmentResult", assessmentResultValue)
        assertCmmnMilestonesOccurred(expectedMilestones)

        // PlanItems executed as a result of UserTasks being completed
        assertCmmnStagesCompleted(expectedStagePlanItems)
        assertCmmnEventOccurred(expectedEventPlanItems)
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
