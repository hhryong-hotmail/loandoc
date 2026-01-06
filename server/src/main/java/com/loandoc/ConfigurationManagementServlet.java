package com.loandoc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

// 형상관리 시스템 서블릿
public class ConfigurationManagementServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/loandoc";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[ConfigurationManagementServlet] PostgreSQL JDBC Driver not found: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        ObjectMapper mapper = new ObjectMapper();

        if (pathInfo != null && !pathInfo.equals("/")) {
            // GET /api/config_management/{id} - Get single history record
            String idStr = pathInfo.substring(1);
            try {
                int historyId = Integer.parseInt(idStr);
                getHistoryRecord(historyId, resp, mapper);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
            }
        } else {
            // GET /api/config_management?q=&status=&env_type= - List history records
            String query = req.getParameter("q");
            String status = req.getParameter("status");
            String envType = req.getParameter("env_type");
            String stageType = req.getParameter("stage_type");
            listHistoryRecords(query, status, envType, stageType, resp, mapper);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode requestBody;

        try {
            requestBody = mapper.readTree(req.getReader());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid JSON\"}");
            return;
        }

        String action = requestBody.has("action") ? requestBody.get("action").asText() : null;

        if ("submit".equals(action)) {
            // 제출 (결재 요청)
            submitForApproval(requestBody, resp, mapper);
        } else if ("approve".equals(action)) {
            // 승인
            approveChange(requestBody, resp, mapper);
        } else if ("reject".equals(action)) {
            // 반려
            rejectChange(requestBody, resp, mapper);
        } else if ("update".equals(action)) {
            // 상태 업데이트
            updateStatus(requestBody, resp, mapper);
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid action\"}");
        }
    }

    private void listHistoryRecords(String query, String status, String envType, String stageType,
            HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            StringBuilder sql = new StringBuilder(
                    "SELECT id, database_name, repo_name, change_datetime, program_name, change_reason, "
                            + "developer_name, important_code_content, approval_number, target_server, env_type, "
                            + "stage_type, test_apply_date, prod_apply_date, submitted_date, approved_date, "
                            + "rejected_date, rejection_reason, prod_scheduled_date, approver, work_content, "
                            + "approval_reason "
                            + "FROM github_history WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (query != null && !query.trim().isEmpty()) {
                sql.append(" AND (program_name ILIKE ? OR change_reason ILIKE ? OR developer_name ILIKE ?)");
                String searchPattern = "%" + query + "%";
                params.add(searchPattern);
                params.add(searchPattern);
                params.add(searchPattern);
            }

            if (status != null && !status.trim().isEmpty() && !status.equals("전체")) {
                // status에 따라 필터링 (제출됨, 승인됨, 반려됨, 대기중)
                if ("제출됨".equals(status)) {
                    sql.append(" AND submitted_date IS NOT NULL AND approved_date IS NULL AND rejected_date IS NULL");
                } else if ("승인됨".equals(status)) {
                    sql.append(" AND approved_date IS NOT NULL");
                } else if ("반려됨".equals(status)) {
                    sql.append(" AND rejected_date IS NOT NULL");
                } else if ("대기중".equals(status)) {
                    sql.append(" AND submitted_date IS NULL");
                }
            }

            if (envType != null && !envType.trim().isEmpty() && !envType.equals("전체")) {
                sql.append(" AND env_type = ?");
                params.add(envType);
            }

            if (stageType != null && !stageType.trim().isEmpty() && !stageType.equals("전체")) {
                sql.append(" AND stage_type = ?");
                params.add(stageType);
            }

            sql.append(" ORDER BY change_datetime DESC");

            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) {
                    stmt.setString(i + 1, (String) param);
                } else {
                    stmt.setObject(i + 1, param);
                }
            }

            rs = stmt.executeQuery();

            ArrayNode rows = mapper.createArrayNode();
            while (rs.next()) {
                ObjectNode row = mapper.createObjectNode();
                row.put("id", rs.getInt("id"));
                row.put("database_name", rs.getString("database_name"));
                row.put("repo_name", rs.getString("repo_name"));
                Timestamp changeDatetime = rs.getTimestamp("change_datetime");
                row.put("change_datetime", changeDatetime != null ? changeDatetime.toString() : null);
                row.put("program_name", rs.getString("program_name"));
                row.put("change_reason", rs.getString("change_reason"));
                row.put("developer_name", rs.getString("developer_name"));
                row.put("important_code_content", rs.getString("important_code_content"));
                row.put("approval_number", rs.getString("approval_number"));
                row.put("target_server", rs.getString("target_server"));
                row.put("env_type", rs.getString("env_type"));
                row.put("stage_type", rs.getString("stage_type"));
                Timestamp testApplyDate = rs.getTimestamp("test_apply_date");
                row.put("test_apply_date", testApplyDate != null ? testApplyDate.toString() : null);
                Timestamp prodApplyDate = rs.getTimestamp("prod_apply_date");
                row.put("prod_apply_date", prodApplyDate != null ? prodApplyDate.toString() : null);
                Timestamp submittedDate = rs.getTimestamp("submitted_date");
                row.put("submitted_date", submittedDate != null ? submittedDate.toString() : null);
                Timestamp approvedDate = rs.getTimestamp("approved_date");
                row.put("approved_date", approvedDate != null ? approvedDate.toString() : null);
                Timestamp rejectedDate = rs.getTimestamp("rejected_date");
                row.put("rejected_date", rejectedDate != null ? rejectedDate.toString() : null);
                row.put("rejection_reason", rs.getString("rejection_reason"));
                Timestamp prodScheduledDate = rs.getTimestamp("prod_scheduled_date");
                row.put("prod_scheduled_date", prodScheduledDate != null ? prodScheduledDate.toString() : null);
                row.put("approver", rs.getString("approver"));
                row.put("work_content", rs.getString("work_content"));
                row.put("approval_reason", rs.getString("approval_reason"));

                // 상태 계산
                String currentStatus = "대기중";
                if (rejectedDate != null) {
                    currentStatus = "반려됨";
                } else if (approvedDate != null) {
                    currentStatus = "승인됨";
                } else if (submittedDate != null) {
                    currentStatus = "제출됨";
                }
                row.put("status", currentStatus);

                rows.add(row);
            }

            ObjectNode response = mapper.createObjectNode();
            response.put("ok", true);
            response.set("rows", rows);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(mapper.writeValueAsString(response));

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(rs, stmt, conn);
        }
    }

    private void getHistoryRecord(int id, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "SELECT id, database_name, repo_name, change_datetime, program_name, change_reason, "
                    + "developer_name, important_code_content, approval_number, target_server, env_type, "
                    + "stage_type, test_apply_date, prod_apply_date, submitted_date, approved_date, "
                    + "rejected_date, rejection_reason, prod_scheduled_date, approver, work_content, "
                    + "approval_reason "
                    + "FROM github_history WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            rs = stmt.executeQuery();

            if (rs.next()) {
                ObjectNode row = mapper.createObjectNode();
                row.put("id", rs.getInt("id"));
                row.put("database_name", rs.getString("database_name"));
                row.put("repo_name", rs.getString("repo_name"));
                Timestamp changeDatetime = rs.getTimestamp("change_datetime");
                row.put("change_datetime", changeDatetime != null ? changeDatetime.toString() : null);
                row.put("program_name", rs.getString("program_name"));
                row.put("change_reason", rs.getString("change_reason"));
                row.put("developer_name", rs.getString("developer_name"));
                row.put("important_code_content", rs.getString("important_code_content"));
                row.put("approval_number", rs.getString("approval_number"));
                row.put("target_server", rs.getString("target_server"));
                row.put("env_type", rs.getString("env_type"));
                row.put("stage_type", rs.getString("stage_type"));
                Timestamp testApplyDate = rs.getTimestamp("test_apply_date");
                row.put("test_apply_date", testApplyDate != null ? testApplyDate.toString() : null);
                Timestamp prodApplyDate = rs.getTimestamp("prod_apply_date");
                row.put("prod_apply_date", prodApplyDate != null ? prodApplyDate.toString() : null);
                Timestamp submittedDate = rs.getTimestamp("submitted_date");
                row.put("submitted_date", submittedDate != null ? submittedDate.toString() : null);
                Timestamp approvedDate = rs.getTimestamp("approved_date");
                row.put("approved_date", approvedDate != null ? approvedDate.toString() : null);
                Timestamp rejectedDate = rs.getTimestamp("rejected_date");
                row.put("rejected_date", rejectedDate != null ? rejectedDate.toString() : null);
                row.put("rejection_reason", rs.getString("rejection_reason"));
                Timestamp prodScheduledDate = rs.getTimestamp("prod_scheduled_date");
                row.put("prod_scheduled_date", prodScheduledDate != null ? prodScheduledDate.toString() : null);
                row.put("approver", rs.getString("approver"));
                row.put("work_content", rs.getString("work_content"));
                row.put("approval_reason", rs.getString("approval_reason"));

                // 상태 계산
                String currentStatus = "대기중";
                if (rejectedDate != null) {
                    currentStatus = "반려됨";
                } else if (approvedDate != null) {
                    currentStatus = "승인됨";
                } else if (submittedDate != null) {
                    currentStatus = "제출됨";
                }
                row.put("status", currentStatus);

                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.set("row", row);

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Record not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(rs, stmt, conn);
        }
    }

    private void submitForApproval(JsonNode requestBody, HttpServletResponse resp, ObjectMapper mapper)
            throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            int id = requestBody.has("id") ? requestBody.get("id").asInt() : 0;
            String targetServer = requestBody.has("target_server") ? requestBody.get("target_server").asText() : null;
            String envType = requestBody.has("env_type") ? requestBody.get("env_type").asText() : null;
            String stageType = requestBody.has("stage_type") ? requestBody.get("stage_type").asText() : null;
            Timestamp prodScheduledDate = null;
            if (requestBody.has("prod_scheduled_date") && !requestBody.get("prod_scheduled_date").isNull()) {
                try {
                    prodScheduledDate = Timestamp.valueOf(requestBody.get("prod_scheduled_date").asText());
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }

            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // 결재번호 자동 생성: YYYYMMDD-001 형식
            java.util.Calendar cal = java.util.Calendar.getInstance();
            String datePrefix = String.format("%04d%02d%02d", 
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH));
            
            // 같은 날짜에 제출된 결재 개수 조회 (submitted_date가 오늘인 것들)
            String countSql = "SELECT COUNT(*) FROM github_history WHERE submitted_date IS NOT NULL AND submitted_date::date = CURRENT_DATE";
            stmt = conn.prepareStatement(countSql);
            rs = stmt.executeQuery();
            int count = 0;
            if (rs.next()) {
                count = rs.getInt(1);
            }
            rs.close();
            stmt.close();
            
            // 다음 번호 생성 (001, 002, ...)
            int nextNumber = count + 1;
            String approvalNumber = String.format("%s-%03d", datePrefix, nextNumber);

            // 승인자 랜덤 할당 (김종호, 하현용, 문규식)
            String[] approvers = {"김종호", "하현용", "문규식"};
            java.util.Random random = new java.util.Random();
            String approver = approvers[random.nextInt(approvers.length)];

            // 작업내용 생성 (변경사유를 기반으로)
            String workContent = requestBody.has("work_content") ? requestBody.get("work_content").asText() : null;
            if (workContent == null || workContent.trim().isEmpty()) {
                // 변경사유를 가져와서 작업내용으로 사용
                String getReasonSql = "SELECT change_reason FROM github_history WHERE id = ?";
                PreparedStatement getReasonStmt = conn.prepareStatement(getReasonSql);
                getReasonStmt.setInt(1, id);
                ResultSet reasonRs = getReasonStmt.executeQuery();
                if (reasonRs.next()) {
                    workContent = reasonRs.getString("change_reason");
                }
                reasonRs.close();
                getReasonStmt.close();
            }

            String sql = "UPDATE github_history SET approval_number = ?, target_server = ?, env_type = ?, "
                    + "stage_type = ?, submitted_date = CURRENT_TIMESTAMP, prod_scheduled_date = ?, "
                    + "approver = ?, work_content = ? WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, approvalNumber);
            stmt.setString(2, targetServer);
            stmt.setString(3, envType);
            stmt.setString(4, stageType);
            stmt.setTimestamp(5, prodScheduledDate);
            stmt.setString(6, approver);
            stmt.setString(7, workContent);
            stmt.setInt(8, id);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "결재 요청이 제출되었습니다");
                response.put("approval_number", approvalNumber);

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Record not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(rs, stmt, conn);
        }
    }

    private void approveChange(JsonNode requestBody, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            int id = requestBody.has("id") ? requestBody.get("id").asInt() : 0;
            Timestamp approvedDate = null;
            if (requestBody.has("approved_date") && !requestBody.get("approved_date").isNull()) {
                try {
                    // YYYY-MM-DD HH:MM 형식을 파싱
                    String dateStr = requestBody.get("approved_date").asText();
                    approvedDate = Timestamp.valueOf(dateStr + ":00"); // 초를 추가하여 Timestamp 형식으로 변환
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 현재 시간 사용
                    approvedDate = new Timestamp(System.currentTimeMillis());
                }
            } else {
                approvedDate = new Timestamp(System.currentTimeMillis());
            }
            
            Timestamp testApplyDate = null;
            if (requestBody.has("test_apply_date") && !requestBody.get("test_apply_date").isNull()) {
                try {
                    testApplyDate = Timestamp.valueOf(requestBody.get("test_apply_date").asText());
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }
            Timestamp prodApplyDate = null;
            if (requestBody.has("prod_apply_date") && !requestBody.get("prod_apply_date").isNull()) {
                try {
                    prodApplyDate = Timestamp.valueOf(requestBody.get("prod_apply_date").asText());
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }

            String approvalReason = null;
            if (requestBody.has("approval_reason") && !requestBody.get("approval_reason").isNull()) {
                approvalReason = requestBody.get("approval_reason").asText();
            }

            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "UPDATE github_history SET approved_date = ?, "
                    + "test_apply_date = ?, prod_apply_date = ?, approval_reason = ?, rejected_date = NULL, rejection_reason = NULL WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setTimestamp(1, approvedDate);
            stmt.setTimestamp(2, testApplyDate);
            stmt.setTimestamp(3, prodApplyDate);
            stmt.setString(4, approvalReason);
            stmt.setInt(5, id);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "변경사항이 승인되었습니다");

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Record not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(null, stmt, conn);
        }
    }

    private void rejectChange(JsonNode requestBody, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            int id = requestBody.has("id") ? requestBody.get("id").asInt() : 0;
            String rejectionReason = requestBody.has("rejection_reason") ? requestBody.get("rejection_reason").asText() : null;
            
            Timestamp rejectedDate = null;
            if (requestBody.has("rejected_date") && !requestBody.get("rejected_date").isNull()) {
                try {
                    // YYYY-MM-DD HH:MM 형식을 파싱
                    String dateStr = requestBody.get("rejected_date").asText();
                    rejectedDate = Timestamp.valueOf(dateStr + ":00"); // 초를 추가하여 Timestamp 형식으로 변환
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 현재 시간 사용
                    rejectedDate = new Timestamp(System.currentTimeMillis());
                }
            } else {
                rejectedDate = new Timestamp(System.currentTimeMillis());
            }

            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "UPDATE github_history SET rejected_date = ?, rejection_reason = ?, "
                    + "approved_date = NULL WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setTimestamp(1, rejectedDate);
            stmt.setString(2, rejectionReason);
            stmt.setInt(3, id);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "변경사항이 반려되었습니다");

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Record not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(null, stmt, conn);
        }
    }

    private void updateStatus(JsonNode requestBody, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            int id = requestBody.has("id") ? requestBody.get("id").asInt() : 0;
            String targetServer = requestBody.has("target_server") ? requestBody.get("target_server").asText() : null;
            String envType = requestBody.has("env_type") ? requestBody.get("env_type").asText() : null;
            String stageType = requestBody.has("stage_type") ? requestBody.get("stage_type").asText() : null;
            Timestamp testApplyDate = null;
            if (requestBody.has("test_apply_date") && !requestBody.get("test_apply_date").isNull()) {
                try {
                    testApplyDate = Timestamp.valueOf(requestBody.get("test_apply_date").asText());
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }
            Timestamp prodApplyDate = null;
            if (requestBody.has("prod_apply_date") && !requestBody.get("prod_apply_date").isNull()) {
                try {
                    prodApplyDate = Timestamp.valueOf(requestBody.get("prod_apply_date").asText());
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }
            Timestamp prodScheduledDate = null;
            if (requestBody.has("prod_scheduled_date") && !requestBody.get("prod_scheduled_date").isNull()) {
                try {
                    prodScheduledDate = Timestamp.valueOf(requestBody.get("prod_scheduled_date").asText());
                } catch (Exception e) {
                    // 날짜 파싱 실패 시 무시
                }
            }

            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            StringBuilder sql = new StringBuilder("UPDATE github_history SET ");
            List<Object> params = new ArrayList<>();
            boolean first = true;

            if (targetServer != null) {
                if (!first) sql.append(", ");
                sql.append("target_server = ?");
                params.add(targetServer);
                first = false;
            }
            if (envType != null) {
                if (!first) sql.append(", ");
                sql.append("env_type = ?");
                params.add(envType);
                first = false;
            }
            if (stageType != null) {
                if (!first) sql.append(", ");
                sql.append("stage_type = ?");
                params.add(stageType);
                first = false;
            }
            if (testApplyDate != null) {
                if (!first) sql.append(", ");
                sql.append("test_apply_date = ?");
                params.add(testApplyDate);
                first = false;
            }
            if (prodApplyDate != null) {
                if (!first) sql.append(", ");
                sql.append("prod_apply_date = ?");
                params.add(prodApplyDate);
                first = false;
            }
            if (prodScheduledDate != null) {
                if (!first) sql.append(", ");
                sql.append("prod_scheduled_date = ?");
                params.add(prodScheduledDate);
                first = false;
            }

            if (params.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"ok\":false,\"error\":\"No fields to update\"}");
                return;
            }

            sql.append(" WHERE id = ?");
            params.add(id);

            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) {
                    stmt.setString(i + 1, (String) param);
                } else if (param instanceof Timestamp) {
                    stmt.setTimestamp(i + 1, (Timestamp) param);
                } else if (param instanceof Integer) {
                    stmt.setInt(i + 1, (Integer) param);
                } else {
                    stmt.setObject(i + 1, param);
                }
            }

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "상태가 업데이트되었습니다");

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Record not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(null, stmt, conn);
        }
    }

    private void closeResources(ResultSet rs, PreparedStatement stmt, Connection conn) {
        try {
            if (rs != null)
                rs.close();
            if (stmt != null)
                stmt.close();
            if (conn != null)
                conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

