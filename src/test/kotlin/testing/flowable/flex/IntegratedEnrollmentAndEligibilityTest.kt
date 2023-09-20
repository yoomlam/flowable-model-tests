package testing.flowable.flex

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.flowable.engine.HistoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.engine.test.Deployment
import org.flowable.spring.impl.test.FlowableSpringExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import testing.flowable.FlowableConfiguration
import testing.flowable.simple.SimpleConfiguration

@ExtendWith(FlowableSpringExtension::class)
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [
        FlowableConfiguration::class,
        SimpleConfiguration::class,
        IntegratedEnrollmentAndEligibilityTest.MySpecificConfig::class
    ]
)
class IntegratedEnrollmentAndEligibilityTest {

    @TestConfiguration
    class MySpecificConfig

    companion object {
        const val applicationId = 20230919

        @RegisterExtension
        @JvmStatic
        val mockApi: WireMockExtension = WireMockExtension.newInstance()
            .failOnUnmatchedRequests(true)
            .options(WireMockConfiguration.wireMockConfig().port(3000))
            .build()
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

    @Autowired
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var historyService: HistoryService

    // Variables going into the process from the startEvent
    // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/pages/api/applications/index.ts#L40
    private val defaultProcessVariables = mapOf(
        // applicationId is used in /api/applications/$applicationId/notifications
        "applicationId" to applicationId,
        "benefitProgramName" to "healthcare",
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

    // Convenience method to create correct-typed map of output variable values from tasks, i.e., UserTasks
    private fun taskOutputMap(vararg pairs: Pair<String, Any>) = mapOf(*pairs)

    // Tip: Go to https://bpmn-io.github.io/bpmn-js-token-simulation/modeler.html?pp=1 and open the bpmn file.
    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun defaultPath() {
        stubResponses()
        val expectedActivities = arrayOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenHealthcareProgram", "checkHealthcareEligibility", "withHealthcareApiResponse", "healthcareResultGW",
            "whenUnknownHealthcareResult", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(defaultProcessVariables, expectedActivities)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun eligibleForHealthcare() {
        stubResponses(healthcareEligibilityResult = "Eligible")
        val expectedActivities = arrayOf(
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
        runToCompletion(defaultProcessVariables, expectedActivities, userTasks)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun notEligibleForHealthcare() {
        stubResponses(healthcareEligibilityResult = "NotEligible")
        val expectedActivities = arrayOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenHealthcareProgram", "checkHealthcareEligibility", "withHealthcareApiResponse", "healthcareResultGW",
            "whenNotHealthcareEligible", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(defaultProcessVariables, expectedActivities)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun whenUnknownHealthcareResult() {
        stubResponses(healthcareEligibilityResult = "some unknown response from eligibility API")
        val processVariables = defaultProcessVariables
        val expectedActivities = arrayOf(
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
        val processVariables = defaultProcessVariables + mapOf("benefitProgramName" to "energy")
        val expectedActivities = arrayOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenEnergyProgram", "makeDetermination", "determinationMade", "sendApprovalNotification", "approvalSent", "applicationProcessed"
        )
        val userTasks = linkedMapOf("makeDetermination" to taskOutputMap())
        runToCompletion(processVariables, expectedActivities, userTasks)
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun notEligibleForEnergy() {
        // TODO: No such path when benefitProgramName = energy
    }

    @Test
    @Deployment(resources = ["processes/integratedEnrollmentAndEligibility_LOCALHOST.bpmn"])
    fun eligibleForFoodAndIncomeVerified() {
        stubResponses(foodEligibilityResult = "Eligible")
        val processVariables = defaultProcessVariables + mapOf("benefitProgramName" to "food")
        val expectedActivities = arrayOf(
            "applicationSubmitted",
            "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenEligibleFood", "verifyIncome", "withIncomeVerification", "incomeVerificationGW",
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
        val processVariables = defaultProcessVariables + mapOf("benefitProgramName" to "food")
        val expectedActivities = arrayOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenEligibleFood", "verifyIncome", "withIncomeVerification", "incomeVerificationGW",
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
        val processVariables = defaultProcessVariables + mapOf("benefitProgramName" to "food")
        val expectedActivities = arrayOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenEligibleFood", "verifyIncome", "withIncomeVerification", "incomeVerificationGW",
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
        val processVariables = defaultProcessVariables + mapOf("benefitProgramName" to "food")
        val expectedActivities = arrayOf(
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
        val processVariables = defaultProcessVariables + mapOf("benefitProgramName" to "food")
        val expectedActivities = arrayOf(
            "applicationSubmitted", "withApplication", "programTypeGW",
            "whenFoodProgram", "checkFoodEligibility", "withFoodApiResponse", "foodResultGW",
            "whenUnknownFoodResult", "sendDenialNotification", "denialSent", "applicationProcessed"
        )
        runToCompletion(processVariables, expectedActivities)
    }

    private fun completeTask(taskKey: String, taskOutputVariables: Map<String, Any>) {
        val task = taskService.createTaskQuery().taskDefinitionKey(taskKey).singleResult()
        assertThat(task).`as`("Task $taskKey").isNotNull
        taskService.complete(task.id, taskOutputVariables)
    }

    private fun runToCompletion(
        processVariables: Map<String, Any>,
        expectedActivities: Array<String>,
        userTasks: LinkedHashMap<String, Map<String, Any>> = LinkedHashMap()
    ) {
        // Start process with processVariables
        runtimeService.startProcessInstanceByKey("integratedEnrollmentAndEligibilityDemo", processVariables)

        userTasks.forEach { completeTask(it.key, it.value) }

        // All tasks are complete, so there are no active tasks
        assertThat(taskService.createTaskQuery().list()).isEmpty()
        // Process instance is now completed
        assertThat(runtimeService.createProcessInstanceQuery().list()).isEmpty()
        // Check history
        assertThat(historyService.createHistoricProcessInstanceQuery().count()).isEqualTo(1)

        // Activities have tasks and sequenceFlows that were executed
        val activities = historyService.createHistoricActivityInstanceQuery().orderByHistoricActivityInstanceStartTime().asc().list()
        val activityIds = activities.map { it.activityId }
        assertThat(activityIds).containsSequence(*expectedActivities)

        // Check userTasks that were ran
        val userTasksRan = historyService.createHistoricTaskInstanceQuery().orderByHistoricTaskInstanceStartTime().asc().list()
        val userTaskKeys = userTasksRan.map { it.taskDefinitionKey }
        assertThat(userTaskKeys).containsSequence(userTasks.keys.asIterable())
    }
}
