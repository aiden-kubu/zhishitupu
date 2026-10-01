package com.knowledgegraph.assistant.host;

import com.knowledgegraph.common.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.*;
import java.util.Map;

/** Explicit isolated entry point. It has no component/configuration stereotype or package scan. */
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,DataSourceTransactionManagerAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class,FlywayAutoConfiguration.class,LiquibaseAutoConfiguration.class})
public final class AssistantReadHostApplication {
    private AssistantReadHostApplication(){}
    static void install(GenericApplicationContext context,AssistantReadHostService host) {
        context.getBeanFactory().registerSingleton(StandaloneHostOnly.MARKER,new Object());
        context.registerBean(AssistantReadHostService.class,()->host,definition->definition.setDestroyMethodName("close"));
        context.registerBean(AssistantReadHostController.class,()->new AssistantReadHostController(host));
        context.registerBean(AssistantReadHostErrors.class,AssistantReadHostErrors::new);
        context.registerBean("assistantRequestId",FilterRegistrationBean.class,()-> {
            FilterRegistrationBean<RequestIdFilter> filter=new FilterRegistrationBean<>(new RequestIdFilter());filter.setOrder(-100);return filter;
        });
        context.registerBean("assistantBoundary",FilterRegistrationBean.class,()-> {
            FilterRegistrationBean<AssistantReadBoundaryFilter> filter=new FilterRegistrationBean<>(new AssistantReadBoundaryFilter());filter.setOrder(-90);return filter;
        });
    }
    /** Default remains closed; no command-line/property route supplies identity or enables the host. */
    public static void main(String[] args) {
        if(args.length!=0)throw new IllegalArgumentException("This disabled launcher accepts no overrides");
        SpringApplication application=new SpringApplication(AssistantReadHostApplication.class);
        StandardEnvironment environment=new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("isolatedReadHost",Map.of(
                "spring.config.location","optional:classpath:/assistant-read-host-unconfigured.properties","spring.sql.init.mode","never",
                "server.address","127.0.0.1","server.port","0","spring.main.banner-mode","off")));
        application.setEnvironment(environment);application.setAddCommandLineProperties(false);
        application.addInitializers(context->install((GenericApplicationContext)context,AssistantReadHostService.disabled()));
        application.run();
    }
}
