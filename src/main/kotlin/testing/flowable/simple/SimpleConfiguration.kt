package testing.flowable.simple

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import testing.flowable.simple.service.MyApiClient
import testing.flowable.simple.service.SimpleService

@Configuration
class SimpleConfiguration {
    @Bean
    fun someService(apiClient: MyApiClient) = SimpleService(MyApiClient("for someService1"))

    @Bean
    fun apiClient2() = MyApiClient("for someService2")

    @Bean
    fun someService2(apiClient: MyApiClient) = SimpleService(apiClient)
}
