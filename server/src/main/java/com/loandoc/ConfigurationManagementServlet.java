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
                            + "rejected_date, rejection_reason, prod_scheduled_date "
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
                    + "rejected_date, rejection_reason, prod_scheduled_date "
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

        try {
            int id = requestBody.has("id") ? requestBody.get("id").asInt() : 0;
            String approvalNumber = requestBody.has("approval_number") ? requestBody.get("approval_number").asText() : null;
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
            String sql = "UPDATE github_history SET approval_number = ?, target_server = ?, env_type = ?, "
                    + "stage_type = ?, submitted_date = CURRENT_TIMESTAMP, prod_scheduled_date = ? WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, approvalNumber);
            stmt.setString(2, targetServer);
            stmt.setString(3, envType);
            stmt.setString(4, stageType);
            stmt.setTimestamp(5, prodScheduledDate);
            stmt.setInt(6, id);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "결재 요청이 제출되었습니다");

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

    private void approveChange(JsonNode requestBody, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            int id = requestBody.has("id") ? requestBody.get("id").asInt() : 0;
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

            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "UPDATE github_history SET approved_date = CURRENT_TIMESTAMP, "
                    + "test_apply_date = ?, prod_apply_date = ?, rejected_date = NULL, rejection_reason = NULL WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setTimestamp(1, testApplyDate);
            stmt.setTimestamp(2, prodApplyDate);
            stmt.setInt(3, id);

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

            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "UPDATE github_history SET rejected_date = CURRENT_TIMESTAMP, rejection_reason = ?, "
                    + "approved_date = NULL WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, rejectionReason);
            stmt.setInt(2, id);

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

