package testing.flowable.flex

import org.assertj.core.api.Assertions
import org.flowable.dmn.engine.test.DmnDeployment
import org.flowable.engine.test.Deployment
import org.junit.jupiter.api.Test
import testing.flowable.FlowableSpringTestBase
import kotlin.test.assertContains
import kotlin.test.assertEquals

class IeeBasicDmnTest : FlowableSpringTestBase() {

    @Test
    @DmnDeployment(resources = ["processes/healthcareDecisionTableAdults.dmn"])
    fun healthcareAdultsDmnTest() {
        val executionResult = decisionService.createExecuteDecisionBuilder()
            .decisionKey("healthcareDecisionTableAdults")
            .variable("household_size", "3")
            .variable("monthly_income_in_cents", "350000")
            .executeWithSingleResult()
        Assertions.assertThat(executionResult).containsEntry("medicaid_for_adults", false)

        val executionResult2 = decisionService.createExecuteDecisionBuilder()
            .decisionKey("healthcareDecisionTableAdults")
            .variable("household_size", "3")
            .variable("monthly_income_in_cents", "270000")
            .executeWithSingleResult()
        Assertions.assertThat(executionResult2).containsEntry("medicaid_for_adults", true)
    }

    @Test
    @Deployment(resources = ["processes/Integrated_Enrollment_and_Eligibility-healthcareProcess.bpmn20.xml"])
    @DmnDeployment(
        resources = [
            "processes/healthcareDecisionTableAdults.dmn",
            "processes/healthcareDecisionTablePregWomen.dmn",
            "processes/healthcareDecisionTableChildren.dmn",
            "processes/eligibilityDecisionTable.dmn"
        ]
    )
    fun healthcareDmnResultEqualsAdultTest() {
        startProcess(
            "integratedEnrollmentAndEligibilityHealthcareProcess",
            mapOf(
                "household_size" to "3",
                "monthly_income_in_cents" to "270000"
            )
        )

        // Check process variables
        assertVarValues(
            "pregnant_women" to true,
            "medicaid_for_adults" to true,
            "children_under_19" to true,
            "eligibility_result" to "Adults"
        )

        checkExpectations()
    }

    @Test
    @Deployment(resources = ["processes/Integrated_Enrollment_and_Eligibility-healthcareProcess.bpmn20.xml"])
    @DmnDeployment(
        resources = [
            "processes/healthcareDecisionTableAdults.dmn",
            "processes/healthcareDecisionTablePregWomen.dmn",
            "processes/healthcareDecisionTableChildren.dmn",
            "processes/eligibilityDecisionTable.dmn"
        ]
    )
    fun healthcareDmnResultEqualsPregWomanTest() {
        startProcess(
            "integratedEnrollmentAndEligibilityHealthcareProcess",
            mapOf(
                "household_size" to "3",
                "monthly_income_in_cents" to "350000"
            )
        )

        // Check process variables
        assertVarValues(
            "medicaid_for_adults" to false,
            "pregnant_women" to true,
            "children_under_19" to true,
            "eligibility_result" to "PregnantWomen"
        )

        checkExpectations()
    }

    @Test
    @Deployment(resources = ["processes/Integrated_Enrollment_and_Eligibility-healthcareProcess.bpmn20.xml"])
    @DmnDeployment(
        resources = [
            "processes/healthcareDecisionTableAdults.dmn",
            "processes/healthcareDecisionTablePregWomen.dmn",
            "processes/healthcareDecisionTableChildren.dmn",
            "processes/eligibilityDecisionTable.dmn"
        ]
    )
    fun healthcareDmnResultEqualsChildrenTest() {
        startProcess(
            "integratedEnrollmentAndEligibilityHealthcareProcess",
            mapOf(
                "household_size" to "3",
                "monthly_income_in_cents" to "640000"
            )
        )

        // Check process variables
        assertVarValues(
            "medicaid_for_adults" to false,
            "pregnant_women" to false,
            "children_under_19" to true,
            "eligibility_result" to "ChildrenUnder19"
        )

        checkExpectations()
    }

    @Test
    @Deployment(resources = ["processes/Integrated_Enrollment_and_Eligibility-healthcareProcess.bpmn20.xml"])
    @DmnDeployment(
        resources = [
            "processes/healthcareDecisionTableAdults.dmn",
            "processes/healthcareDecisionTablePregWomen.dmn",
            "processes/healthcareDecisionTableChildren.dmn",
            "processes/eligibilityDecisionTable.dmn"
        ]
    )
    fun healthcareDmnResultEqualsNotEligible() {
        startProcess(
            "integratedEnrollmentAndEligibilityHealthcareProcess",
            mapOf(
                "household_size" to "3",
                "monthly_income_in_cents" to "700000"
            )
        )

        // Check process variables
        assertVarValues(
            "medicaid_for_adults" to false,
            "pregnant_women" to false,
            "children_under_19" to false,
            "eligibility_result" to "NotEligible"
        )

        checkExpectations()
    }

    private fun checkExpectations() {
        assertEquals(7, getVars().size)
        assertProcessesComplete()

        val activityIds = getActivitiesOccurred().map { it.activityId }
        assertContains(activityIds, "adultsDT")
        assertContains(activityIds, "pregWomenDT")
        assertContains(activityIds, "childrenDT")
        assertContains(activityIds, "eligibilityDecisionTable")
    }
}
