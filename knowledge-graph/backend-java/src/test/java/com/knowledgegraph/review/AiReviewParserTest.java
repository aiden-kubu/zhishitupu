package com.knowledgegraph.review;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class AiReviewParserTest {
    @Test void rejectsMissingForgedDuplicateAndNonBooleanDecisions() {
        for (String raw : new String[]{"{}", "{\"decisions\":[]}",
                "{\"decisions\":[{\"id\":\"entity:999\",\"approved\":true,\"reason\":\"通过\"}]}",
                "{\"decisions\":[{\"id\":\"entity:1\",\"approved\":\"true\",\"reason\":\"通过\"}]}",
                "{\"decisions\":[{\"id\":\"entity:1\",\"approved\":true,\"reason\":\"通过\"},{\"id\":\"entity:1\",\"approved\":false,\"reason\":\"拒绝\"}]}"})
            assertThrows(ApiException.class, () -> AiReviewService.parse(new ObjectMapper(),raw,Set.of("entity:1")));
    }
    @Test void acceptsExplicitRejectionAndRejectsTrailingJson() {
        String raw="{\"decisions\":[{\"id\":\"entity:1\",\"approved\":false,\"reason\":\"证据不足\"}]}";
        assertFalse(AiReviewService.parse(new ObjectMapper(),raw,Set.of("entity:1")).get(0).approved());
        assertThrows(ApiException.class, () -> AiReviewService.parse(new ObjectMapper(),raw+"{}",Set.of("entity:1")));
    }
}
