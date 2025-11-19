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

@WebServlet(urlPatterns = {"/api/loan_consultant", "/api/loan_consultant/*"})
public class LoanConsultantServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/loandoc";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException cnfe) {
            System.err.println("PostgreSQL JDBC Driver not found: " + cnfe.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        ObjectMapper mapper = new ObjectMapper();

        if (pathInfo != null && !pathInfo.equals("/")) {
            // GET /api/loan_consultant/{id}
            String idStr = pathInfo.substring(1);
            try {
                int id = Integer.parseInt(idStr);
                getRequest(id, resp, mapper);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
            }
        } else {
            // GET list: q, req_type
            String q = req.getParameter("q");
            String reqType = req.getParameter("req_type");
            listRequests(q, reqType, resp, mapper);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode body;
        try {
            body = mapper.readTree(req.getReader());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid JSON\"}");
            return;
        }

        String reqLogin = body.has("req_login") ? body.get("req_login").asText() : null;
        String counseler = body.has("counseler") ? body.get("counseler").asText() : null;
        String name = body.has("name") ? body.get("name").asText() : null;
        String phone = body.has("phone_number") ? body.get("phone_number").asText() : null;
        String nationality = body.has("nationality") ? body.get("nationality").asText() : null;
        String reqType = body.has("req_type") ? body.get("req_type").asText() : null;
        String title = body.has("title") ? body.get("title").asText() : null;
        String content = body.has("req_content") ? body.get("req_content").asText() : null;

        if (reqLogin == null || reqLogin.trim().isEmpty() || title == null || title.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"req_login and title are required\"}");
            return;
        }

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
            String content = body.has("req_content") ? body.get("req_content").asText() : null;

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

        try {
            int id = Integer.parseInt(pathInfo.substring(1));
            deleteRequest(id, resp, new ObjectMapper());
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
        }
    }

    private void listRequests(String q, String reqType, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            StringBuilder sql = new StringBuilder("SELECT req_id, req_login, counseler, name, phone_number, nationality, req_type, title, req_content, updated_at FROM loan_consultant WHERE 1=1");
            List<String> params = new ArrayList<>();

            if (reqType != null && !reqType.trim().isEmpty() && !reqType.equals("전체")) {
                sql.append(" AND req_type = ?");
                params.add(reqType);
            }

            if (q != null && !q.trim().isEmpty()) {
                sql.append(" AND (title ILIKE ? OR req_content ILIKE ? OR req_login ILIKE ? OR name ILIKE ?)");
                String p = "%" + q + "%";
                params.add(p); params.add(p); params.add(p); params.add(p);
            }

            sql.append(" ORDER BY updated_at DESC");

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
                row.put("req_content", rs.getString("req_content"));
                row.put("updated_at", rs.getTimestamp("updated_at").toString());
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
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "SELECT req_id, req_login, counseler, name, phone_number, nationality, req_type, title, req_content, updated_at FROM loan_consultant WHERE req_id = ?";
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
                row.put("req_content", rs.getString("req_content"));
                row.put("updated_at", rs.getTimestamp("updated_at").toString());

                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.set("row", row);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Request not found\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(rs, stmt, conn);
        }
    }

    private void createRequest(String reqLogin, String counseler, String name, String phone, String nationality, String reqType, String title, String content, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            // Resolve name, nationality and phone_number from foreign_worker_master by user_id (reqLogin)
            if (reqLogin != null && ((name == null || name.trim().isEmpty()) || (nationality == null || nationality.trim().isEmpty()) || (phone == null || phone.trim().isEmpty()))) {
                PreparedStatement fwStmt = null;
                ResultSet fwRs = null;
                try {
                    String fwSql = "SELECT name, nationality, phone_number FROM foreign_worker_master WHERE user_id = ?";
                    fwStmt = conn.prepareStatement(fwSql);
                    fwStmt.setString(1, reqLogin);
                    fwRs = fwStmt.executeQuery();
                    if (fwRs.next()) {
                        if (name == null || name.trim().isEmpty()) name = fwRs.getString("name");
                        if (nationality == null || nationality.trim().isEmpty()) nationality = fwRs.getString("nationality");
                        if (phone == null || phone.trim().isEmpty()) phone = fwRs.getString("phone_number");
                    }
                } catch (SQLException ex) {
                    // ignore and continue with provided values
                } finally {
                    if (fwRs != null) try { fwRs.close(); } catch (SQLException ignore) {}
                    if (fwStmt != null) try { fwStmt.close(); } catch (SQLException ignore) {}
                }
            }
            String sql = "INSERT INTO loan_consultant (req_login, counseler, name, phone_number, nationality, req_type, title, req_content) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING req_id";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, reqLogin);
            stmt.setString(2, counseler);
            stmt.setString(3, name);
            stmt.setString(4, phone);
            stmt.setString(5, nationality);
            stmt.setString(6, reqType);
            stmt.setString(7, title);
            stmt.setString(8, content);

            rs = stmt.executeQuery();
            if (rs.next()) {
                int newId = rs.getInt(1);
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("req_id", newId);
                response.put("message", "요청이 등록되었습니다");
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(rs, stmt, conn);
        }
    }

    private void updateRequest(int id, String counseler, String name, String phone, String nationality, String reqType, String title, String content, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "UPDATE loan_consultant SET counseler = COALESCE(?, counseler), name = COALESCE(?, name), phone_number = COALESCE(?, phone_number), nationality = COALESCE(?, nationality), req_type = COALESCE(?, req_type), title = COALESCE(?, title), req_content = COALESCE(?, req_content), updated_at = CURRENT_TIMESTAMP WHERE req_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, counseler);
            stmt.setString(2, name);
            stmt.setString(3, phone);
            stmt.setString(4, nationality);
            stmt.setString(5, reqType);
            stmt.setString(6, title);
            stmt.setString(7, content);
            stmt.setInt(8, id);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "요청이 수정되었습니다");
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Request not found\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(null, stmt, conn);
        }
    }

    private void deleteRequest(int id, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "DELETE FROM loan_consultant WHERE req_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, id);

            int rows = stmt.executeUpdate();
            if (rows > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "요청이 삭제되었습니다");
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Request not found\"}");
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
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
