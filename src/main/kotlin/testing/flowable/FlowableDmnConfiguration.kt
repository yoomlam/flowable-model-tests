package testing.flowable

import org.flowable.dmn.api.DmnDecisionService
import org.flowable.dmn.api.DmnHistoryService
import org.flowable.dmn.api.DmnManagementService
import org.flowable.dmn.api.DmnRepositoryService
import org.flowable.dmn.engine.DmnEngine
import org.flowable.dmn.spring.DmnEngineFactoryBean
import org.flowable.dmn.spring.SpringDmnEngineConfiguration
import org.flowable.dmn.spring.configurator.SpringDmnEngineConfigurator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import javax.sql.DataSource

// Refer to https://github.com/flowable/flowable-engine/blob/flowable-6.8.0/modules/flowable-dmn-spring/src/test/java/org/flowable/spring/test/jupiter/DmnSpringJunitJupiterTest.java
@Configuration(proxyBeanMethods = false)
class FlowableDmnConfiguration {

    //    @Lazy
    @Bean
    fun dmnEngineConfiguration(
        dataSource: DataSource,
        dataSourceTransactionManager: DataSourceTransactionManager
    ) =
        SpringDmnEngineConfiguration().apply {
            setDataSource(dataSource)
            transactionManager = dataSourceTransactionManager
            databaseSchemaUpdate = "true"
            // https://www.flowable.com/open-source/docs/dmn/ch02-Configuration#strict-mode
            // desired?: isStrictMode = false

            // https://www.flowable.com/open-source/docs/dmn/ch02-Configuration#custom-flowable-function-delegates
            // customFlowableFunctionDelegates
        }

    @Bean
    fun dmnEngineFactory(dmnEngineConfiguration: SpringDmnEngineConfiguration) =
        DmnEngineFactoryBean().apply {
            this.dmnEngineConfiguration = dmnEngineConfiguration
        }

    @Bean
    fun dmnEngineConfigurator(dmnEngineConfiguration: SpringDmnEngineConfiguration) =
        SpringDmnEngineConfigurator().apply {
            this.dmnEngineConfiguration = dmnEngineConfiguration
        }

    // The following are for conveniently injecting these DMN Engine services.

    @Bean
    fun dmnRepositoryService(dmnEngine: DmnEngine): DmnRepositoryService = dmnEngine.dmnRepositoryService

    @Bean
    fun dmnDecisionService(dmnEngine: DmnEngine): DmnDecisionService = dmnEngine.dmnDecisionService

    @Bean
    fun dmnHistoryService(dmnEngine: DmnEngine): DmnHistoryService = dmnEngine.dmnHistoryService

    @Bean
    fun dmnManagementService(dmnEngine: DmnEngine): DmnManagementService = dmnEngine.dmnManagementService
}
