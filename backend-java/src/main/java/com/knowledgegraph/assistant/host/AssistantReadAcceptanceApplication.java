package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;
import static com.knowledgegraph.assistant.host.AssistantReadAcceptanceConfig.*;

/** Explicit acceptance-only entry point: no scanning, no legacy Application and no property enable switch. */
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,DataSourceTransactionManagerAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class,FlywayAutoConfiguration.class,LiquibaseAutoConfiguration.class})
public final class AssistantReadAcceptanceApplication {
    private AssistantReadAcceptanceApplication(){}
    static final Path APPROVED_FRONTEND_ROOT=Path.of("D:/知识图谱/frontend/dist");

    public static void main(String[] args) {
        AssistantReadAcceptanceConfig config=configuration(args);
        AssistantReadAcceptanceEvidence evidence=AssistantReadAcceptanceEvidence.load(config,Clock.systemUTC());
        // Validate every optional static resource BEFORE composing an enabled host or opening a listener.
        StaticSite site=staticSite(config,APPROVED_FRONTEND_ROOT);
        AssistantReadAcceptanceDataSource source=new AssistantReadAcceptanceDataSource(config,evidence);
        AssistantReadHostService host=host(config,evidence,source);
        try {
            SpringApplication app=new SpringApplication(AssistantReadAcceptanceApplication.class);
            app.setEnvironment(environment(config.port()));app.setAddCommandLineProperties(false);
            app.addInitializers(context->install((GenericApplicationContext)context,host,site));
            app.run();
        }catch(RuntimeException ex){host.close();throw new IllegalStateException("Acceptance startup failed");}
    }
    static AssistantReadAcceptanceConfig configuration(String[] args) {
        try {
            if(args.length!=2 || !args[0].startsWith("--reviewed-config=") || !args[1].startsWith("--controller-approved-config-sha256="))throw invalid();
            return AssistantReadAcceptanceConfig.load(Path.of(args[0].substring("--reviewed-config=".length())),
                    args[1].substring("--controller-approved-config-sha256=".length()));
        }catch(Exception ex){throw invalid();}
    }
    static AssistantReadHostService host(AssistantReadAcceptanceConfig config,AssistantReadAcceptanceEvidence evidence,AssistantReadAcceptanceDataSource source) {
        if(!source.authorityValid())throw invalid();
        var properties=new AssistantReadHostProperties(true,true,false,8,32,Duration.ofSeconds(1800),2L*1024*1024);
        var access=new AssistantReadAccessContext(config.owner(),config.grant(),scope->source.authorityValid() && scope.isWithin(config.grant()));
        return AssistantReadHostService.production(properties,()->access,evidence.reader(config,source));
    }
    static void install(GenericApplicationContext context,AssistantReadHostService host,StaticSite site) {
        AssistantReadHostApplication.install(context,host);
        if(site!=null)context.registerBean(StaticSite.class,()->site);
    }
    static StandardEnvironment environment(int port) {
        StandardEnvironment e=new StandardEnvironment();
        e.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        e.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        e.getPropertySources().addFirst(new MapPropertySource("reviewedAcceptanceOnly",Map.of(
                "spring.config.location","optional:classpath:/assistant-read-acceptance-unconfigured.properties",
                "spring.sql.init.mode","never","server.address",ADDRESS,"server.port",Integer.toString(port),
                "spring.main.banner-mode","off","spring.web.resources.add-mappings","false","server.forward-headers-strategy","NONE")));
        return e;
    }
    /** package-private dist-boundary seam used solely for synthetic offline build fixtures. */
    static StaticSite staticSite(AssistantReadAcceptanceConfig config,Path permittedRoot) {
        JsonNode n=config.frontend();if(n.isNull())return null;
        try {
            Path root=absolute(text(n,"directory",512));
            Path boundary=permittedRoot.toAbsolutePath().normalize();
            if(!root.startsWith(boundary) || !Files.isDirectory(boundary,LinkOption.NOFOLLOW_LINKS)
                    || !boundary.toRealPath().equals(boundary) || !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)
                    || !root.toRealPath().equals(root))throw invalid();
            StaticSite.noRedirects(root,boundary);
            byte[] manifest=read(config.bundleFile(text(n,"manifestFile",128)),262144,true);
            if(!sha256(manifest).equals(text(n,"manifestSha256",64)))throw invalid();
            JsonNode parsed=JSON.readTree(manifest);fields(parsed,"schemaVersion","files");equal(parsed,"schemaVersion","08-static-build-v1");
            JsonNode files=parsed.path("files");if(!files.isObject() || files.isEmpty() || files.size()>1000 || !files.has("index.html"))throw invalid();
            Map<String,StaticSite.Asset> assets=new HashMap<>();long total=0;
            for(var iterator=files.fields();iterator.hasNext();) {
                var item=iterator.next();String name=item.getKey();JsonNode spec=item.getValue();fields(spec,"sha256","bytes");
                if(!StaticSite.safe(name) || name.startsWith("api/") || name.equals("api"))throw invalid();
                int size=integer(spec,"bytes",0,10*1024*1024);String digest=hash(text(spec,"sha256",64));
                byte[] content=StaticSite.asset(root,name);
                if(content.length!=size || !sha256(content).equals(digest))throw invalid();
                total+=size;if(total>64L*1024*1024)throw invalid();assets.put(name,new StaticSite.Asset(digest,size));
            }
            return new StaticSite(root,Map.copyOf(assets));
        }catch(Exception ex){throw invalid();}
    }

    /** Explicitly installed only; the condition excludes this handler from legacy component scans. */
    @RestController @Conditional(StandaloneHostOnly.class)
    static final class StaticSite {
        record Asset(String sha256,int size){}
        private final Path root;
        private final Map<String,Asset> assets;
        StaticSite(Path root,Map<String,Asset> assets){this.root=root;this.assets=assets;}
        static boolean safe(String path) {
            if(path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains("%") || path.contains(":"))return false;
            for(String part:path.split("/",-1))if(!part.matches("[A-Za-z0-9_.-]+") || part.equals(".") || part.equals(".."))return false;
            return true;
        }
        static byte[] asset(Path root,String name)throws Exception {
            if(!safe(name))throw invalid();Path file=root.resolve(name).normalize();
            if(!file.startsWith(root))throw invalid();
            noRedirects(file,root);
            if(!root.toRealPath().equals(root) || !file.toRealPath().startsWith(root))throw invalid();
            return read(file,10*1024*1024,false);
        }
        static void noRedirects(Path path,Path boundary)throws Exception {
            for(Path p=path;;p=p.getParent()) {
                BasicFileAttributes attributes=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                // Windows junctions may be reparse/other entries rather than symbolic links.
                if(attributes.isSymbolicLink() || attributes.isOther() || !p.toRealPath().equals(p))throw invalid();
                if(p.equals(boundary))return;
            }
        }
        @RequestMapping(value="/{*asset}",method={RequestMethod.GET,RequestMethod.HEAD})
        ResponseEntity<byte[]> get(HttpServletRequest request) {
            String uri=request.getRequestURI();
            // Encoded paths are conservatively rejected, rather than decoded into a different resource.
            if(uri==null || !uri.startsWith("/") || uri.contains("%"))return ResponseEntity.badRequest().build();
            String name=uri.substring(1),lower=name.toLowerCase(Locale.ROOT);
            if(lower.equals("api") || lower.startsWith("api/"))return ResponseEntity.notFound().build();
            if(name.isEmpty())name="index.html";
            else if(!safe(name))return ResponseEntity.badRequest().build();
            else if(!assets.containsKey(name) && Arrays.stream(name.split("/")).noneMatch(p->p.contains(".")))name="index.html";
            Asset expected=assets.get(name);if(expected==null)return ResponseEntity.notFound().build();
            try {
                byte[] bytes=asset(root,name);
                if(bytes.length!=expected.size() || !sha256(bytes).equals(expected.sha256()))return ResponseEntity.status(503).build();
                MediaType type=MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).contentLength(bytes.length)
                        .body("HEAD".equals(request.getMethod())?null:bytes);
            }catch(Exception ex){return ResponseEntity.status(503).build();}
        }
    }
}
