package testing.flowable

import org.flowable.cmmn.api.CmmnHistoryService
import org.flowable.cmmn.api.CmmnManagementService
import org.flowable.cmmn.api.CmmnRepositoryService
import org.flowable.cmmn.api.CmmnRuntimeService
import org.flowable.cmmn.api.CmmnTaskService
import org.flowable.cmmn.engine.CmmnEngine
import org.flowable.cmmn.spring.CmmnEngineFactoryBean
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration
import org.flowable.cmmn.spring.configurator.SpringCmmnEngineConfigurator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

// Refer to https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-cmmn-spring/src/test/java/org/flowable/spring/test/jupiter/CmmnSpringJunitJupiterTest.java
@Configuration(proxyBeanMethods = false)
class FlowableCmmnConfiguration {

    //    @Lazy
    @Bean
    fun cmmnEngineConfiguration(
        dataSource: DataSource,
        dataSourceTransactionManager: DataSourceTransactionManager
    ) =
        SpringCmmnEngineConfiguration().apply {
            setDataSource(dataSource)
            transactionManager = dataSourceTransactionManager
            databaseSchemaUpdate = "true"
        }

    @Bean
    fun cmmnEngineFactory(cmmnEngineConfiguration: SpringCmmnEngineConfiguration) =
        CmmnEngineFactoryBean().apply {
            this.cmmnEngineConfiguration = cmmnEngineConfiguration
        }

    @Bean
    fun cmmnEngineConfigurator(cmmnEngineConfiguration: SpringCmmnEngineConfiguration) =
        SpringCmmnEngineConfigurator().apply {
            this.setCmmnEngineConfiguration(cmmnEngineConfiguration)
        }

    // The following are for conveniently injecting these CMMN Engine services.

    @Bean
    fun cmmnRepositoryService(cmmnEngine: CmmnEngine): CmmnRepositoryService = cmmnEngine.cmmnRepositoryService

    @Bean
    fun cmmnRuntimeService(cmmnEngine: CmmnEngine): CmmnRuntimeService = cmmnEngine.cmmnRuntimeService

    @Bean
    fun cmmntaskService(cmmnEngine: CmmnEngine): CmmnTaskService = cmmnEngine.cmmnTaskService

    @Bean
    fun cmmnHistoryService(cmmnEngine: CmmnEngine): CmmnHistoryService = cmmnEngine.cmmnHistoryService

    @Bean
    fun cmmnManagementService(cmmnEngine: CmmnEngine): CmmnManagementService = cmmnEngine.cmmnManagementService

    // @Bean
    // fun cmmnMigrationService(cmmnEngine: CmmnEngine): CmmnMigrationService = cmmnEngine.cmmnMigrationService
}
