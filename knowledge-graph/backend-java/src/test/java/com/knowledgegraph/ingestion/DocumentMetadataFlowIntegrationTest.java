package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.extraction.LibraryOrganizationService;
import com.knowledgegraph.library.LibraryService;
import com.knowledgegraph.library.dto.LibraryDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档元数据 / 标签 / 可信度 / 知识库别名 集成测试（属性面板第一批）。
 * 真实本地 MySQL + 隔离夹具（唯一后缀，测试后清理）；不调用真实模型。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.sql.init.mode=never")
class DocumentMetadataFlowIntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired DocumentService documentService;
    @Autowired LibraryService libraryService;
    @Autowired LibraryOrganizationService organizationService;

    private String suffix;
    private long libraryId;
    private long documentId;
    private final List<Long> createdLibraryIds = new ArrayList<>();

    private long insert(String sql, Map<String, ?> params) {
        var key = new GeneratedKeyHolder();
        jdbc.sql(sql).params(params).update(key);
        return key.getKey().longValue();
    }

    @BeforeEach
    void setup() {
        suffix = Long.toString(System.nanoTime());
        libraryId = insert("INSERT INTO knowledge_libraries(name,type,status) VALUES(:n,'topic','active')",
                Map.of("n", "元数据测试库" + suffix));
        createdLibraryIds.add(libraryId);
        documentId = insert("""
                INSERT INTO documents(library_id,original_name,stored_name,mime_type,extension,size_bytes,sha256,storage_path,status)
                VALUES(:l,:n,'s','application/pdf','pdf',1,:s,'s','COMPLETED')
                """, Map.of("l", libraryId, "n", "meta-" + suffix, "s", "sha-meta-" + suffix));
    }

    @AfterEach
    void cleanup() {
        jdbc.sql("DELETE FROM document_tags WHERE document_id = :id").param("id", documentId).update();
        jdbc.sql("DELETE FROM document_metadata WHERE document_id = :id").param("id", documentId).update();
        jdbc.sql("DELETE FROM document_topic_assignments WHERE document_id = :id").param("id", documentId).update();
        jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", documentId).update();
        jdbc.sql("DELETE FROM knowledge_libraries WHERE id IN (:ids)")
                .param("ids", createdLibraryIds).update();
    }

    private DocumentService.DocumentView view() {
        return documentService.get(documentId);
    }

    // ---------------------------------------------------------------- 元数据

    @Test
    void metadataUpsertsLazilyAndValidates() {
        // 夹具未建 metadata 行：首次更新按原文件名补建并覆盖
        DocumentService.DocumentView view = documentService.updateMetadata(
                documentId, "自定义标题" + suffix, "archived");
        assertEquals("自定义标题" + suffix, view.title());
        assertEquals("archived", view.lifecycleStatus());

        // 缺省字段保持原值
        view = documentService.updateMetadata(documentId, null, "outdated");
        assertEquals("自定义标题" + suffix, view.title());
        assertEquals("outdated", view.lifecycleStatus());

        // 非法值
        assertThrows(ApiException.class, () -> documentService.updateMetadata(documentId, "  ", null));
        assertThrows(ApiException.class, () -> documentService.updateMetadata(documentId, null, "hacked"));
        assertThrows(ApiException.class, () -> documentService.updateMetadata(999999, "x", null));
    }

    // ---------------------------------------------------------------- 标签

    @Test
    void tagsDedupeNormalizeAndHumanPriority() {
        documentService.addTag(documentId, "重点");
        documentService.addTag(documentId, "重点"); // 完全重复
        documentService.addTag(documentId, " 重点 "); // 归一化后重复
        assertEquals(1, view().tags().size());

        // AI 先写入同名标签 → 人工随后添加时来源转 human（人工优先）
        jdbc.sql("INSERT INTO document_tags(document_id, tag, normalized_tag, source) VALUES(:d,'校对通过','校对通过','ai')")
                .param("d", documentId).update();
        documentService.addTag(documentId, "校对通过");
        DocumentService.TagView tag = view().tags().stream()
                .filter(t -> t.tag().equals("校对通过")).findFirst().orElseThrow();
        assertEquals("human", tag.source());

        // 删除标签
        documentService.removeTag(documentId, "重点");
        assertFalse(view().tags().stream().anyMatch(t -> t.tag().equals("重点")));

        // 非法标签
        assertThrows(ApiException.class, () -> documentService.addTag(documentId, "   "));
        assertThrows(ApiException.class, () -> documentService.removeTag(999999, "x"));
    }

    // ---------------------------------------------------------------- 可信度

    @Test
    void humanVerificationMergesIntoVerificationJson() {
        DocumentService.DocumentView view = documentService.setHumanVerification(documentId, true, "已人工核对原文");
        Map<?, ?> human = ((Map<?, ?>) view.verification().get("human"));
        assertEquals(true, human.get("checked"));
        assertEquals("已人工核对原文", human.get("note"));
    }

    // ---------------------------------------------------------------- 知识库别名

    @Test
    void libraryAliasesReplaceValidateAndDedupe() {
        List<String> aliasInput = new ArrayList<>();
        aliasInput.add("stack-queue-" + suffix);
        aliasInput.add("堆栈相关");
        aliasInput.add("");
        aliasInput.add(null);
        aliasInput.add("STACK-QUEUE-" + suffix);
        LibraryDetail detail = libraryService.replaceAliases(libraryId, aliasInput);
        assertEquals(2, detail.aliases().size()); // 空值剔除 + 大小写去重
        assertTrue(detail.aliases().contains("堆栈相关"));

        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 11; i++) tooMany.add("别名" + i);
        assertThrows(ApiException.class, () -> libraryService.replaceAliases(libraryId, tooMany));
        assertThrows(ApiException.class, () -> libraryService.replaceAliases(999999, List.of("x")));
    }

    // ---------------------------------------------------------------- 自动整理联动

    @Test
    void organizationMatchesByAliasWritesAliasesAndAiTags() {
        String alias = "stack-queue-" + suffix;
        libraryService.replaceAliases(libraryId, List.of(alias));
        long librariesBefore = countLibraries();

        // AI 返回的组不带 existingLibraryId，但组别名命中已有库 → 归入该库而不是新建
        LibraryOrganizationService.Group group = new LibraryOrganizationService.Group(
                null, "堆栈专题" + suffix, "测试描述", List.of(alias, "SQ别名" + suffix), List.of("k1"));
        organizationService.apply(documentId, List.of(group));

        assertEquals(librariesBefore, countLibraries(), "按别名命中后不得新建库");
        assertEquals(libraryId, jdbc.sql("SELECT library_id FROM documents WHERE id = :id")
                .param("id", documentId).query(Long.class).single());
        // 组别名沉淀进 library_aliases
        Integer aliasCount = jdbc.sql("SELECT COUNT(*) FROM library_aliases WHERE library_id = :id AND normalized_alias = :n")
                .param("id", libraryId).param("n", ("sq别名" + suffix).toLowerCase())
                .query(Integer.class).single();
        assertEquals(1, aliasCount);
        // 主题名写成 AI 标签
        DocumentService.DocumentView view = view();
        assertTrue(view.tags().stream().anyMatch(t -> t.tag().equals("堆栈专题" + suffix) && t.source().equals("ai")));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM document_topic_assignments WHERE document_id = :id AND library_id = :l")
                .param("id", documentId).param("l", libraryId).query(Integer.class).single());
    }

    @Test
    void organizationCreatesLibraryAndAiTagForNewTopic() {
        long librariesBefore = countLibraries();
        LibraryOrganizationService.Group group = new LibraryOrganizationService.Group(
                null, "全新主题" + suffix, "新主题描述", List.of("全新别名" + suffix), List.of("k1"));
        organizationService.apply(documentId, List.of(group));

        assertEquals(librariesBefore + 1, countLibraries());
        Long created = jdbc.sql("SELECT id FROM knowledge_libraries WHERE name = :n")
                .param("n", "全新主题" + suffix).query(Long.class).optional().orElse(null);
        createdLibraryIds.add(created);
        // 新库别名已沉淀
        Integer aliasCount = jdbc.sql("SELECT COUNT(*) FROM library_aliases WHERE library_id = :id")
                .param("id", created).query(Integer.class).single();
        assertEquals(1, aliasCount);
        // AI 标签
        assertTrue(view().tags().stream().anyMatch(t -> t.source().equals("ai")));
    }

    private long countLibraries() {
        return jdbc.sql("SELECT COUNT(*) FROM knowledge_libraries").query(Long.class).single();
    }
}
