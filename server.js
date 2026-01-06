const express = require('express');
const { Pool } = require('pg');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = 3333;

// PostgreSQL 연결 설정
const pool = new Pool({
    host: process.env.DB_HOST || 'localhost',
    port: process.env.DB_PORT || 5432,
    database: process.env.DB_NAME || 'loandoc',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
    encoding: 'UTF8'
});

// 미들웨어 설정
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 정적 파일 서빙 (HTML, CSS, JS)
app.use(express.static(path.join(__dirname, 'public')));

// PostgreSQL 연결 테스트
pool.on('connect', () => {
    console.log('PostgreSQL 연결 성공');
});

pool.on('error', (err) => {
    console.error('PostgreSQL 연결 오류:', err);
});

// GET /api/github-history - 목록 조회
app.get('/api/github-history', async (req, res) => {
    try {
        const { q, status, env_type, stage_type } = req.query;
        
        let sql = `
            SELECT id, database_name, repo_name, change_datetime, program_name, change_reason,
                   developer_name, important_code_content, approval_number, target_server, env_type,
                   stage_type, test_apply_date, prod_apply_date, submitted_date, approved_date,
                   rejected_date, rejection_reason, prod_scheduled_date, approver, work_content,
                   approval_reason
            FROM github_history
            WHERE 1=1
        `;
        
        const params = [];
        let paramIndex = 1;
        
        // 검색어 필터
        if (q && q.trim() !== '') {
            sql += ` AND (program_name ILIKE $${paramIndex} OR change_reason ILIKE $${paramIndex + 1} OR developer_name ILIKE $${paramIndex + 2})`;
            const searchPattern = `%${q}%`;
            params.push(searchPattern, searchPattern, searchPattern);
            paramIndex += 3;
        }
        
        // 상태 필터
        if (status && status !== '전체') {
            if (status === '제출됨') {
                sql += ` AND submitted_date IS NOT NULL AND approved_date IS NULL AND rejected_date IS NULL`;
            } else if (status === '승인됨') {
                sql += ` AND approved_date IS NOT NULL`;
            } else if (status === '반려됨') {
                sql += ` AND rejected_date IS NOT NULL`;
            } else if (status === '대기중') {
                sql += ` AND submitted_date IS NULL`;
            }
        }
        
        // 환경 타입 필터
        if (env_type && env_type !== '전체') {
            sql += ` AND env_type = $${paramIndex}`;
            params.push(env_type);
            paramIndex++;
        }
        
        // 단계 타입 필터
        if (stage_type && stage_type !== '전체') {
            sql += ` AND stage_type = $${paramIndex}`;
            params.push(stage_type);
            paramIndex++;
        }
        
        sql += ` ORDER BY change_datetime DESC`;
        
        const result = await pool.query(sql, params);
        
        // 상태 계산
        const rows = result.rows.map(row => {
            let status = '대기중';
            if (row.submitted_date && !row.approved_date && !row.rejected_date) {
                status = '제출됨';
            } else if (row.approved_date) {
                status = '승인됨';
            } else if (row.rejected_date) {
                status = '반려됨';
            }
            
            return {
                ...row,
                status: status
            };
        });
        
        res.json({
            ok: true,
            rows: rows
        });
    } catch (error) {
        console.error('목록 조회 오류:', error);
        res.status(500).json({
            ok: false,
            error: error.message
        });
    }
});

// GET /api/github-history/:id - 단일 항목 조회
app.get('/api/github-history/:id', async (req, res) => {
    try {
        const { id } = req.params;
        
        const result = await pool.query(
            `SELECT id, database_name, repo_name, change_datetime, program_name, change_reason,
                    developer_name, important_code_content, approval_number, target_server, env_type,
                    stage_type, test_apply_date, prod_apply_date, submitted_date, approved_date,
                    rejected_date, rejection_reason, prod_scheduled_date, approver, work_content,
                    approval_reason
             FROM github_history
             WHERE id = $1`,
            [id]
        );
        
        if (result.rows.length === 0) {
            return res.status(404).json({
                ok: false,
                error: '항목을 찾을 수 없습니다.'
            });
        }
        
        const row = result.rows[0];
        let status = '대기중';
        if (row.submitted_date && !row.approved_date && !row.rejected_date) {
            status = '제출됨';
        } else if (row.approved_date) {
            status = '승인됨';
        } else if (row.rejected_date) {
            status = '반려됨';
        }
        
        res.json({
            ok: true,
            row: {
                ...row,
                status: status
            }
        });
    } catch (error) {
        console.error('상세 조회 오류:', error);
        res.status(500).json({
            ok: false,
            error: error.message
        });
    }
});

// PUT /api/github-history/:id - 항목 업데이트
app.put('/api/github-history/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const updateData = req.body;
        
        // 업데이트 가능한 필드 목록
        const allowedFields = [
            'approval_number', 'target_server', 'env_type', 'stage_type',
            'test_apply_date', 'prod_apply_date', 'submitted_date', 'approved_date',
            'rejected_date', 'rejection_reason', 'prod_scheduled_date', 'approver',
            'work_content', 'approval_reason'
        ];
        
        const updateFields = [];
        const values = [];
        let paramIndex = 1;
        
        for (const field of allowedFields) {
            if (updateData.hasOwnProperty(field)) {
                updateFields.push(`${field} = $${paramIndex}`);
                values.push(updateData[field] || null);
                paramIndex++;
            }
        }
        
        if (updateFields.length === 0) {
            return res.status(400).json({
                ok: false,
                error: '업데이트할 필드가 없습니다.'
            });
        }
        
        // updated_at 자동 업데이트
        updateFields.push(`updated_at = CURRENT_TIMESTAMP`);
        
        values.push(id);
        
        const sql = `
            UPDATE github_history
            SET ${updateFields.join(', ')}
            WHERE id = $${paramIndex}
            RETURNING *
        `;
        
        const result = await pool.query(sql, values);
        
        if (result.rows.length === 0) {
            return res.status(404).json({
                ok: false,
                error: '항목을 찾을 수 없습니다.'
            });
        }
        
        res.json({
            ok: true,
            message: '업데이트되었습니다.',
            row: result.rows[0]
        });
    } catch (error) {
        console.error('업데이트 오류:', error);
        res.status(500).json({
            ok: false,
            error: error.message
        });
    }
});

// 서버 시작
app.listen(PORT, () => {
    console.log(`서버가 http://localhost:${PORT} 에서 실행 중입니다.`);
});
