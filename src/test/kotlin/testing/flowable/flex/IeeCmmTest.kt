package testing.flowable.flex

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.flowable.cmmn.engine.test.CmmnDeployment
import org.flowable.dmn.engine.test.DmnDeployment
import org.flowable.engine.test.Deployment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import testing.flowable.VarValueMap
import testing.flowable.simple.TestService
import testing.flowable.wireMockExtension
import kotlin.test.assertEquals

@TestConfiguration
private class IeeCmmConfig {
    // `someService` is referenced in the CMM XML file
    @Bean
    fun someService(): TestService = TestService("in ${this::class.simpleName}")
}

@Import(IeeCmmConfig::class)
class IeeCmmTest : FlowableSpringTestBase() {
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

    @Test
    @CmmnDeployment(resources = ["processes/iee.cmmn"])
    @Deployment(
        resources = [
            "processes/iee-processHealthcare.bpmn",
            "processes/iee-processApproval.bpmn"
        ]
    )
    @DmnDeployment(
        resources = [
            "processes/iee-healthcareDecisionAdults.dmn",
            "processes/iee-healthcareDecisionPregWomen.dmn",
            "processes/iee-healthcareDecisionChildren.dmn",
            "processes/iee-healthcareDecisionEligibility.dmn"
        ]
    )
    fun healthcareProgram() {
        stubResponses(healthcareEligibilityResult = "Eligible")
        runToCompletion(
            "passed",
            userTasks = listOf(
                TaskOutput("verifySubmission", outputMap = mapOf("benefitProgramName" to "healthcare")),
                TaskOutput("makeDetermination", ModelType.CMM, outputMap = mapOf())
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("verifySubmission", "healthcareProcess", "approvalProcess"),
            expectedEventPlanItems = listOf("approvalSentMS"),
            expectedMilestones = listOf("approval sent")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/iee.cmmn"])
    @Deployment(resources = ["processes/iee-processApproval.bpmn"])
    fun energyProgram() {
        val assessmentResultValue = "passed"
        stubResponses()
        runToCompletion(
            assessmentResultValue,
            userTasks = listOf(
                TaskOutput("verifySubmission", outputMap = mapOf("benefitProgramName" to "energy")),
                TaskOutput("energySvc", outputMap = mapOf("assessmentResult" to assessmentResultValue)),
                TaskOutput("makeDetermination", ModelType.CMM, outputMap = mapOf())
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("verifySubmission", "energySvc", "approvalProcess"),
            expectedEventPlanItems = listOf("approvalSentMS"),
            expectedMilestones = listOf("approval sent")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/iee.cmmn"])
    @Deployment(resources = ["processes/iee-processApproval.bpmn"])
    fun energyProgramFailed() {
        val assessmentResultValue = "failed"
        stubResponses()
        runToCompletion(
            assessmentResultValue,
            userTasks = listOf(
                TaskOutput("verifySubmission", outputMap = mapOf("benefitProgramName" to "energy")),
                TaskOutput("energySvc", outputMap = mapOf("assessmentResult" to assessmentResultValue))
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("verifySubmission", "energySvc", "sendDenialNotification"),
            expectedEventPlanItems = listOf("denialSentMS"),
            expectedMilestones = listOf("denial sent")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/iee.cmmn"])
    @Deployment(
        resources = [
            "processes/iee-processFood.bpmn",
            "processes/iee-processApproval.bpmn"
        ]
    )
    fun foodProgram() {
        stubResponses(foodEligibilityResult = "Eligible")
        runToCompletion(
            "passed",
            userTasks = listOf(
                TaskOutput("verifySubmission", outputMap = mapOf("benefitProgramName" to "food")),
                TaskOutput(
                    "verifyIncome",
                    ModelType.CMM,
                    mapOf("sufficient_proof_of_income_response" to "is_sufficient")
                ),
                TaskOutput("makeDetermination", ModelType.CMM, mapOf())
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("verifySubmission", "foodProcess", "approvalProcess"),
            expectedEventPlanItems = listOf("approvalSentMS"),
            expectedMilestones = listOf("approval sent")
        )
    }

    @Test
    @CmmnDeployment(resources = ["processes/iee.cmmn"])
    @Deployment(
        resources = [
            "processes/iee-processFood.bpmn",
            "processes/iee-processApproval.bpmn"
        ]
    )
    fun foodProgramButIncomeNotVerified() {
        stubResponses(foodEligibilityResult = "Eligible")
        runToCompletion(
            "failed",
            userTasks = listOf(
                TaskOutput("verifySubmission", ModelType.BPM, mapOf("benefitProgramName" to "food")),
                TaskOutput(
                    "verifyIncome",
                    ModelType.CMM,
                    mapOf("sufficient_proof_of_income_response" to "need_integrity_review")
                )
            ),
            expectedStagePlanItems = listOf("submissionStage", "assessSubmissionStage", "decisionStage"),
            expectedTaskPlanItems = listOf("verifySubmission", "foodProcess", "sendDenialNotification"),
            expectedEventPlanItems = listOf("denialSentMS"),
            expectedMilestones = listOf("denial sent")
        )
    }

    private fun runToCompletion(
        assessmentResultValue: String,
        processVariables: VarValueMap = defaultProcessVariables(),
        userTasks: List<TaskOutput> = listOf(),
        expectedStagePlanItems: List<String>,
        expectedTaskPlanItems: List<String>,
        expectedEventPlanItems: List<String>,
        expectedMilestones: List<String> = listOf()
    ) {
        val caseInstance = startCmmCase("ieeCMMN", processVariables)

        assertCmmnPlanItems(4)
        assertCmmnActiveStage(listOf("submissionStage"))
        assertEquals(4, getVars().size)

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
