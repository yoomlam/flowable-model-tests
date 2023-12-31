package testing.flowable

import org.flowable.common.engine.impl.EngineConfigurator
import org.flowable.engine.HistoryService
import org.flowable.engine.ManagementService
import org.flowable.engine.ProcessEngine
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.flowable.engine.TaskService
import org.flowable.spring.ProcessEngineFactoryBean
import org.flowable.spring.SpringProcessEngineConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.time.Instant
import javax.sql.DataSource

// Refer to https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-spring/src/test/java/org/flowable/spring/test/jupiter/SpringJunitJupiterTest.java#L104
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
class FlowableConfiguration {
    @Bean
    fun dataSource(): DataSource = SimpleDriverDataSource().apply {
        setDriverClass(org.h2.Driver::class.java)
        // Use a suffix for the DB name so that tests do not interfere with each other's DB
        val suffix = Instant.now().toEpochMilli()
        // Added DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE for in-memory DB to avoid exceptions during ShutdownHook
        // https://github.com/camunda-community-hub/micronaut-camunda-platform-7/issues/91#issuecomment-703154719
        url = "jdbc:h2:mem:flowable-$suffix;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
        username = "sa"
        password = ""
    }

    @Bean
    fun dataSourceTransactionManager(dataSource: DataSource) = DataSourceTransactionManager(dataSource)

//    @Bean
//    fun engineConfiguratorList(): MutableList<EngineConfigurator> = mutableListOf<EngineConfigurator>()

    @Bean
    fun processEngineConfiguration(
        dataSource: DataSource,
        dataSourceTransactionManager: DataSourceTransactionManager
    ): SpringProcessEngineConfiguration =
        SpringProcessEngineConfiguration().apply {
            setDataSource(dataSource)
            transactionManager = dataSourceTransactionManager
            databaseSchemaUpdate = "true"
            isAsyncExecutorActivate = false
        }

    @Bean
    @Lazy
    fun processEngineFactory(
        processEngineConfiguration: SpringProcessEngineConfiguration,
        applicationContext: ApplicationContext,
        engineConfiguratorList: List<EngineConfigurator>
    ) =
        ProcessEngineFactoryBean().apply {
            val c = applicationContext.getBeansOfType(EngineConfigurator::class.java)
            println("EngineConfigurators: ${c.values}")
            // https://www.flowable.com/open-source/docs/dmn/ch02-Configuration#plug-into-process-engine
            processEngineConfiguration.configurators = c.values.toList() // engineConfiguratorList
            this.processEngineConfiguration = processEngineConfiguration
        }

    // The following are for conveniently injecting these Process Engine services into tests.

    @Bean
    fun repositoryService(processEngine: ProcessEngine): RepositoryService = processEngine.repositoryService

    @Bean
    fun runtimeService(processEngine: ProcessEngine): RuntimeService = processEngine.runtimeService

    @Bean
    fun taskService(processEngine: ProcessEngine): TaskService = processEngine.taskService

    @Bean
    fun historyService(processEngine: ProcessEngine): HistoryService = processEngine.historyService

    @Bean
    fun managementService(processEngine: ProcessEngine): ManagementService = processEngine.managementService
}
