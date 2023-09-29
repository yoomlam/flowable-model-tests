package testing.flowable.flex

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.flowable.cmmn.engine.test.CmmnDeployment
import org.flowable.dmn.engine.test.DmnDeployment
import org.flowable.engine.test.Deployment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import testing.flowable.VarValueMap
import testing.flowable.simple.TestService
import testing.flowable.wireMockExtension
import kotlin.test.assertEquals

@TestConfiguration
private class IntegratedEnrollmentAndEligibilityCmmConfig {
    @Bean
    fun someService(): TestService = TestService("in ${this::class.simpleName}")
}

@Import(IntegratedEnrollmentAndEligibilityCmmConfig::class)
class IntegratedEnrollmentAndEligibilityCmmTest : FlowableSpringTestBase() {

    @Autowired
    lateinit var someService: TestService

    companion object {
        const val applicationId = 20230919

        @RegisterExtension
        @JvmStatic
        val mockApi: WireMockExtension = wireMockExtension()
    }

    private fun stubResponses(
        healthcareEligibilityResult: String = "",
        foodEligibilityResult: String = ""
    ) {
        mockApi.stubFor(
            WireMock.post("/api/applications/$applicationId/notifications")
                .willReturn(WireMock.badRequest())
        )
        // Eligibility table: https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/pages/api/eligibility/healthcare.ts#L20
        mockApi.stubFor(
            WireMock.post("/api/eligibility/healthcare")
                .willReturn(
                    WireMock.aResponse()
                        // EligiblityResult possible values: Adults, PregnantWomen, ChildrenUnder19, NotEligible
                        // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/pages/api/eligibility/healthcare.ts#L10
                        .withBody("{ \"eligibility_result\": \"$healthcareEligibilityResult\" }")
                )
        )
        mockApi.stubFor(
            WireMock.post("/api/eligibility/food")
                .willReturn(
                    WireMock.aResponse()
                        // EligiblityResult possible values: Eligible, NotEligible
                        // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/pages/api/eligibility/food.ts#L10
                        .withBody("{ \"eligibility_result\": \"$foodEligibilityResult\" }")
                )
        )
    }
    override fun defaultProcessVariables(): VarValueMap = mapOf(
        // applicationId is used in /api/applications/$applicationId/notifications
        "applicationId" to applicationId,
        // applicationIncome and householdSize are used in the payload to /api/eligibility/*
        "applicationIncome" to 3500 * 100, // in cents
        "householdSize" to 2
    )

    private fun runToCompletion(
        assessmentResultValue: String,
        processVariables: VarValueMap,
        userTasks: List<TaskOutput> = listOf(),
        expectedStagePlanItems: List<String>,
        expectedTaskPlanItems: List<String>,
        expectedEventPlanItems: List<String>,
        expectedMilestones: List<String> = listOf()
    ) {
        val caseInstance = startCmmCase("ieeCMMN", processVariables)

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
            when (it.modelType) {
                ModelType.BPM -> {
                    // assertCmmnActiveUserTasks(caseInstance, it.taskKey)
                    completeCmmnTask(it.taskKey, it.outputMap)
                }
                ModelType.CMM -> {
                    completeTask(it.taskKey, it.outputMap)
                }
            }
        }

        assertUserTasksOccurred(userTasks.map { it.taskKey })
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
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.cmmn"])
    @Deployment(
        resources = [
            "processes/Integrated_Enrollment_and_Eligibility-healthcareProcess.bpmn20.xml",
            "processes/Integrated_Enrollment_and_Eligibility-approvalProcess.bpmn20.xml"
        ]
    )
    @DmnDeployment(
        resources = [
            "processes/healthcareDecisionTableAdults.dmn",
            "processes/healthcareDecisionTablePregWomen.dmn",
            "processes/healthcareDecisionTableChildren.dmn",
            "processes/eligibilityDecisionTable.dmn"
        ]
    )
    fun healthcareProgram() {
        val userTasks = listOf(
            TaskOutput("assessApplications", outputMap = mapOf("benefitProgramName" to "healthcare")),
            TaskOutput("makeDetermination", ModelType.CMM, outputMap = mapOf())
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "healthcareProcess", "approvalProcess")
        val eventItems = listOf("approvalSentMS")
        val expectedMilestones = listOf("approval sent")
        stubResponses(healthcareEligibilityResult = "Eligible")
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
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.cmmn"])
    @Deployment(resources = ["processes/Integrated_Enrollment_and_Eligibility-approvalProcess.bpmn20.xml"])
    fun energyProgram() {
        val assessmentResultValue = "passed"
        val userTasks = listOf(
            TaskOutput("assessApplications", outputMap = mapOf("benefitProgramName" to "energy")),
            TaskOutput("energySvc", outputMap = mapOf("assessmentResult" to assessmentResultValue)),
            TaskOutput("makeDetermination", ModelType.CMM, outputMap = mapOf())
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "energySvc", "approvalProcess")
        val eventItems = listOf("approvalSentMS")
        val expectedMilestones = listOf("approval sent")
        stubResponses()
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
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.cmmn"])
    @Deployment(resources = ["processes/Integrated_Enrollment_and_Eligibility-approvalProcess.bpmn20.xml"])
    fun energyProgramFailed() {
        val assessmentResultValue = "failed"
        val userTasks = listOf(
            TaskOutput("assessApplications", outputMap = mapOf("benefitProgramName" to "energy")),
            TaskOutput("energySvc", outputMap = mapOf("assessmentResult" to assessmentResultValue))
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "energySvc", "sendDenialNotification")
        val eventItems = listOf("denialSentMS")
        val expectedMilestones = listOf("denial sent")
        stubResponses()
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
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.cmmn"])
    @Deployment(
        resources = [
            "processes/Integrated_Enrollment_and_Eligibility-foodProcess.bpmn20.xml",
            "processes/Integrated_Enrollment_and_Eligibility-approvalProcess.bpmn20.xml"
        ]
    )
    fun foodProgram() {
        val userTasks = listOf(
            TaskOutput("assessApplications", outputMap = mapOf("benefitProgramName" to "food")),
            TaskOutput("verifyIncome", ModelType.CMM, mapOf("sufficient_proof_of_income_response" to "is_sufficient")),
            TaskOutput("makeDetermination", ModelType.CMM, mapOf())
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "foodProcess", "approvalProcess")
        val eventItems = listOf("approvalSentMS")
        val expectedMilestones = listOf("approval sent")
        stubResponses(foodEligibilityResult = "Eligible")
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
    @CmmnDeployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.cmmn"])
    @Deployment(
        resources = [
            "processes/Integrated_Enrollment_and_Eligibility-foodProcess.bpmn20.xml",
            "processes/Integrated_Enrollment_and_Eligibility-approvalProcess.bpmn20.xml"
        ]
    )
    fun foodProgramButIncomeNotVerified() {
        val userTasks = listOf(
            TaskOutput("assessApplications", outputMap = mapOf("benefitProgramName" to "food")),
            TaskOutput("verifyIncome", ModelType.CMM, mapOf("sufficient_proof_of_income_response" to "need_integrity_review"))
//            TaskOutput("makeDetermination", ModelType.CMM, mapOf())
        )
        val stageItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage")
        val taskItems = listOf("assessApplications", "foodProcess", "sendDenialNotification")
        val eventItems = listOf("denialSentMS")
        val expectedMilestones = listOf("denial sent")
        stubResponses(foodEligibilityResult = "Eligible")
        runToCompletion(
            "failed",
            defaultProcessVariables(),
            userTasks,
            stageItems,
            taskItems,
            eventItems,
            expectedMilestones
        )
    }
}
