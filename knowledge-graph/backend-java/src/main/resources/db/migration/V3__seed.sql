-- =====================================================================
-- V3__seed.sql — 最小 TCP/UDP 演示集（仅当 knowledge_nodes 为空时插入）
-- 用于没有旧数据可迁移的全新环境。可重复执行。
-- =====================================================================

-- 演示知识库
INSERT INTO knowledge_libraries (id, name, type, description, status)
SELECT 1, '计算机网络', 'course', '最小演示知识库（TCP/UDP）', 'active'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM knowledge_nodes)
  AND NOT EXISTS (SELECT 1 FROM knowledge_libraries WHERE id = 1);

-- 演示节点：TCP / UDP
INSERT INTO knowledge_nodes (id, canonical_name, name_en, node_type, definition, properties_json, status)
SELECT 1, 'TCP', 'Transmission Control Protocol', 'concept',
       '传输控制协议：面向连接的、可靠的传输层协议，通过三次握手建立连接，提供流量控制与拥塞控制。',
       NULL, 'active'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM knowledge_nodes)
  AND NOT EXISTS (SELECT 1 FROM knowledge_nodes WHERE id = 1);

INSERT INTO knowledge_nodes (id, canonical_name, name_en, node_type, definition, properties_json, status)
SELECT 2, 'UDP', 'User Datagram Protocol', 'concept',
       '用户数据报协议：无连接的传输层协议，不保证可靠交付，开销小、时延低。',
       NULL, 'active'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM knowledge_nodes)
  AND NOT EXISTS (SELECT 1 FROM knowledge_nodes WHERE id = 2);

-- 演示节点挂到演示知识库
INSERT INTO library_nodes (library_id, node_id, chapter_label, sort_order)
SELECT 1, 1, NULL, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM knowledge_nodes)
  AND NOT EXISTS (SELECT 1 FROM library_nodes WHERE library_id = 1 AND node_id = 1);

INSERT INTO library_nodes (library_id, node_id, chapter_label, sort_order)
SELECT 1, 2, NULL, 2 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM knowledge_nodes)
  AND NOT EXISTS (SELECT 1 FROM library_nodes WHERE library_id = 1 AND node_id = 2);

-- 演示关系：TCP 对比 UDP
INSERT INTO knowledge_edges (id, source_node_id, target_node_id, relation_type, weight, status)
SELECT 1, 1, 2, '对比', 1.0, 'active'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM knowledge_nodes)
  AND NOT EXISTS (SELECT 1 FROM knowledge_edges WHERE source_node_id = 1 AND target_node_id = 2 AND relation_type = '对比');
