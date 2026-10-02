package com.knowledgegraph.assistant.host.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgegraph.KnowledgeGraphApplication;
import com.knowledgegraph.assistant.host.*;
import com.knowledgegraph.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** Scanned by the ordinary website only. No legacy reader/model lookup or independent application startup. */
@Configuration(proxyBeanMethods=false)
@Conditional(AssistantWebsiteReadConfiguration.OrdinaryWebsite.class)
public class AssistantWebsiteReadConfiguration {
    public static final String CONFIG="assistant.website.reviewed-config";
    public static final String CONFIG_SHA="assistant.website.controller-approved-config-sha256";
    public static final String DEMO_CONFIG="assistant.website.demo-config";
    record DemoSetup(boolean requested,AssistantWebsiteDemoConfig config) {}
    @Bean DemoSetup websiteAssistantDemoSetup(Environment environment) {
        if(!environment.containsProperty(DEMO_CONFIG))return new DemoSetup(false,null);
        if(environment.containsProperty(CONFIG) || environment.containsProperty(CONFIG_SHA))return new DemoSetup(true,null);
        try{return new DemoSetup(true,AssistantWebsiteDemoFactory.load(Path.of(environment.getRequiredProperty(DEMO_CONFIG))));}
        catch(RuntimeException rejected){return new DemoSetup(true,null);}
    }

    public static final class OrdinaryWebsite implements Condition {
        @Override public boolean matches(ConditionContext context,AnnotatedTypeMetadata metadata) {
            return context.getBeanFactory()!=null
                    && context.getBeanFactory().getBeanNamesForType(KnowledgeGraphApplication.class,false,false).length>0;
        }
    }
    @Bean(destroyMethod="close") @ConditionalOnMissingBean(AssistantReadHostService.class)
    AssistantReadHostService websiteAssistantReadHost(Environment environment,DemoSetup demo) {
        if(demo.requested())return demo.config()==null?AssistantReadHostService.unavailable():AssistantWebsiteDemoFactory.host(demo.config());
        String file=environment.getProperty(CONFIG),digest=environment.getProperty(CONFIG_SHA);
        if(file==null && digest==null)return AssistantReadHostService.disabled();
        if(file==null || digest==null || file.isBlank() || digest.isBlank())return AssistantReadHostService.unavailable();
        try{return AssistantReadHostApplication.reviewedWebsiteHost(Path.of(file),digest);}
        catch(RuntimeException rejected){return AssistantReadHostService.unavailable();}
    }
    // Explicit bean registration does not remove the controller's standalone scan condition.
    // Ordinary website errors continue to use its existing GlobalExceptionHandler.
    @Bean AssistantReadHostController websiteAssistantReadController(AssistantReadHostService host) {
        return new AssistantReadHostController(host);
    }
    @Bean FilterRegistrationBean<Filter> websiteAssistantReadBoundary(AssistantReadHostService host,DemoSetup demo) {
        Filter boundary=new WebsiteBoundary(host);
        if(demo.requested())boundary=new AssistantWebsiteDemoAccess(demo.config());
        FilterRegistrationBean<Filter> registration=new FilterRegistrationBean<>(boundary);
        registration.setOrder(-90);registration.addUrlPatterns("/api/assistant/*");return registration;
    }

    /** Public diagnostics report an unavailable snapshot only; all executable reads keep the local boundary. */
    static final class WebsiteBoundary implements Filter {
        private final AssistantReadHostService host;
        private final Filter local=AssistantReadHostApplication.localReadBoundary();
        private final ObjectMapper json=new ObjectMapper();
        WebsiteBoundary(AssistantReadHostService host){this.host=host;}
        @Override public void doFilter(ServletRequest input,ServletResponse output,FilterChain chain)throws IOException,ServletException {
            HttpServletRequest request=(HttpServletRequest)input;HttpServletResponse response=(HttpServletResponse)output;
            String path=request.getRequestURI().substring(request.getContextPath().length());
            if("GET".equals(request.getMethod()) && Set.of("/api/assistant/capabilities","/api/assistant/templates").contains(path)) {
                ObjectNode capability=host.capabilities();
                if(!capability.path("databaseIdentityVerified").asBoolean()) {
                    ObjectNode snapshot=path.endsWith("/capabilities")?capability:host.templates();
                    boolean unavailable=path.endsWith("/capabilities") || java.util.stream.StreamSupport.stream(snapshot.path("items").spliterator(),false)
                            .noneMatch(item->"available".equals(item.path("status").asText()));
                    if(unavailable) {
                        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
                        response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
                        if(!request.getParameterMap().isEmpty() || request.getContentLengthLong()>0 || request.getInputStream().read()!=-1) {
                            response.setStatus(400);json.writeValue(response.getOutputStream(),ApiResponse.error("INVALID_ARGUMENT","INVALID_ARGUMENT"));
                        }else json.writeValue(response.getOutputStream(),ApiResponse.ok(snapshot));
                        return;
                    }
                }
            }
            local.doFilter(input,output,chain);
        }
    }
}
