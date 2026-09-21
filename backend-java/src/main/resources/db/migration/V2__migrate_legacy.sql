-- =====================================================================
-- V2__migrate_legacy.sql — 旧 PHP 表（nodes/edges）只读迁移到 Java 新表
-- 只读旧表（SELECT），严禁修改旧表。可重复执行：每条 INSERT ... SELECT ... WHERE NOT EXISTS。
-- 映射规则（保留旧 id，使各表映射可精确对齐且幂等）：
--   a. nodes 中 type='course' 的行 -> knowledge_libraries（type='course'），
--      同时该节点仍作为 knowledge_nodes 中 node_type='course' 的节点（见 b）。
--   b. 其余（全部）nodes -> knowledge_nodes：definition 保留；scene 非空时写入
--      properties_json = {"scene": ...}；status tinyint 1 -> 'active'，其他 -> 'inactive'。
--   c. name_en 非空 -> node_aliases(language='en')，normalized_alias = LOWER(TRIM(name_en))。
--   d. 旧 nodes.course_id -> library_nodes(library_id = 该 course 节点对应的
--      knowledge_libraries.id（即该 course 节点的旧 id），node_id = 新节点 id，sort_order = 节点 id)。
--   e. 旧 edges -> knowledge_edges(source_node_id, target_node_id, relation_type, weight, status='active')。
-- =====================================================================

-- a. 课程节点 -> 知识库
INSERT INTO knowledge_libraries (id, name, type, description, status)
SELECT n.id, n.name, 'course', n.definition, 'active'
FROM nodes n
WHERE n.type = 'course'
  AND NOT EXISTS (SELECT 1 FROM knowledge_libraries kl WHERE kl.id = n.id);

-- b. 全部节点 -> knowledge_nodes（含课程节点本身，类型仍为 course）
INSERT INTO knowledge_nodes (id, canonical_name, name_en, node_type, definition, properties_json, status)
SELECT n.id,
       n.name,
       NULLIF(TRIM(n.name_en), ''),
       n.type,
       n.definition,
       CASE WHEN n.scene IS NOT NULL AND TRIM(n.scene) <> '' THEN JSON_OBJECT('scene', n.scene) END,
       CASE WHEN n.status = 1 THEN 'active' ELSE 'inactive' END
FROM nodes n
WHERE NOT EXISTS (SELECT 1 FROM knowledge_nodes kn WHERE kn.id = n.id);

-- c. 英文名 -> 别名（language='en'）
INSERT INTO node_aliases (node_id, alias, normalized_alias, language)
SELECT n.id, TRIM(n.name_en), LOWER(TRIM(n.name_en)), 'en'
FROM nodes n
WHERE n.name_en IS NOT NULL AND TRIM(n.name_en) <> ''
  AND NOT EXISTS (
    SELECT 1 FROM node_aliases na
    WHERE na.node_id = n.id
      AND na.normalized_alias = LOWER(TRIM(n.name_en))
      AND na.language = 'en'
  );

-- d. 旧 course_id -> library_nodes（library_id 即课程节点的旧 id，与 a 中保留的 id 一致）
INSERT INTO library_nodes (library_id, node_id, chapter_label, sort_order)
SELECT n.course_id, n.id, NULL, n.id
FROM nodes n
WHERE n.course_id IS NOT NULL AND n.course_id > 0
  AND EXISTS (SELECT 1 FROM knowledge_libraries kl WHERE kl.id = n.course_id)
  AND NOT EXISTS (
    SELECT 1 FROM library_nodes ln
    WHERE ln.library_id = n.course_id AND ln.node_id = n.id
  );

-- e. 旧边 -> knowledge_edges（起点/终点必须能对应到已迁移节点，避免孤儿边）
INSERT INTO knowledge_edges (id, source_node_id, target_node_id, relation_type, weight, status)
SELECT e.id, e.source_id, e.target_id, e.relation, e.weight, 'active'
FROM edges e
WHERE NOT EXISTS (SELECT 1 FROM knowledge_edges ke WHERE ke.id = e.id)
  AND EXISTS (SELECT 1 FROM knowledge_nodes s WHERE s.id = e.source_id)
  AND EXISTS (SELECT 1 FROM knowledge_nodes t WHERE t.id = e.target_id);
