package testing.flowable

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
import org.flowable.task.api.Task
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

private val log = mu.KotlinLogging.logger {}

abstract class FlowableSpringCmmnTestBase {

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

    fun startCmmCase(key: String, processVariables: VarValueMap = mapOf()): CaseInstance =
        cmmnRuntimeService.createCaseInstanceBuilder()
            .caseDefinitionKey(key).variables(processVariables)
            .start().also {
                log.info { "==== Starting CMM $key" }
            }

    fun getCmmnTask(taskKey: String): Task? =
        cmmnTaskService.createTaskQuery().taskDefinitionKey(taskKey).singleResult()

    fun completeCmmnTask(taskKey: String, taskOutputVariables: VarValueMap = mapOf()) =
        getCmmnTask(taskKey).apply {
            assertNotNull(this, "Cannot complete null task: $taskKey")
            log.info("  Completing $taskKey")
            cmmnTaskService.complete(id, taskOutputVariables)
        }

    fun assertCmmnCaseNotComplete() =
        assertNotEquals(0, cmmnRuntimeService.createCaseInstanceQuery().count())

    fun assertCmmnCaseComplete(count: Long = 1) {
        // Expecting no active tasks"
        assertCmmnActiveTasks(listOf())

        val activeCaseCount = cmmnRuntimeService.createCaseInstanceQuery().count()
        assertEquals(0, activeCaseCount, "Expecting no active case instances")

        val casesRan = cmmnHistoryService.createHistoricCaseInstanceQuery().count()
        assertEquals(count, casesRan, "Expecting $count case instances to have run")
    }

    fun assertCmmnActiveUserTasks(caseInstance: CaseInstance, vararg expectedUserTaskKeys: String) =
        cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.id).orderByTaskCreateTime().asc().list().apply {
            val userTaskKeys = this.map { it.taskDefinitionKey }
            log.info("  userTasks: $userTaskKeys")
            assertEquals(expectedUserTaskKeys.toList(), userTaskKeys)
        }

    fun assertCmmnMilestonesOccurred(expectedMilestones: List<String> = listOf()) =
        cmmnHistoryService.createHistoricMilestoneInstanceQuery().orderByTimeStamp().asc().list().apply {
            val milestoneNames = this.map { it.name }
            log.info("  milestones: $milestoneNames")
            assertEquals(expectedMilestones, milestoneNames)
        }

    // Practically every CMMN element is a PlanItem: Stage, Task, EventListener, Milestone
    // See https://documentation.flowable.com/latest/model/cmmn/introduction/part2-tasks-and-plan-items
    private val planItemStates = arrayOf(
        "available",
        "active",
        "enabled",
        "disabled",
        "failed",
        "suspended",
        "terminated",
        "completed"
    )

    // This prints the state of all PlanItems, useful for diagnostics
    fun assertCmmnPlanItems(expectedPlanItemCount: Int = -1): MutableList<HistoricPlanItemInstance> =
        cmmnHistoryService.createHistoricPlanItemInstanceQuery().orderByCreateTime().asc().list().apply {
            val itemSet = this.toMutableSet()
            val itemStateMap = planItemStates.associateWith { state ->
                // remove items with given state
                val filteredItems = itemSet.filter { it.state == state }
                itemSet -= filteredItems.toSet()
                // event-related items "occur", whereas stages/tasks items "complete"
                filteredItems.sortedBy { it.completedTime ?: it.occurredTime }.map { it.planItemDefinitionId }
            }
            log.info(itemStateMap.entries.joinToString("\n", prefix = "  planItems by state: \n"))
            assertEquals(setOf(), itemSet, "PlanItems with unaccounted state")

            if (expectedPlanItemCount >= 0) {
                assertEquals(expectedPlanItemCount, this.size)
            }
        }

    fun assertCmmnActiveStage(stagePlanItemIds: List<String>): MutableList<HistoricPlanItemInstance> =
        cmmnHistoryService.createHistoricPlanItemInstanceQuery().onlyStages()
            .planItemInstanceState("active").orderByLastStartedTime().asc().list().apply {
                val activeStageItemIds = this.map { it.planItemDefinitionId }
                assertEquals(stagePlanItemIds, activeStageItemIds, "Active stages")
            }

    fun assertCmmnActiveTasks(taskPlanItemIds: List<String>): List<HistoricPlanItemInstance> {
        val activeItems = cmmnHistoryService.createHistoricPlanItemInstanceQuery()
            .planItemInstanceState("active").orderByLastStartedTime().asc().list()
        // filter out event-related items (which "occur") and stage items
        return activeItems.filter { !it.isStage && it.occurredTime == null }.apply {
            val activeTaskItemIds = map { it.planItemDefinitionId }
            assertEquals(taskPlanItemIds, activeTaskItemIds, "Active tasks")
        }
    }
    fun assertCmmnTasksCompleted(taskPlanItemIds: List<String>): List<HistoricPlanItemInstance> {
        val completedItems = cmmnHistoryService.createHistoricPlanItemInstanceQuery()
            .planItemInstanceState("completed").orderByCompletedTime().asc().list()
        // filter out event-related items (which "occur") and stage items
        return completedItems.filter { !it.isStage && it.occurredTime == null }.apply {
            val completedTaskItemIds = map { it.planItemDefinitionId }
            assertEquals(taskPlanItemIds, completedTaskItemIds, "Completed tasks")
        }
    }

    fun assertCmmnStagesCompleted(stagePlanItemIds: List<String>): List<HistoricPlanItemInstance> =
        cmmnHistoryService.createHistoricPlanItemInstanceQuery().onlyStages()
            .planItemInstanceState("completed").orderByCompletedTime().asc().list().apply {
                val completedStageItemIds = this.map { it.planItemDefinitionId }
                assertEquals(stagePlanItemIds, completedStageItemIds, "Completed stages")
            }

    // event-related items "occur", whereas stages/tasks "complete"
    fun assertCmmnEventOccurred(eventPlanItemIds: List<String>, inOrder: Boolean = true): List<HistoricPlanItemInstance> {
        val completeItems = cmmnHistoryService.createHistoricPlanItemInstanceQuery()
            .planItemInstanceState("completed").orderByOccurredTime().asc().list()
        // filter out non-event-related items (which don't have occurTime) and stage items
        return completeItems.filter { !it.isStage && it.occurredTime != null }.apply {
            val eventItemIds = map { it.planItemDefinitionId }
            if (inOrder) {
                assertEquals(eventPlanItemIds, eventItemIds, "Occurred events")
            } else {
                assertEquals(eventPlanItemIds.sorted(), eventItemIds.sorted(), "Occurred events")
            }
        }
    }

    // Haven't found a use for this
    // Copied from https://github.com/flowable/flowable-engine/blob/0052eb63aee1d831c3a527c2c64c96cbae7a4eaa/modules/flowable-cmmn-engine/src/test/java/org/flowable/cmmn/test/sentry/TriggerModeSentryTest.java#L124
    fun assertCmmnSentryPartCount(caseInstance: CaseInstance, expectedCount: Int): MutableList<SentryPartInstanceEntity> =
        cmmnEngineConfiguration.commandExecutor.execute {
            CommandContextUtil.getSentryPartInstanceEntityManager(it)
                .findSentryPartInstancesByCaseInstanceId(caseInstance.id)
        }.apply {
            log.info("  sentryPart: " + this.map { it.planItemInstanceId })
            assertEquals(expectedCount, this.size)
        }
}
