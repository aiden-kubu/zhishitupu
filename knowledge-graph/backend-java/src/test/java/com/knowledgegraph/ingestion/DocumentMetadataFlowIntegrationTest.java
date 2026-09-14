package com.knowledgegraph.ingestion;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.extraction.LibraryOrganizationService;
import com.knowledgegraph.library.LibraryService;
import com.knowledgegraph.library.dto.LibraryDetail;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档元数据 / 标签 / 可信度 / 知识库别名 集成测试（属性面板第一批）。
 * 真实本地 MySQL + 隔离夹具（唯一后缀，测试后清理）；不调用真实模型。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.sql.init.mode=never")
// 夹具全部在测试事务内建立，测试结束自动回滚；不再依赖手工 DELETE 清理（进程中断不会残留）
@org.springframework.transaction.annotation.Transactional
class DocumentMetadataFlowIntegrationTest {

    @Autowired JdbcClient jdbc;
    @Autowired DocumentService documentService;
    @Autowired LibraryService libraryService;
    @Autowired LibraryOrganizationService organizationService;

    private String suffix;
    private long libraryId;
    private long documentId;

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
        documentId = insert("""
                INSERT INTO documents(library_id,original_name,stored_name,mime_type,extension,size_bytes,sha256,storage_path,status)
                VALUES(:l,:n,'s','application/pdf','pdf',1,:s,'s','COMPLETED')
                """, Map.of("l", libraryId, "n", "meta-" + suffix, "s", "sha-meta-" + suffix));
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

        // 查询参数删除入口所需场景：斜杠是合法标签内容，服务层必须可正常增删。
        documentService.addTag(documentId, "AI/ML");
        documentService.removeTag(documentId, "AI/ML");
        assertFalse(view().tags().stream().anyMatch(t -> t.tag().equals("AI/ML")));

        // 非法标签
        assertThrows(ApiException.class, () -> documentService.addTag(documentId, "   "));
        assertThrows(ApiException.class, () -> documentService.addTag(documentId, "\t\r\n"));
        assertThrows(ApiException.class, () -> documentService.removeTag(999999, "x"));
    }

    @Test
    void existingTagRemainsIdempotentAtLimit() {
        for (int i = 0; i < 20; i++) {
            jdbc.sql("INSERT INTO document_tags(document_id, tag, normalized_tag, source) VALUES(:d,:t,:t,'ai')")
                    .param("d", documentId).param("t", "标签" + i).update();
        }

        documentService.addTag(documentId, "标签0");
        assertEquals(20, view().tags().size());
        assertEquals("human", view().tags().stream()
                .filter(t -> t.tag().equals("标签0")).findFirst().orElseThrow().source());
        assertThrows(ApiException.class, () -> documentService.addTag(documentId, "第21个标签"));
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
        // 新库别名已沉淀
        Integer aliasCount = jdbc.sql("SELECT COUNT(*) FROM library_aliases WHERE library_id = :id")
                .param("id", created).query(Integer.class).single();
        assertEquals(1, aliasCount);
        // AI 标签
        assertTrue(view().tags().stream().anyMatch(t -> t.source().equals("ai")));
    }

    @Test
    void organizationSkipsOversizedAiTagWithoutRollingBackAssignment() {
        String longTopic = "长".repeat(101);
        LibraryOrganizationService.Group group = new LibraryOrganizationService.Group(
                libraryId, longTopic, "测试描述", List.of(), List.of("k1"));

        organizationService.apply(documentId, List.of(group));

        assertTrue(view().tags().stream().noneMatch(t -> t.tag().equals(longTopic)));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM document_topic_assignments WHERE document_id = :id AND library_id = :l")
                .param("id", documentId).param("l", libraryId).query(Integer.class).single());
    }

    // ---------------------------------------------------------------- R02：属性展示准确性

    @Test
    void metadataReturnsRealUpdatedAtAndReflectsChanges() {
        // 存量资料未建元数据行：updatedAt 为 null（不得用 created_at 冒充）
        assertNull(view().updatedAt());

        documentService.updateMetadata(documentId, "标题一" + suffix, null);
        String firstUpdated = view().updatedAt();
        assertNotNull(firstUpdated);

        // 可控旧时间夹具：显式赋值会停用 ON UPDATE，随后一次真实更新必须推进时间
        jdbc.sql("UPDATE document_metadata SET updated_at = '2020-01-01 00:00:00' WHERE document_id = :id")
                .param("id", documentId).update();
        assertEquals("2020-01-01 00:00:00.0", view().updatedAt());

        documentService.updateMetadata(documentId, "标题二" + suffix, null);
        String secondUpdated = view().updatedAt();
        assertFalse(secondUpdated.startsWith("2020-01-01"), "元数据更新后 updatedAt 必须真实变化");
    }

    @Test
    void humanVerificationCanBeToggledOff() {
        documentService.setHumanVerification(documentId, true, "首轮人工核对");
        assertTrue(((Map<?, ?>) view().verification().get("human")).get("checked").equals(true));

        documentService.setHumanVerification(documentId, false, null);
        Map<?, ?> human = (Map<?, ?>) view().verification().get("human");
        assertEquals(false, human.get("checked"), "人工校对应支持取消勾选并正确保存 false");
    }

    @Test
    void uploadedHashUsesRecordedSemanticsWithoutMatchedFlag() throws Exception {
        byte[] content = ("%PDF-1.4 hash-semantics-" + suffix).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "hash-r02-" + suffix + ".pdf",
                "application/pdf", content);
        DocumentService.DocumentView view = documentService.upload(libraryId, file);
        try {
            Map<?, ?> hash = (Map<?, ?>) view.verification().get("hash");
            assertNotNull(hash.get("sha256"));
            assertNotNull(hash.get("recordedAt"), "新数据必须携带 recordedAt");
            assertFalse(hash.containsKey("matched"), "上传只计算并记录 SHA-256，不得声称比对通过");
            assertFalse(hash.containsKey("checkedAt"), "不得使用暗示校验成功的 checkedAt");
        } finally {
            // 磁盘文件在事务之外创建，必须显式删除；数据库记录随测试事务回滚
            String storagePath = jdbc.sql("SELECT storage_path FROM documents WHERE id = :id")
                    .param("id", view.id()).query(String.class).optional().orElse(null);
            if (storagePath != null) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(storagePath));
                } catch (Exception ignored) {
                    // 清理失败不影响断言
                }
            }
        }
    }

    private long countLibraries() {
        return jdbc.sql("SELECT COUNT(*) FROM knowledge_libraries").query(Long.class).single();
    }
}
