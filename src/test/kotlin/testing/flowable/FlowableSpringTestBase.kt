package testing.flowable

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.flowable.cmmn.spring.impl.test.FlowableCmmnSpringExtension
import org.flowable.dmn.api.DmnDecisionService
import org.flowable.dmn.api.DmnRepositoryService
import org.flowable.dmn.engine.DmnEngine
import org.flowable.dmn.spring.impl.test.FlowableDmnSpringExtension
import org.flowable.engine.HistoryService
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.engine.history.HistoricActivityInstance
import org.flowable.engine.runtime.ProcessInstance
import org.flowable.spring.impl.test.FlowableSpringExtension
import org.flowable.task.api.Task
import org.flowable.task.api.history.HistoricTaskInstance
import org.flowable.variable.api.history.HistoricVariableInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

private val log = mu.KotlinLogging.logger {}

@ExtendWith(SpringExtension::class)
@ExtendWith(FlowableSpringExtension::class) // provides FlowableTestHelper
@ExtendWith(FlowableDmnSpringExtension::class) // provides FlowableDmnTestHelper
@ExtendWith(FlowableCmmnSpringExtension::class) // provides FlowableCmmnTestHelper
@ContextConfiguration(
    classes = [
        FlowableConfiguration::class,
        FlowableDmnConfiguration::class,
        FlowableCmmnConfiguration::class
    ]
)
// Convenience annotation that includes all the above
annotation class FlowableSpringTesting

fun wireMockExtension(port: Int = 3456): WireMockExtension =
    WireMockExtension.newInstance()
        .failOnUnmatchedRequests(true)
        .options(WireMockConfiguration.wireMockConfig().port(port))
        .build()

// For representing process variables consistently
typealias VarValuePair = Pair<String, Any?>
typealias VarValueMap = Map<String, Any?>

@FlowableSpringTesting
abstract class FlowableSpringTestBase : FlowableSpringCmmnTestBase() {

    // BPMN

    @Autowired
    lateinit var processEngine: ProcessEngine

    @Autowired
    lateinit var runtimeService: RuntimeService

    @Autowired
    lateinit var taskService: TaskService

    @Autowired
    lateinit var repositoryService: RepositoryService

    @Autowired
    lateinit var historyService: HistoryService

    // DMN

    @Autowired
    lateinit var dmnEngine: DmnEngine

    @Autowired
    lateinit var decisionService: DmnDecisionService

    @Autowired
    lateinit var dmnRepositoryService: DmnRepositoryService

    // Helper methods for BPMN models

    // Variables going into the process from the startEvent
    // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/pages/api/applications/index.ts#L40
    open fun defaultProcessVariables(): VarValueMap = mapOf()

    // Convenience method to set additional process variables and override the default ones
    fun addProcessVariables(vararg pairs: VarValuePair) = defaultProcessVariables() + pairs

    // Convenience method to create correctly-typed map of output variable values from tasks, i.e., UserTasks
    fun taskOutputMap(vararg pairs: VarValuePair) = mapOf(*pairs)

    enum class ModelType { BPM, CMM }

    data class TaskOutput(
        val taskKey: String,
        val modelType: ModelType = ModelType.BPM,
        val outputMap: VarValueMap = mapOf()
    )

    fun userTaskOutputPair(modelType: ModelType = ModelType.BPM, vararg pairs: VarValuePair) =
        modelType to mapOf(*pairs)

    fun startProcess(key: String, processVariables: VarValueMap = mapOf()): ProcessInstance =
        runtimeService.startProcessInstanceByKey(key, processVariables)

    fun getTask(taskKey: String): Task? =
        taskService.createTaskQuery().taskDefinitionKey(taskKey).singleResult()

    fun completeTask(taskKey: String, taskOutputVariables: VarValueMap = mapOf()) =
        getTask(taskKey).apply {
            assertNotNull(this, "Cannot complete null task: $taskKey")
            log.info("  Completing $taskKey")
            taskService.complete(id, taskOutputVariables)
        }

    // If process is complete, runtimeService.createVariableInstanceQuery() returns empty
    fun getVars(): MutableList<HistoricVariableInstance> =
        historyService.createHistoricVariableInstanceQuery().list().apply {
            log.info { this.joinToString(prefix = "  vars: ") { "${it.variableName} = ${it.value}" } }
        }

    // Convenience assertions

    fun assertVarEquals(varName: String, expectedValue: Any?) =
        historyService.createHistoricVariableInstanceQuery().variableName(varName).singleResult().apply {
            assertNotNull(this, "Variable not found: $varName")
            assertEquals(expectedValue, this.value, "For variable $variableName")
        }

    fun assertVarValues(vararg varValuePairs: VarValuePair) =
        historyService.createHistoricVariableInstanceQuery().list().associate {
            it.variableName to it.value
        }.apply {
            varValuePairs.forEach { (name, value) ->
                assert(this.containsKey(name)) { "Variable not found: $name" }
                assertEquals(value, this[name], "For variable $name")
            }
        }

    fun assertProcessNotComplete() =
        assertNotEquals(0, runtimeService.createProcessInstanceQuery().count())

    fun assertProcessesComplete(count: Long = 1) {
        val taskCount = taskService.createTaskQuery().count()
        assertEquals(0, taskCount, "Expecting no active tasks")

        val processCount = runtimeService.createProcessInstanceQuery().count()
        assertEquals(0, processCount, "Expecting no active process instances")

        val processesRan = historyService.createHistoricProcessInstanceQuery().count()
        assertEquals(count, processesRan, "Expecting $count process instances to have run")
    }

    // Activities include tasks and sequence flows
    fun getActivitiesOccurred(): MutableList<HistoricActivityInstance> =
        historyService.createHistoricActivityInstanceQuery().orderByHistoricActivityInstanceStartTime()
            .asc().list().apply {
                log.info { this.joinToString(prefix = "  activities: ") { it.activityId } }
            }

    fun assertActivitiesOccurred(expectedActivityIds: List<String>) =
        getActivitiesOccurred().apply {
            val activityIds = this.map { it.activityId }
            assertEquals(expectedActivityIds, activityIds)
        }

    fun assertUserTasksOccurred(expectedUserTaskKeys: List<String>): MutableList<HistoricTaskInstance> =
        historyService.createHistoricTaskInstanceQuery().orderByHistoricTaskInstanceStartTime().asc().list().apply {
            val userTaskKeys = this.map { it.taskDefinitionKey }
            assertEquals(expectedUserTaskKeys, userTaskKeys)
        }
}
