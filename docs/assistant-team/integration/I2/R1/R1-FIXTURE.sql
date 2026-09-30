-- R1 isolated semantic fixture only. Exact ID collisions are checked by the guarded loader.
-- Different inactive relation type is required by the existing unique source/target/type index.
START TRANSACTION;
INSERT INTO knowledge_libraries(id,name,type,status,created_at,updated_at) VALUES
(7910,'I2 R1 library A','topic','active','2026-09-28','2026-09-28'),
(7911,'I2 R1 library B','topic','active','2026-09-28','2026-09-28'),
(7912,'I2 R1 outside library','topic','active','2026-09-28','2026-09-28'),
(7913,'I2 R1 empty library','topic','active','2026-09-28','2026-09-28');
INSERT INTO knowledge_nodes(id,canonical_name,node_type,status,created_at,updated_at) VALUES
(900001,'I2R1 a','concept','active','2026-09-28','2026-09-28'),
(900002,'I2R1 b','concept','active','2026-09-28','2026-09-28'),
(900003,'I2R1 c','concept','active','2026-09-28','2026-09-28'),
(900004,'I2R1 o','concept','active','2026-09-28','2026-09-28'),
(900005,'I2R1 i','concept','inactive','2026-09-28','2026-09-28'),
(900006,'I2R1 j','concept','active','2026-09-28','2026-09-28');
INSERT INTO library_nodes(library_id,node_id) VALUES
(7910,900001),(7911,900001),(7910,900002),(7911,900003),(7912,900004),(7910,900005),(7910,900006);
INSERT INTO knowledge_edges(id,source_node_id,target_node_id,relation_type,status,created_at,updated_at) VALUES
(910001,900001,900001,'SELF','active','2026-09-28','2026-09-28'),
(910002,900001,900002,'R','active','2026-09-28','2026-09-28'),
(910003,900002,900001,'R','active','2026-09-28','2026-09-28'),
(910004,900001,900002,'R2','active','2026-09-28','2026-09-28'),
(910005,900001,900004,'OUT','active','2026-09-28','2026-09-28'),
(910006,900001,900002,'INACTIVE','inactive','2026-09-28','2026-09-28'),
(910007,900001,900003,'CROSS','active','2026-09-28','2026-09-28'),
(910008,900005,900002,'FROM_INACTIVE_NODE','active','2026-09-28','2026-09-28');
COMMIT;
