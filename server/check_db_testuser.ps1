$sql = "SELECT count(*) FROM foreign_worker_master WHERE user_id='testuser';"
psql -d loandoc -U postgres -c $sql