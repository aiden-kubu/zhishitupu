package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowledgegraph.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@Conditional(StandaloneHostOnly.class)
@RequestMapping("/api/assistant")
public final class AssistantReadHostController {
    private final AssistantReadHostService host;
    public AssistantReadHostController(AssistantReadHostService host){this.host=host;}
    private ResponseEntity<ApiResponse<ObjectNode>> ok(ObjectNode data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(data));}
    private void query(MultiValueMap<String,String> args,Set<String> keys) {
        if(!keys.containsAll(args.keySet()))throw HostJson.error(400,"INVALID_ARGUMENT");
        for(var e:args.entrySet())if(!e.getKey().equals("libraryIds")&&e.getValue().size()!=1)throw HostJson.error(400,"INVALID_ARGUMENT");
    }
    private int number(MultiValueMap<String,String> args,String key,int fallback,int max) {
        String value=args.getFirst(key);if(value==null)return fallback;
        if(!value.matches("[1-9][0-9]{0,3}"))throw HostJson.error(400,"INVALID_ARGUMENT");
        int parsed=Integer.parseInt(value);if(parsed>max)throw HostJson.error(400,"INVALID_ARGUMENT");return parsed;
    }
    private String body(HttpServletRequest request,int max) throws IOException {
        if(request.getContentLengthLong()>max)throw HostJson.error(413,"PAYLOAD_TOO_LARGE");
        byte[] raw=request.getInputStream().readNBytes(max+1);if(raw.length>max)throw HostJson.error(413,"PAYLOAD_TOO_LARGE");
        try {return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();}
        catch(java.nio.charset.CharacterCodingException ex){throw HostJson.error(400,"INVALID_ARGUMENT");}
    }
    @GetMapping("/capabilities") public ResponseEntity<ApiResponse<ObjectNode>> capabilities(@RequestParam MultiValueMap<String,String> args){query(args,Set.of());return ok(host.capabilities());}
    @GetMapping("/templates") public ResponseEntity<ApiResponse<ObjectNode>> templates(@RequestParam MultiValueMap<String,String> args){query(args,Set.of());return ok(host.templates());}
    @GetMapping("/scope/libraries") public ResponseEntity<ApiResponse<ObjectNode>> libraries(@RequestParam MultiValueMap<String,String> args){
        query(args,Set.of("page","pageSize"));return ok(host.libraries(number(args,"page",1,1000),number(args,"pageSize",20,20)));
    }
    @GetMapping("/scope/documents") public ResponseEntity<ApiResponse<ObjectNode>> documents(@RequestParam MultiValueMap<String,String> args){
        query(args,Set.of("libraryIds","page","pageSize"));List<String> values=args.get("libraryIds");
        Set<Long> ids=HostJson.ids(HostJson.JSON.valueToTree(values),1000);
        return ok(host.documents(ids,number(args,"page",1,1000),number(args,"pageSize",20,20)));
    }
    @PostMapping(value="/runs",consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<ApiResponse<ObjectNode>> create(HttpServletRequest request,@RequestParam MultiValueMap<String,String> args)throws IOException {
        query(args,Set.of());return ResponseEntity.status(202).cacheControl(CacheControl.noStore()).body(ApiResponse.ok(host.create(body(request,65536))));
    }
    @GetMapping("/runs/{runId}") public ResponseEntity<ApiResponse<ObjectNode>> run(@PathVariable String runId,@RequestParam MultiValueMap<String,String> args){query(args,Set.of());return ok(host.run(runId));}
    @PostMapping("/runs/{runId}/cancel") public ResponseEntity<ApiResponse<ObjectNode>> cancel(@PathVariable String runId,HttpServletRequest request,@RequestParam MultiValueMap<String,String> args)throws IOException {
        query(args,Set.of());if(!body(request,1).isEmpty())throw HostJson.error(400,"INVALID_ARGUMENT");return ok(host.cancel(runId));
    }
    @GetMapping("/runs/{runId}/artifacts/{artifactId}") public ResponseEntity<ApiResponse<ObjectNode>> artifact(@PathVariable String runId,@PathVariable String artifactId,@RequestParam MultiValueMap<String,String> args){query(args,Set.of());return ok(host.artifact(runId,artifactId));}
    @PostMapping(value="/runs/{runId}/artifacts/{artifactId}/source-reread",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ObjectNode>> reread(@PathVariable String runId,@PathVariable String artifactId,HttpServletRequest request,@RequestParam MultiValueMap<String,String> args)throws IOException {
        query(args,Set.of());return ok(host.reread(runId,artifactId,body(request,4096)));
    }
    @GetMapping("/runs/{runId}/artifacts/{artifactId}/graph") public ResponseEntity<ApiResponse<ObjectNode>> graph(@PathVariable String runId,@PathVariable String artifactId,@RequestParam MultiValueMap<String,String> args){query(args,Set.of());return ok(host.graph(runId,artifactId));}
}
