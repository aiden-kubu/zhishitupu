package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ErrorCodes;
import com.knowledgegraph.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 资料属性接口的请求校验与标签删除路由测试（不依赖数据库）。 */
@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock DocumentService documentService;
    @Mock ProcessingService processingService;
    @Mock IngestionPipeline pipeline;
    @Mock JdbcClient jdbc;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DocumentController(documentService, processingService, pipeline, jdbc))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void blankTagReturns400BeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/documents/7/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tag\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_ARGUMENT));
    }

    @Test
    void missingHumanCheckedReturns400InsteadOf500() throws Exception {
        mockMvc.perform(put("/api/documents/7/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_ARGUMENT));
    }

    @Test
    void slashTagCanBeDeletedThroughQueryParameter() throws Exception {
        mockMvc.perform(delete("/api/documents/7/tags").queryParam("tag", "AI/ML"))
                .andExpect(status().isOk());

        verify(documentService).removeTag(7L, "AI/ML");
    }
}
