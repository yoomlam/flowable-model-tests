package testing.flowable.flex

import org.flowable.cmmn.engine.test.CmmnDeployment
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import testing.flowable.VarValueMap
import testing.flowable.simple.TestService
import kotlin.test.assertEquals

@TestConfiguration
private class IeeBasicCmmConfig {
    @Bean
    fun someService(): TestService = TestService("in ${this::class.simpleName}")
}

@Import(IeeBasicCmmConfig::class)
class IeeBasicCmmTest : FlowableSpringTestBase() {
    @Autowired
    lateinit var someService: TestService

    override fun defaultProcessVariables(): VarValueMap = mapOf()

    private fun runToCompletion(
        assessmentResultValue: String,
        processVariables: VarValueMap,
        userTasks: LinkedHashMap<String, VarValueMap> = LinkedHashMap(),
        expectedStagePlanItems: List<String>,
        expectedTaskPlanItems: List<String>,
        expectedEventPlanItems: List<String>,
        expectedMilestones: List<String> = listOf()
    ) {
        val caseInstance = startCmmCase("ieeBasic", processVariables)

        assertCmmnPlanItems(4)
        assertCmmnActiveStage(listOf("submissionStage"))
        val vars = getVars()
        println("vars: ${vars.map { it.variableName }}")
//        assertEquals(4, vars.size)

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

        println("vars: ${getVars().map { it.variableName }}")
        assertVarEquals("assessmentResult", assessmentResultValue)
//        assertVarEquals("assessmentResult", assessmentResultValue)
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

    @Test
    @CmmnDeployment(resources = ["processes/ieeBasic.cmmn.xml"])
    fun healthcareProgram() {
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "healthcare"
            )
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "healthcareSvc", "approvalProcess")
        val eventItems = listOf("approvalSentMS")
        val expectedMilestones = listOf("approval sent")
        runToCompletion(
            "passed",
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems,
            expectedMilestones
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/ieeBasic.cmmn.xml"])
    fun energyProgram() {
        val assessmentResultValue = "passed"
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "energy"
            ),
            "energySvc" to taskOutputMap(
                "assessmentResult" to assessmentResultValue
            )
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "energySvc", "approvalProcess")
        val eventItems = listOf("approvalSentMS")
        val expectedMilestones = listOf("approval sent")
        runToCompletion(
            assessmentResultValue,
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems,
            expectedMilestones
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/ieeBasic.cmmn.xml"])
    fun energyProgramFailed() {
        val assessmentResultValue = "failed"
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "energy"
            ),
            "energySvc" to taskOutputMap(
                "assessmentResult" to assessmentResultValue
            )
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "energySvc", "denialProcess")
        val eventItems = listOf("denialSentMS")
        val expectedMilestones = listOf("denial sent")
        runToCompletion(
            assessmentResultValue,
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems,
            expectedMilestones
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/ieeBasic.cmmn.xml"])
    fun foodProgram() {
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "food"
            )
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "foodSvc", "approvalProcess")
        val eventItems = listOf("approvalSentMS")
        val expectedMilestones = listOf("approval sent")
        runToCompletion(
            "passed",
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems,
            expectedMilestones
        )
    }
}
