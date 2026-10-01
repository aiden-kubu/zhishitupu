package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A small assertion walker for the exact keywords used by the frozen local contract. */
final class ApprovedSchema {
    private final JsonNode document;
    ApprovedSchema()throws Exception {document=HostJson.JSON.readTree(Files.readString(Path.of("../docs/assistant-team/website/08-stage1/http-contract.json")));}
    void check(String name,JsonNode value){visit(document.path("components").path("schemas").path(name),value,"$"+name);}
    private void visit(JsonNode schema,JsonNode value,String path) {
        if(schema.isBoolean()){assertTrue(schema.booleanValue(),path);return;}
        if(schema.has("$ref")){visit(document.at(schema.path("$ref").asText().substring(1)),value,path);return;}
        for(String union:List.of("oneOf","anyOf"))if(schema.has(union)) {
            int passed=0;List<String> errors=new ArrayList<>();for(JsonNode s:schema.get(union))try{visit(s,value,path);passed++;}catch(AssertionError error){errors.add(error.getMessage());}
            assertTrue(union.equals("oneOf")?passed==1:passed>0,path+" "+union+" "+errors);return;
        }
        if(schema.has("const"))assertEquals(schema.get("const"),value,path+" const");
        if(schema.has("enum")){boolean found=false;for(JsonNode e:schema.get("enum"))if(e.equals(value))found=true;assertTrue(found,path+" enum="+value);}
        if(schema.has("type")) {
            JsonNode type=schema.path("type");
            boolean valid=type.isArray()?java.util.stream.StreamSupport.stream(type.spliterator(),false).anyMatch(t->matches(t.asText(),value)):matches(type.asText(),value);
            assertTrue(valid,path+" type value="+value);
        }
        if(value.isObject()) {
            for(JsonNode key:schema.path("required"))assertTrue(value.has(key.asText()),path+" missing "+key);
            value.fields().forEachRemaining(e->{if(schema.path("properties").has(e.getKey()))visit(schema.path("properties").get(e.getKey()),e.getValue(),path+"."+e.getKey());
                else if(schema.has("additionalProperties")&&!schema.get("additionalProperties").asBoolean())fail(path+" extra "+e.getKey());});
        }
        if(value.isArray()) {
            if(schema.has("maxItems"))assertTrue(value.size()<=schema.get("maxItems").intValue(),path+" maxItems");
            if(schema.has("minItems"))assertTrue(value.size()>=schema.get("minItems").intValue(),path+" minItems");
            if(schema.path("uniqueItems").asBoolean()){Set<JsonNode> seen=new HashSet<>();value.forEach(n->assertTrue(seen.add(n),path+" uniqueItems"));}
            for(int i=0;i<value.size();i++) {JsonNode s=schema.path("prefixItems").has(i)?schema.path("prefixItems").get(i):schema.get("items");if(s!=null)visit(s,value.get(i),path+"["+i+"]");}
        }
        if(value.isTextual()) {
            int length=value.asText().codePointCount(0,value.asText().length());
            if(schema.has("minLength"))assertTrue(length>=schema.path("minLength").intValue(),path+" minLength");
            if(schema.has("maxLength"))assertTrue(length<=schema.path("maxLength").intValue(),path+" maxLength");
            if(schema.has("pattern"))assertTrue(java.util.regex.Pattern.compile(schema.path("pattern").asText()).matcher(value.asText()).find(),path+" pattern");
            if("date-time".equals(schema.path("format").asText()))assertDoesNotThrow(()->java.time.OffsetDateTime.parse(value.asText()),path);
        }
        if(value.isNumber()) {
            if(schema.has("minimum"))assertTrue(value.doubleValue()>=schema.get("minimum").doubleValue(),path+" minimum");
            if(schema.has("maximum"))assertTrue(value.doubleValue()<=schema.get("maximum").doubleValue(),path+" maximum");
        }
    }
    private boolean matches(String type,JsonNode value) {
        return switch(type){
                case "object"->value.isObject();case "array"->value.isArray();case "string"->value.isTextual();case "integer"->value.isIntegralNumber();case "boolean"->value.isBoolean();case "null"->value.isNull();default->throw new AssertionError("Unhandled schema type");};
    }
}
