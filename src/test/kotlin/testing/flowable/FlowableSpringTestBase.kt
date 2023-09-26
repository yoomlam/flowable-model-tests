package testing.flowable

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.flowable.cmmn.api.CmmnHistoryService
import org.flowable.cmmn.api.CmmnManagementService
import org.flowable.cmmn.api.CmmnRepositoryService
import org.flowable.cmmn.api.CmmnRuntimeService
import org.flowable.cmmn.api.CmmnTaskService
import org.flowable.cmmn.api.history.HistoricPlanItemInstance
import org.flowable.cmmn.api.runtime.CaseInstance
import org.flowable.cmmn.engine.CmmnEngineConfiguration
import org.flowable.cmmn.engine.impl.persistence.entity.SentryPartInstanceEntity
import org.flowable.cmmn.engine.impl.util.CommandContextUtil
import org.flowable.cmmn.spring.impl.test.FlowableCmmnSpringExtension
import org.flowable.common.engine.impl.interceptor.Command
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
    lateinit var cmmnEngineConfiguration: CmmnEngineConfiguration

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

    fun startCmmCase(key: String, processVariables: VarValueMap = mapOf()): CaseInstance =
        cmmnRuntimeService.createCaseInstanceBuilder()
            .caseDefinitionKey(key).variables(processVariables)
            .start()

    fun getTask(taskKey: String): Task? =
        taskService.createTaskQuery().taskDefinitionKey(taskKey).singleResult()

    fun completeTask(taskKey: String, taskOutputVariables: VarValueMap = mapOf()) {
        with(getTask(taskKey)) {
            assertNotNull(this, "Cannot complete null task: $taskKey")
            log.info("  Completing $taskKey")
            taskService.complete(id, taskOutputVariables)
        }
    }

    fun completeCmmnTask(taskKey: String, taskOutputVariables: VarValueMap = mapOf()) {
        with(getTask(taskKey)) {
            assertNotNull(this, "Cannot complete null task: $taskKey")
            log.info("  Completing $taskKey")
            cmmnTaskService.complete(id, taskOutputVariables)
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

    fun assertCmmnCaseNotComplete() =
        assertNotEquals(0, cmmnRuntimeService.createCaseInstanceQuery().count())

    fun assertCmmnCaseComplete() {
//        val taskCount = taskService.createTaskQuery().count()
//        assertEquals(0, taskCount, "Expecting no active tasks")

        val caseCount = cmmnRuntimeService.createCaseInstanceQuery().count()
        assertEquals(0, caseCount, "Expecting no active case instances")
    }

    fun assertCmmnCaseCount(count: Long = 1) =
        assertEquals(
            count,
            cmmnHistoryService.createHistoricCaseInstanceQuery().count(),
            "Expecting $count case instances to have run"
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

    fun assertCmmnMilestonesOccurred(expectedMilestoneIds: List<String>) {
        val milestones = cmmnHistoryService.createHistoricMilestoneInstanceQuery().orderByTimeStamp().asc().list()
        val milestoneIds = milestones.map { it.elementId }
        assertEquals(expectedMilestoneIds, milestoneIds)
    }

    fun assertCmmnActiveUserTasks(caseInstance: CaseInstance, vararg expectedUserTaskKeys: String) {
        val activeUserTasks = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.id).orderByTaskCreateTime().asc().list()
        val userTaskKeys = activeUserTasks.map { it.taskDefinitionKey }
        log.info("  userTaskKeys: $userTaskKeys")
        assertEquals(expectedUserTaskKeys.toList(), userTaskKeys)
    }

    fun assertPlanItems(expectedPlanItemCount: Int = -1): MutableList<HistoricPlanItemInstance> {
        val items = cmmnHistoryService.createHistoricPlanItemInstanceQuery().orderByCreateTime().asc().list()
        val itemIds = items.map { it.name }
        log.info("  planItems: $itemIds")
        if (expectedPlanItemCount >= 0) {
            assertEquals(expectedPlanItemCount, items.size)
        }
        return items
    }

    fun assertPlanItemsExecuted(newPlanItemNames: List<String>, startupItems: MutableList<HistoricPlanItemInstance>): MutableList<HistoricPlanItemInstance> {
        val items = assertPlanItems()
        startupItems.forEach { removePlanItem(items, it) }
        val itemIds = items.map { it.name }
        assertEquals(newPlanItemNames, itemIds)
        return items
    }

    private fun removePlanItem(
        items: MutableList<HistoricPlanItemInstance>,
        item: HistoricPlanItemInstance
    ) {
        val foundItem = items.find {
            it.name == item.name &&
                it.elementId == item.elementId &&
                it.caseInstanceId == item.caseInstanceId &&
                it.caseDefinitionId == item.caseDefinitionId &&
                it.derivedCaseDefinitionId == item.derivedCaseDefinitionId &&
                it.isStage == item.isStage &&
                it.stageInstanceId == item.stageInstanceId &&
                it.planItemDefinitionId == item.planItemDefinitionId &&
                it.planItemDefinitionType == item.planItemDefinitionType &&
                it.createTime == item.createTime
        }
        if (foundItem == null) {
            log.warn("Couldn't find ${item.name}")
        } else {
            items.remove(foundItem)
        }
    }

    // Copied from https://github.com/flowable/flowable-engine/blob/0052eb63aee1d831c3a527c2c64c96cbae7a4eaa/modules/flowable-cmmn-engine/src/test/java/org/flowable/cmmn/test/sentry/TriggerModeSentryTest.java#L124
    fun assertSentryPartInstanceCount(caseInstance: CaseInstance, expectedCount: Int) {
        val sentryPartInstanceEntities = cmmnEngineConfiguration.commandExecutor.execute {
            CommandContextUtil.getSentryPartInstanceEntityManager(it)
                .findSentryPartInstancesByCaseInstanceId(caseInstance.id)
        }
        log.info("  sentryPart: " + sentryPartInstanceEntities.map { it.planItemInstanceId })
        assertEquals(expectedCount, sentryPartInstanceEntities.size)
    }
}

fun wireMockExtension(port: Int = 3456) =
    WireMockExtension.newInstance()
        .failOnUnmatchedRequests(true)
        .options(WireMockConfiguration.wireMockConfig().port(port))
        .build()
