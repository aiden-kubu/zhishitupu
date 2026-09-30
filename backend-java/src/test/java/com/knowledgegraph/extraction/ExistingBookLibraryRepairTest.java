package com.knowledgegraph.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Explicitly invoked local maintenance; never runs as part of the normal test suite. */
@SpringBootTest(properties="spring.sql.init.mode=never")
@EnabledIfSystemProperty(named="bookRepair", matches="inspect|apply")
class ExistingBookLibraryRepairTest {
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;

    @Test void repair() throws Exception {
        System.out.println("BOOK_REPAIR_DOCUMENTS=" + mapper.writeValueAsString(jdbc.sql("""
            SELECT d.id,d.library_id,d.original_name,COUNT(DISTINCT ne.node_id) AS nodes
            FROM documents d LEFT JOIN document_units u ON u.document_id=d.id
            LEFT JOIN document_chunks c ON c.unit_id=u.id LEFT JOIN node_evidence ne ON ne.chunk_id=c.id
            WHERE d.id IN (63,1221) GROUP BY d.id
            """).query().listOfRows()));
        System.out.println("BOOK_REPAIR_SOURCES=" + mapper.writeValueAsString(jdbc.sql("""
            SELECT document_id,REGEXP_REPLACE(source_locator,'[[:space:]]*第[[:space:]]*[0-9]+[[:space:]]*页$','') AS source,
                COUNT(*) AS units,MIN(unit_index) AS first_unit,MAX(unit_index) AS last_unit
            FROM document_units WHERE document_id IN (63,1221) GROUP BY document_id,source
            """).query().listOfRows()));
        System.out.println("BOOK_REPAIR_LIBRARIES=" + mapper.writeValueAsString(jdbc.sql("""
            SELECT l.id,l.name,l.status,COUNT(DISTINCT ln.node_id) AS nodes,
              COUNT(DISTINCT CASE WHEN u.document_id=63 THEN ne.node_id END) AS java_nodes,
              COUNT(DISTINCT CASE WHEN u.document_id=1221 THEN ne.node_id END) AS cpp_nodes
            FROM knowledge_libraries l LEFT JOIN library_nodes ln ON ln.library_id=l.id
            LEFT JOIN node_evidence ne ON ne.node_id=ln.node_id LEFT JOIN document_chunks c ON c.id=ne.chunk_id
            LEFT JOIN document_units u ON u.id=c.unit_id
            WHERE l.status='active' GROUP BY l.id
            """).query().listOfRows()));
        if ("apply".equals(System.getProperty("bookRepair"))) applyRepair();
    }

    private void applyRepair() throws Exception {
        var ids=java.util.List.of(193L,194L,195L,196L,197L,198L,199L,200L,201L,1881L);
        var old=java.util.List.of(194L,195L,196L,197L,198L,199L,200L,201L);
        if (!"数据结构与算法（JAVA语言版）.pdf".equals(jdbc.sql("SELECT original_name FROM documents WHERE id=63").query(String.class).single())
                || !"textbook.zip".equals(jdbc.sql("SELECT original_name FROM documents WHERE id=1221").query(String.class).single()))
            throw new IllegalStateException("源资料不符合本次修复范围");
        var backup=new java.util.LinkedHashMap<String,Object>();
        backup.put("knowledge_libraries",jdbc.sql("SELECT * FROM knowledge_libraries WHERE id IN (:ids)").param("ids",ids).query().listOfRows());
        backup.put("library_nodes",jdbc.sql("SELECT * FROM library_nodes WHERE library_id IN (:ids)").param("ids",ids).query().listOfRows());
        backup.put("document_topic_assignments",jdbc.sql("SELECT * FROM document_topic_assignments WHERE document_id IN (63,1221)").query().listOfRows());
        backup.put("documents",jdbc.sql("SELECT * FROM documents WHERE id IN (63,1221)").query().listOfRows());
        backup.put("learning_paths",jdbc.sql("SELECT * FROM learning_paths WHERE library_id IN (:ids)").param("ids",ids).query().listOfRows());
        var file=java.nio.file.Path.of("maintenance-backups","book-libraries-"+System.currentTimeMillis()+".json");
        java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(backup),java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.println("BOOK_REPAIR_BACKUP="+file.toAbsolutePath());
        tx.executeWithoutResult(status -> {
            long nodes=jdbc.sql("SELECT COUNT(*) FROM knowledge_nodes").query(Long.class).single();
            long edges=jdbc.sql("SELECT COUNT(*) FROM knowledge_edges").query(Long.class).single();
            // Retain existing IDs and archived chapter memberships so old references remain recoverable.
            jdbc.sql("UPDATE library_nodes SET chapter_label=COALESCE(chapter_label,'Java与面向对象程序设计') WHERE library_id=193").update();
            jdbc.sql("""
                INSERT IGNORE INTO library_nodes(library_id,node_id,chapter_label,sort_order)
                SELECT 193,ln.node_id,COALESCE(ln.chapter_label,l.name),ln.sort_order
                FROM library_nodes ln JOIN knowledge_libraries l ON l.id=ln.library_id
                WHERE ln.library_id IN (:ids) ORDER BY ln.library_id,ln.sort_order
                """).param("ids",old).update();
            jdbc.sql("""
                INSERT IGNORE INTO library_nodes(library_id,node_id,chapter_label,sort_order)
                SELECT DISTINCT 193,ne.node_id,NULL,0 FROM node_evidence ne
                JOIN document_chunks c ON c.id=ne.chunk_id JOIN document_units u ON u.id=c.unit_id
                WHERE u.document_id=63
                """).update();
            jdbc.sql("""
                UPDATE knowledge_libraries SET name='数据结构与算法（Java语言版）',type='book',
                description='Java语言版教材。按原文来源归集知识点，保留Java基础、线性表、栈与队列、递归、树、图、查找和排序等章节归属。',status='active'
                WHERE id=193
                """).update();
            jdbc.sql("""
                UPDATE knowledge_libraries SET name='数据结构（C++语言版）',type='book',
                description='邓俊辉《数据结构（C++语言版）》。来源为textbook.zip中的第1、2、3版PDF，保留各版本原文与证据；与Java语言版教材分别归库。'
                WHERE id=1881
                """).update();
            jdbc.sql("UPDATE knowledge_libraries SET status='archived' WHERE id IN (:ids)").param("ids",old).update();
            jdbc.sql("UPDATE learning_paths SET library_id=193 WHERE library_id IN (:ids)").param("ids",old).update();
            jdbc.sql("""
                INSERT IGNORE INTO document_topic_assignments(document_id,temp_key,library_id)
                SELECT document_id,temp_key,193 FROM document_topic_assignments WHERE document_id=63
                """).update();
            if (nodes!=jdbc.sql("SELECT COUNT(*) FROM knowledge_nodes").query(Long.class).single()
                    || edges!=jdbc.sql("SELECT COUNT(*) FROM knowledge_edges").query(Long.class).single())
                throw new IllegalStateException("修复不能改变节点和关系总数");
            if (jdbc.sql("""
                SELECT COUNT(DISTINCT ne.node_id) FROM node_evidence ne JOIN document_chunks c ON c.id=ne.chunk_id
                JOIN document_units u ON u.id=c.unit_id WHERE u.document_id=63
                AND NOT EXISTS(SELECT 1 FROM library_nodes ln WHERE ln.library_id=193 AND ln.node_id=ne.node_id)
                """).query(Long.class).single()!=0) throw new IllegalStateException("存在遗漏的Java教材知识点");
        });
        System.out.println("BOOK_REPAIR_APPLIED");
    }
}
