package testing.flowable.flex

import org.flowable.cmmn.api.runtime.CaseInstance
import org.flowable.cmmn.engine.CmmnEngineConfiguration
import org.flowable.cmmn.engine.impl.persistence.entity.SentryPartInstanceEntity
import org.flowable.cmmn.engine.impl.util.CommandContextUtil
import org.flowable.cmmn.engine.test.CmmnDeployment
import org.flowable.common.engine.impl.interceptor.Command
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
private class IeeCmmConfig {
    @Bean
    fun someService(): TestService = TestService("in ${this::class.simpleName}")
}

@Import(IeeCmmConfig::class)
class IeeCmmTest : FlowableSpringTestBase() {
    @Autowired
    lateinit var someService: TestService

    companion object {
        const val applicationId = 20230919
    }

    override fun defaultProcessVariables() = mapOf(
        "applicationId" to applicationId
//        "benefitProgramName" to "energy"
    )

    private fun runToCompletion(
        eligibilityResponseValue: String,
        processVariables: VarValueMap,
        userTasks: LinkedHashMap<String, VarValueMap> = LinkedHashMap(),
        expectedPlanItems: List<String>
    ) {
        val caseInstance = startCmmCase("iee3", processVariables)

        val startupItems = assertPlanItems(6)
        val runtimePis = cmmnRuntimeService.createPlanItemInstanceQuery().list()

        if(userTasks.isNotEmpty())
            assertCmmnCaseNotComplete()

        userTasks.forEach {
            assertCmmnActiveUserTasks(caseInstance, it.key)
            completeCmmnTask(it.key, it.value)
        }

        assertUserTasksOccurred(userTasks.keys.toList())
        // assert 0 UserTasks since they've all been completed
        assertCmmnActiveUserTasks(caseInstance)

        assertVarEquals("eligibilityResponse", eligibilityResponseValue)
        assertCmmnCaseComplete()
        assertCmmnCaseCount(1)

        // PlanItems executed as a result of UserTasks being completed
        assertPlanItemsExecuted(expectedPlanItems, startupItems)

        // Why is it 0?
        assertSentryPartInstanceCount(caseInstance, 0)

        // What's a TaskLog? Why is it 0?
        // val taskLog = cmmnHistoryService.createHistoricTaskLogEntryQuery().list()
        // assertEquals(0, taskLog.size)
    }

    @Test
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility.cmmn.xml"])
    fun healthcareProgram() {
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "healthcare"
            )
        )
        val planItems = listOf("healthcareSvc", "varEventGW")
        runToCompletion("Healthcare", defaultProcessVariables(), userTasks, planItems)
    }

    @Test
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility.cmmn.xml"])
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
        val planItems = listOf("energySvc", "varEventGW")
        runToCompletion(eligibilityResponseValue, defaultProcessVariables(), userTasks, planItems)
    }

    @Test
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility.cmmn.xml"])
    fun foodProgram() {
        val userTasks = linkedMapOf(
            "assessApplications" to taskOutputMap(
                "benefitProgramName" to "food"
            )
        )
        val planItems = listOf("foodSvc", "varEventGW")
        runToCompletion("Food", defaultProcessVariables(), userTasks, planItems)
    }
}
