-- 안전한 절차: foreign_worker_master의 user_id를 PRIMARY KEY(또는 UNIQUE)로 설정
-- 사용법: psql -U postgres -h localhost -d loandoc -f sql_add_pk_foreign_worker_master.sql
-- 주의: 이 스크립트는 데이터 변경을 합니다. 실행 전에 전체 백업을 권장합니다.

-- 1) 백업 (사용자가 직접 수행할 수 있도록 안내)
-- 권장: pg_dump -U postgres -h localhost -p 5432 -F c -f "backup_loandoc_before_pk.dump" loandoc

BEGIN;

-- 2) 중복 user_id 확인
CREATE TEMP TABLE tmp_duplicates AS
SELECT user_id, count(*) AS cnt
FROM foreign_worker_master
GROUP BY user_id
HAVING count(*) > 1;

-- 보고: 중복이 있는 경우 행을 보여줌
SELECT * FROM tmp_duplicates;

-- 3) 중복 제거 (옵션) - 가장 오래된(또는 새로운) 행을 유지하도록 조정
-- 여기서는 ctid를 이용해 첫 번째 행만 남기고 나머지 제거
-- 주의: 실제 운영에서는 어떤 행을 보존할지 비즈니스 규칙에 따라 결정해야 함
WITH to_delete AS (
  SELECT f.ctid
  FROM foreign_worker_master f
  JOIN tmp_duplicates d ON f.user_id = d.user_id
  ORDER BY f.user_id, f.ctid
  OFFSET 1
)
DELETE FROM foreign_worker_master WHERE ctid IN (SELECT ctid FROM to_delete);

-- 4) user_id에 대해 UNIQUE 제약 추가 (안전한 방법)
ALTER TABLE foreign_worker_master
ADD CONSTRAINT foreign_worker_master_user_id_unique UNIQUE (user_id);

-- 만약 PRIMARY KEY로 만들고 싶다면(테이블에 이미 PK가 없다면)
-- 주의: PRIMARY KEY 추가 전에 user_id가 NOT NULL인지 확인
-- ALTER TABLE foreign_worker_master ALTER COLUMN user_id SET NOT NULL;
-- ALTER TABLE foreign_worker_master ADD CONSTRAINT foreign_worker_master_pkey PRIMARY KEY (user_id);

COMMIT;

-- 끝. 제약 추가 후에는 애플리케이션 INSERT 로직에서 중복 발생 시 23505 SQLSTATE를 처리하면 됩니다.
