package testing.flowable

import org.flowable.cmmn.api.CmmnHistoryService
import org.flowable.cmmn.api.CmmnManagementService
import org.flowable.cmmn.api.CmmnRepositoryService
import org.flowable.cmmn.api.CmmnRuntimeService
import org.flowable.cmmn.api.CmmnTaskService
import org.flowable.dmn.api.DmnDecisionService
import org.flowable.dmn.api.DmnRepositoryService
import org.flowable.dmn.engine.DmnEngine
import org.flowable.dmn.spring.impl.test.FlowableDmnSpringExtension
import org.flowable.engine.HistoryService
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.spring.impl.test.FlowableSpringExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(FlowableSpringExtension::class) // provides FlowableTestHelper
@ExtendWith(FlowableDmnSpringExtension::class) // provides FlowableDmnTestHelper
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [
        FlowableDmnConfiguration::class,
        FlowableCmmnConfiguration::class,
        FlowableConfiguration::class
    ]
)
annotation class FlowableSpringTesting

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
}