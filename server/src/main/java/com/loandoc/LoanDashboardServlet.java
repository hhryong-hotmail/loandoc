package com.loandoc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = {"/server/loan_dashboard"})
public class LoanDashboardServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/loandoc";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";
    private static final long serialVersionUID = 1L;
    
    static {
        try {
            // Ensure the PostgreSQL JDBC driver is loaded and registered with DriverManager
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            // If driver is not available, keep going; errors will be reported when attempting connections
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
            // GET /api/loan_dashboard/{id}
            String idStr = pathInfo.substring(1);
            try {
                int id = Integer.parseInt(idStr);
                getRequest(id, resp, mapper);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
            }
        } else {
            // list: /api/loan_dashboard?q=&req_type=&order=(asc|desc)
            String q = req.getParameter("q");
            String reqType = req.getParameter("req_type");
            String order = req.getParameter("order");
            listRequests(q, reqType, order, resp, mapper);
        }
        // (테스트용) 임시 응답 코드 예시
        // resp.getWriter().write("{\"ok\": true, \"rows\": []}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode body;
        try { body = mapper.readTree(req.getReader()); } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid JSON\"}");
            return;
        }

        String reqLogin = body.has("req_login") ? body.get("req_login").asText() : null;
        String title = body.has("title") ? body.get("title").asText() : null;
        if (reqLogin == null || reqLogin.trim().isEmpty() || title == null || title.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"req_login and title are required\"}");
            return;
        }

        String counseler = body.has("counseler") ? body.get("counseler").asText() : null;
        String name = body.has("name") ? body.get("name").asText() : null;
        String phone = body.has("phone_number") ? body.get("phone_number").asText() : null;
        String nationality = body.has("nationality") ? body.get("nationality").asText() : null;
        String reqType = body.has("req_type") ? body.get("req_type").asText() : null;
        String content = body.has("content") ? body.get("content").asText() : null;
        createRequest(reqLogin, counseler, name, phone, nationality, reqType, title, content, resp, mapper);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"ID required\"}");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            int id = Integer.parseInt(pathInfo.substring(1));
            JsonNode body = mapper.readTree(req.getReader());
            String counseler = body.has("counseler") ? body.get("counseler").asText() : null;
            String name = body.has("name") ? body.get("name").asText() : null;
            String phone = body.has("phone_number") ? body.get("phone_number").asText() : null;
            String nationality = body.has("nationality") ? body.get("nationality").asText() : null;
            String reqType = body.has("req_type") ? body.get("req_type").asText() : null;
            String title = body.has("title") ? body.get("title").asText() : null;
            String content = body.has("content") ? body.get("content").asText() : null;
            updateRequest(id, counseler, name, phone, nationality, reqType, title, content, resp, mapper);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"ID required\"}");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            int id = Integer.parseInt(pathInfo.substring(1));
            deleteRequest(id, resp, mapper);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
        }
    }

    private void listRequests(String q, String reqType, String order, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            StringBuilder sql = new StringBuilder("SELECT req_id, req_login, counseler, name, phone_number, nationality, req_type, title, content, created_at, updated_at FROM loan_dashboard WHERE 1=1");
            List<String> params = new ArrayList<>();
            if (reqType != null && !reqType.trim().isEmpty() && !reqType.equals("전체")) {
                sql.append(" AND req_type = ?");
                params.add(reqType);
            }
            if (q != null && !q.trim().isEmpty()) {
                sql.append(" AND (title ILIKE ? OR content ILIKE ? OR req_login ILIKE ? OR name ILIKE ?)");
                String p = "%" + q + "%";
                params.add(p); params.add(p); params.add(p); params.add(p);
            }
            // Allow optional ordering by req_id. Default is DESC (new requirement).
            String ord = (order != null && order.equalsIgnoreCase("asc")) ? "ASC" : "DESC";
            sql.append(" ORDER BY req_id " + ord);

            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) stmt.setString(i+1, params.get(i));

            rs = stmt.executeQuery();
            ArrayNode rows = mapper.createArrayNode();
            while (rs.next()) {
                ObjectNode row = mapper.createObjectNode();
                row.put("req_id", rs.getInt("req_id"));
                row.put("req_login", rs.getString("req_login"));
                row.put("counseler", rs.getString("counseler"));
                row.put("name", rs.getString("name"));
                row.put("phone_number", rs.getString("phone_number"));
                row.put("nationality", rs.getString("nationality"));
                row.put("req_type", rs.getString("req_type"));
                row.put("title", rs.getString("title"));
                row.put("content", rs.getString("content"));
                row.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");
                row.put("updated_at", rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : "");
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

    private void getRequest(int id, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null; PreparedStatement stmt = null; ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "SELECT req_id, req_login, counseler, name, phone_number, nationality, req_type, title, content, created_at, updated_at FROM loan_dashboard WHERE req_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            rs = stmt.executeQuery();
            if (rs.next()) {
                ObjectNode row = mapper.createObjectNode();
                row.put("req_id", rs.getInt("req_id"));
                row.put("req_login", rs.getString("req_login"));
                row.put("counseler", rs.getString("counseler"));
                row.put("name", rs.getString("name"));
                row.put("phone_number", rs.getString("phone_number"));
                row.put("nationality", rs.getString("nationality"));
                row.put("req_type", rs.getString("req_type"));
                row.put("title", rs.getString("title"));
                row.put("content", rs.getString("content"));
                row.put("created_at", rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toString() : "");
                row.put("updated_at", rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toString() : "");

                ObjectNode respJson = mapper.createObjectNode(); respJson.put("ok", true); respJson.set("row", row);
                resp.setStatus(HttpServletResponse.SC_OK); resp.getWriter().write(mapper.writeValueAsString(respJson));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Not found\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace(); resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally { closeResources(rs, stmt, conn); }
    }

    private void createRequest(String reqLogin, String counseler, String name, String phone, String nationality, String reqType, String title, String reqContent, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null; PreparedStatement stmt = null; ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "INSERT INTO loan_dashboard (req_login, counseler, name, phone_number, nationality, req_type, title, content) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING req_id";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, reqLogin);
            stmt.setString(2, counseler);
            stmt.setString(3, name);
            stmt.setString(4, phone);
            stmt.setString(5, nationality);
            stmt.setString(6, reqType);
            stmt.setString(7, title);
            stmt.setString(8, reqContent);
            rs = stmt.executeQuery();
            if (rs.next()) {
                int newId = rs.getInt(1);
                ObjectNode respJson = mapper.createObjectNode(); respJson.put("ok", true); respJson.put("req_id", newId);
                resp.setStatus(HttpServletResponse.SC_OK); resp.getWriter().write(mapper.writeValueAsString(respJson));
            }
        } catch (SQLException e) {
            e.printStackTrace(); resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally { closeResources(rs, stmt, conn); }
    }

    private void updateRequest(int id, String counseler, String name, String phone, String nationality, String reqType, String title, String reqContent, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null; PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "UPDATE loan_dashboard SET counseler = ?, name = ?, phone_number = ?, nationality = ?, req_type = ?, title = ?, content = ?, updated_at = CURRENT_TIMESTAMP WHERE req_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, counseler);
            stmt.setString(2, name);
            stmt.setString(3, phone);
            stmt.setString(4, nationality);
            stmt.setString(5, reqType);
            stmt.setString(6, title);
            stmt.setString(7, reqContent);
            stmt.setInt(8, id);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                ObjectNode respJson = mapper.createObjectNode(); respJson.put("ok", true); respJson.put("message", "요청이 수정되었습니다");
                resp.setStatus(HttpServletResponse.SC_OK); resp.getWriter().write(mapper.writeValueAsString(respJson));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND); resp.getWriter().write("{\"ok\":false,\"error\":\"Not found\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace(); resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally { closeResources(null, stmt, conn); }
    }

    private void deleteRequest(int id, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null; PreparedStatement stmt = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "DELETE FROM loan_dashboard WHERE req_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                ObjectNode respJson = mapper.createObjectNode(); respJson.put("ok", true); respJson.put("message", "요청이 삭제되었습니다");
                resp.setStatus(HttpServletResponse.SC_OK); resp.getWriter().write(mapper.writeValueAsString(respJson));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND); resp.getWriter().write("{\"ok\":false,\"error\":\"Not found\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace(); resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally { closeResources(null, stmt, conn); }
    }

    private void closeResources(ResultSet rs, PreparedStatement stmt, Connection conn) {
        try { if (rs != null) rs.close(); if (stmt != null) stmt.close(); if (conn != null) conn.close(); } catch (SQLException e) { e.printStackTrace(); }
    }
}
