package testing.flowable.simple

import org.flowable.cmmn.engine.test.CmmnDeployment
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import testing.flowable.VarValueMap
import kotlin.test.assertEquals

@TestConfiguration
private class CmmTestConfig {
    @Bean
    fun someService(): TestService = TestService("in ${this::class.simpleName}")
}

@Import(CmmTestConfig::class)
class CmmTest : FlowableSpringTestBase() {
    @Autowired
    lateinit var someService: TestService

    companion object {
        const val applicationId = 20230919
    }

    override fun defaultProcessVariables() = mapOf(
        "applicationId" to applicationId
    )

    private fun runToCompletion(
        eligibilityResponseValue: String,
        processVariables: VarValueMap,
        userTasks: LinkedHashMap<String, VarValueMap> = LinkedHashMap(),
        expectedStagePlanItems: List<String>,
        expectedTaskPlanItems: List<String>,
        expectedEventPlanItems: List<String>,
        expectedMilestones: List<String> = listOf()
    ) {
        val caseInstance = startCmmCase("simpleCmmn", processVariables)

        assertCmmnPlanItems(8)
        assertCmmnActiveStage(listOf("applicationEntry"))
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
        assertVarEquals("eligibilityResponse", eligibilityResponseValue)
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
    @CmmnDeployment(resources = ["processes/simpleCaseManagement.cmmn.xml"])
    fun healthcareProgram() {
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "healthcare"
            )
        )
        val stageItems = listOf("applicationEntry", "healthcareSubmissionStage")
        val taskItems = listOf("assessApplications", "healthcareSvc", "varHandler")
        val eventItems = listOf("varEventLnr")
        runToCompletion(
            "Healthcare",
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/simpleCaseManagement.cmmn.xml"])
    fun energyProgram() {
        val eligibilityResponseValue = "Energy"
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "energy"
            ),
            "energySvc" to taskOutputMap(
                "eligibilityResponse" to eligibilityResponseValue
            )
        )
        val stageItems = listOf("applicationEntry", "energySubmissionStage")
        val taskItems = listOf("assessApplications", "energySvc", "varHandler")
        val eventItems = listOf("varEventLnr")
        runToCompletion(
            eligibilityResponseValue,
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/simpleCaseManagement.cmmn.xml"])
    fun foodProgram() {
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "food"
            )
        )
        val stageItems = listOf("applicationEntry", "foodSubmissionStage")
        val taskItems = listOf("assessApplications", "foodSvc", "varHandler")
        val eventItems = listOf("foodMS", "varEventLnr")
        val expectedMilestones = listOf("food MS")
        runToCompletion(
            "Food",
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems,
            expectedMilestones
        )
    }
}
