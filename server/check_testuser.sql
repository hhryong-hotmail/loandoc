-- check_testuser.sql
SELECT count(*) AS cnt FROM foreign_worker_master WHERE user_id='testuser';
SELECT user_id, password FROM foreign_worker_master WHERE user_id LIKE 'testuser%' LIMIT 10;
\d+ foreign_worker_master
