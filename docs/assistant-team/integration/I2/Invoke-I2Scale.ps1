$ErrorActionPreference='Stop'
$existing=[int](Invoke-I2Sql 'root' $adminPassword "SELECT COUNT(*) FROM knowledge_libraries WHERE id=7901;" $db)
if($existing -ne 0){throw 'Scale sample already exists; no automatic reset/overwrite'}
$password=New-Password;$secrets.Add($password)
[void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' IDENTIFIED BY '$password' ACCOUNT UNLOCK;")
$digits='(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)'
$numbers="(SELECT a.n+10*b.n+100*c.n+1000*d.n n FROM $digits a CROSS JOIN $digits b CROSS JOIN $digits c CROSS JOIN $digits d)"
$sql=@"
START TRANSACTION;
INSERT INTO knowledge_libraries(id,name,type,status,created_at,updated_at) VALUES(7901,'I2 scale synthetic library','topic','active','2026-09-27','2026-09-27');
INSERT INTO knowledge_nodes(id,canonical_name,node_type,definition,status,created_at,updated_at)
SELECT 100000+n,CONCAT('I2PERF node ',LPAD(n,5,'0')),'concept','I2PERF synthetic performance fact','active','2026-09-27','2026-09-27' FROM $numbers q;
INSERT INTO library_nodes(library_id,node_id) SELECT 7901,id FROM knowledge_nodes WHERE id>=100000 AND id<110000;
INSERT INTO knowledge_edges(id,source_node_id,target_node_id,relation_type,status,created_at,updated_at)
SELECT 200000+q.n*3+k.n,100000+q.n,100000+MOD(q.n+k.n+1,10000),'I2PERF_R','active','2026-09-27','2026-09-27' FROM $numbers q CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2) k;
INSERT INTO documents(id,library_id,original_name,stored_name,extension,size_bytes,sha256,storage_path,status,created_at,updated_at)
SELECT 300000+n,7901,CONCAT('i2-perf-',n,'.txt'),CONCAT('i2-perf-',n,'.txt'),'txt',0,SHA2(CONCAT('I2PERF metadata ',n),256),CONCAT('D:/kg-i2-env/scale-absent-files/',n,'.txt'),'COMPLETED','2026-09-27','2026-09-27' FROM $numbers q WHERE n<2000;
INSERT INTO document_units(id,document_id,unit_type,unit_index,source_locator,status,created_at)
SELECT 400000+n,300000+n,'TEXT',1,CONCAT('I2PERF:',n),'READY','2026-09-27' FROM $numbers q WHERE n<2000;
INSERT INTO document_chunks(id,unit_id,chunk_index,content,content_hash,start_offset,end_offset,created_at)
SELECT 500000+q.n*10+k.n,400000+q.n,k.n,CONCAT('I2PERF synthetic source ',q.n,' chunk ',k.n),SHA2(CONCAT('I2PERF synthetic source ',q.n,' chunk ',k.n),256),0,CHAR_LENGTH(CONCAT('I2PERF synthetic source ',q.n,' chunk ',k.n)),'2026-09-27' FROM $numbers q CROSS JOIN $digits k WHERE q.n<2000;
INSERT INTO node_evidence(node_id,chunk_id,created_at) SELECT id,500000+MOD(id-100000,20000),'2026-09-27' FROM knowledge_nodes WHERE id>=100000 AND id<110000;
COMMIT;
"@
try{[void](Invoke-I2Sql 'kg_i2_loader' $password $sql $db)}finally{[void](Invoke-I2Sql 'root' $adminPassword "ALTER USER 'kg_i2_loader'@'127.0.0.1' ACCOUNT LOCK;")}
$proof=@{stage='I2';kind='derived_scale_fixture_not_frozen_I1_base';libraryId=7901;nodes=10000;edges=30000;documents=2000;chunks=20000;realOriginalFiles=$false;note='Synthetic DB query load only; not parser or original-file availability acceptance';loaderLocked=$true;sqlSha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($sql))).ToLowerInvariant()}
[IO.File]::WriteAllText((Join-Path $reportRoot 'I2-SCALE-FIXTURE.json'),($proof|ConvertTo-Json -Depth 5),$utf8)
& (Join-Path $reportRoot 'Invoke-I2BaseVerification.ps1') -Suite Scale -Case SCALE
