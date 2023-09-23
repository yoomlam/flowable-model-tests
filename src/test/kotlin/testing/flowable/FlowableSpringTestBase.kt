package testing.flowable

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.flowable.cmmn.api.CmmnHistoryService
import org.flowable.cmmn.api.CmmnManagementService
import org.flowable.cmmn.api.CmmnRepositoryService
import org.flowable.cmmn.api.CmmnRuntimeService
import org.flowable.cmmn.api.CmmnTaskService
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
import org.flowable.engine.runtime.ProcessInstance
import org.flowable.spring.impl.test.FlowableSpringExtension
import org.flowable.task.api.Task
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

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
annotation class FlowableSpringTesting

// For representing process variables consistently
typealias VarValuePair = Pair<String, Any?>
typealias VarValueMap = Map<String, Any?>

@FlowableSpringTesting
abstract class FlowableSpringTestBase {

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

    // CMMN

    @Autowired
    lateinit var cmmnRepositoryService: CmmnRepositoryService

    @Autowired
    lateinit var cmmnRuntimeService: CmmnRuntimeService

    @Autowired
    lateinit var cmmnTaskService: CmmnTaskService

    @Autowired
    lateinit var cmmnHistoryService: CmmnHistoryService

    @Autowired
    lateinit var cmmnManagementService: CmmnManagementService

    // Helper methods

    // Variables going into the process from the startEvent
    // https://github.com/navapbc/benefit-delivery-systems/blob/cdb23eb1f02a0f367cfc864ff89505dfce36e217/portal/src/pages/api/applications/index.ts#L40
    open fun defaultProcessVariables() = mapOf<String, Any>()

    // Convenience method to set additional process variables and override the default ones
    fun processVariables(vararg pairs: VarValuePair) = defaultProcessVariables() + pairs

    // Convenience method to create correctly-typed map of output variable values from tasks, i.e., UserTasks
    fun taskOutputMap(vararg pairs: VarValuePair) = mapOf(*pairs)

    fun startProcess(key: String, processVariables: VarValueMap = mapOf()): ProcessInstance =
        runtimeService.startProcessInstanceByKey(key, processVariables)

    fun getTask(taskKey: String): Task? =
        taskService.createTaskQuery().taskDefinitionKey(taskKey).singleResult()

    fun completeTask(taskKey: String, taskOutputVariables: VarValueMap = mapOf()) {
        with(getTask(taskKey)) {
            assertNotNull(this, "Cannot complete null task: $taskKey")
            taskService.complete(id, taskOutputVariables)
        }
    }

    // If process is complete, runtimeService.createVariableInstanceQuery() returns empty
    fun getVars() = historyService.createHistoricVariableInstanceQuery().list()

    // Convenience assertions

    fun assertVarEquals(varName: String, expectedValue: Any?) {
        with(historyService.createHistoricVariableInstanceQuery().variableName(varName).singleResult()) {
            assertNotNull(this, "Variable not found: $varName")
            assertEquals(expectedValue, this.value, "For variable $variableName")
        }
    }

    fun assertVarValues(vararg varValuePairs: VarValuePair) {
        val histVarValuePairs = historyService.createHistoricVariableInstanceQuery().list().associate {
            it.variableName to it.value
        }
        varValuePairs.forEach { (name, value) ->
            assert(histVarValuePairs.containsKey(name)) { "Variable not found: $name" }
            assertEquals(value, histVarValuePairs[name], "For variable $name")
        }
    }

    fun assertProcessNotComplete() =
        assertNotEquals(0, runtimeService.createProcessInstanceQuery().count())

    fun assertProcessesComplete() {
        val taskCount = taskService.createTaskQuery().count()
        assertEquals(0, taskCount, "Expecting no active tasks")

        val processCount = runtimeService.createProcessInstanceQuery().count()
        assertEquals(0, processCount, "Expecting no active process instances")
    }

    fun assertProcessCount(count: Long = 1) =
        assertEquals(
            count,
            historyService.createHistoricProcessInstanceQuery().count(),
            "Expecting $count process instances to have run"
        )

    // Activities include tasks and sequence flows
    fun assertActivitiesOccurred(expectedActivityIds: List<String>) {
        val activities = historyService.createHistoricActivityInstanceQuery().orderByHistoricActivityInstanceStartTime().asc().list()
        val activityIds = activities.map { it.activityId }
        assertEquals(expectedActivityIds, activityIds)
    }

    fun assertUserTasksOccurred(expectedUserTaskKeys: List<String>) {
        val userTasksRan = historyService.createHistoricTaskInstanceQuery().orderByHistoricTaskInstanceStartTime().asc().list()
        val userTaskKeys = userTasksRan.map { it.taskDefinitionKey }
        assertEquals(expectedUserTaskKeys, userTaskKeys)
    }
}

fun wireMockExtension(port: Int = 3456) =
    WireMockExtension.newInstance()
        .failOnUnmatchedRequests(true)
        .options(WireMockConfiguration.wireMockConfig().port(port))
        .build()
