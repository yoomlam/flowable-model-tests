package testing.flowable.flex

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.flowable.engine.test.Deployment
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import testing.flowable.FlowableSpringTestBase
import testing.flowable.VarValueMap
import testing.flowable.wireMockExtension

@TestConfiguration
private class IntegratedEnrollmentAndEligibilityTestConfig

@Import(IntegratedEnrollmentAndEligibilityTestConfig::class)
class IntegratedEnrollmentAndEligibilityTest : FlowableSpringTestBase() {

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
        // (eligibility_result == 'PregnantWomen' || eligibility_result == 'ChildrenUnder19') ?
        // 'mixed' : 'approved' is the payload to /api/applications/$applicationId/notifications
    }

    override fun defaultProcessVariables() = mapOf(
        // applicationId is used in /api/applications/$applicationId/notifications
        "applicationId" to applicationId,
        "benefitProgramName" to "",
        // applicationIncome and householdSize are used in the payload to /api/eligibility/*
        "applicationIncome" to 3500 * 100, // in cents
        "householdSize" to 2
        // Other application fields are saved to the DB for use by other api calls -- see
        // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/prisma/schema.prisma#L30
        // The DB is queried for api/.../notifications calls
        // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/pages/api/applications/%5Bapplication_id%5D/notifications.ts#L40
        // TODO: applicationIncome and householdSize are being passed as process variables via the workflow to be
        //   passed as part the payload to only api/eligibility/* calls.
        //   When should this info be pulled from the DB, rather than as process variables?
    )

    // Tip: Go to https://bpmn-io.github.io/bpmn-js-token-simulation/modeler.html?pp=1 and open the bpmn file.
    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun defaultPathUnknownProgram() {
        stubResponses(healthcareEligibilityResult = "some unknown response from eligibility API")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenUnknownProgram", "checkHealthcareEligibility", "withHealthcareApiResponse", "healthcareResultGW",
            "whenUnknownHealthcareResult", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(defaultProcessVariables(), expectedActivities)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun eligibleForHealthcare() {
        stubResponses(healthcareEligibilityResult = "Eligible")
        val processVariables = addProcessVariables("benefitProgramName" to "healthcare")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenHealthcareProgram", "checkHealthcareEligibility", "withHealthcareApiResponse", "healthcareResultGW",
            // chooses checkHealthcareEligibility path b/c flow healthcareProgram has no 'conditionExpression' defined
            // checkHealthcareEligibility has field (responseVariableName = eligibilityResponse)
            // "healthcareResultGW" default="whenHealthcareEligible"
            "whenHealthcareEligible", "makeDetermination", "determinationMade", "sendApprovalNotification", "approvalSent", "applicationProcessed"
            // userTask id="makeDetermination" name="Makes determination" flowable:dueDate="P5D"
            // dueDate format: https://documentation.flowable.com/latest/model/forms/reference/date/index.html#formats
        )
        val userTasks = linkedMapOf("makeDetermination" to taskOutputMap())
        runToCompletion(processVariables, expectedActivities, userTasks)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun notEligibleForHealthcare() {
        stubResponses(healthcareEligibilityResult = "NotEligible")
        val processVariables = addProcessVariables("benefitProgramName" to "healthcare")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenHealthcareProgram", "checkHealthcareEligibility", "withHealthcareApiResponse", "healthcareResultGW",
            "whenNotHealthcareEligible", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(processVariables, expectedActivities)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun whenUnknownHealthcareResult() {
        stubResponses(healthcareEligibilityResult = "some unknown response from eligibility API")
        val processVariables = addProcessVariables("benefitProgramName" to "healthcare")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenHealthcareProgram", "checkHealthcareEligibility", "withHealthcareApiResponse", "healthcareResultGW",
            "whenUnknownHealthcareResult", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(processVariables, expectedActivities)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun eligibleForEnergy() {
        stubResponses()
        val processVariables = addProcessVariables("benefitProgramName" to "energy")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenEnergyProgram", "makeDetermination", "determinationMade", "sendApprovalNotification", "approvalSent", "applicationProcessed"
        )
        val userTasks = linkedMapOf("makeDetermination" to taskOutputMap())
        runToCompletion(processVariables, expectedActivities, userTasks)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun notEligibleForEnergy() {
        // No such path when benefitProgramName = energy
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun eligibleForFoodAndIncomeVerified() {
        stubResponses(foodEligibilityResult = "Eligible")
        val processVariables = addProcessVariables("benefitProgramName" to "food")
        val expectedActivities = listOf(
            "applicationSubmitted",
            "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenFoodEligible", "verifyIncome", "withIncomeVerification", "incomeVerificationGW",
            "whenIncomeVerified", "makeDetermination", "determinationMade", "sendApprovalNotification", "approvalSent", "applicationProcessed"
        )
        val userTasks = linkedMapOf(
            "verifyIncome" to taskOutputMap("sufficient_proof_of_income_response" to "is_sufficient"),
            "makeDetermination" to taskOutputMap()
        )
        runToCompletion(processVariables, expectedActivities, userTasks)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun eligibleForFoodButIncomeNotVerified() {
        stubResponses(foodEligibilityResult = "Eligible")
        val processVariables = addProcessVariables("benefitProgramName" to "food")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenFoodEligible", "verifyIncome", "withIncomeVerification", "incomeVerificationGW",
            "whenNotIncomeVerified", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/components/staff/features/task-actions/VerifyIncome.tsx#L64C20-L64C28
        val insufficientResponse = arrayOf("need_rfi", "need_integrity_review").random()
        val userTasks = linkedMapOf(
            "verifyIncome" to taskOutputMap("sufficient_proof_of_income_response" to insufficientResponse)
        )
        runToCompletion(processVariables, expectedActivities, userTasks)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun eligibleForFoodButUnknownIncomeVerificationResult() {
        stubResponses(foodEligibilityResult = "Eligible")
        val processVariables = addProcessVariables("benefitProgramName" to "food")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenFoodEligible", "verifyIncome", "withIncomeVerification", "incomeVerificationGW",
            "whenUnknownIncomeVerification", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        val userTasks = linkedMapOf(
            "verifyIncome" to taskOutputMap("sufficient_proof_of_income_response" to "some unknown response from income API")
        )
        runToCompletion(processVariables, expectedActivities, userTasks)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun notEligibleForFood() {
        stubResponses(foodEligibilityResult = "NotEligible")
        val processVariables = addProcessVariables("benefitProgramName" to "food")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenNotFoodEligible", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(processVariables, expectedActivities)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun whenUnknownFoodResult() {
        stubResponses(foodEligibilityResult = "some unknown response from eligibility API")
        val processVariables = addProcessVariables("benefitProgramName" to "food")
        val expectedActivities = listOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenUnknownFoodResult", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(processVariables, expectedActivities)
    }

    private fun runToCompletion(
        processVariables: VarValueMap,
        expectedActivities: List<String>,
        userTasks: LinkedHashMap<String, VarValueMap> = LinkedHashMap()
    ) {
        startProcess("integratedEnrollmentAndEligibilityDemo", processVariables)

        userTasks.forEach { completeTask(it.key, it.value) }
        assertUserTasksOccurred(userTasks.keys.toList())

        assertProcessesComplete()
        assertActivitiesOccurred(expectedActivities)
    }
}
