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

@WebServlet(urlPatterns = {"/api/dashboard", "/api/dashboard/*"})
public class DashboardServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/loandoc";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        ObjectMapper mapper = new ObjectMapper();

        if (pathInfo != null && !pathInfo.equals("/")) {
            // GET /api/dashboard/{id} - Get single post
            String idStr = pathInfo.substring(1);
            try {
                int msgId = Integer.parseInt(idStr);
                getPost(msgId, resp, mapper);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
            }
        } else {
            // GET /api/dashboard?q=&msg_type= - List posts
            String query = req.getParameter("q");
            String msgType = req.getParameter("msg_type");
            listPosts(query, msgType, resp, mapper);
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

        String author = requestBody.has("author") ? requestBody.get("author").asText() : null;
        String password = requestBody.has("password") ? requestBody.get("password").asText() : null;
        String title = requestBody.has("title") ? requestBody.get("title").asText() : null;
        String msgType = requestBody.has("msg_type") ? requestBody.get("msg_type").asText() : "일반";
        String content = requestBody.has("content") ? requestBody.get("content").asText() : null;

        if (author == null || author.trim().isEmpty() || title == null || title.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Author and title are required\"}");
            return;
        }

        createPost(author, password, title, msgType, content, resp, mapper);
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
            int msgId = Integer.parseInt(pathInfo.substring(1));
            JsonNode requestBody = mapper.readTree(req.getReader());

            String title = requestBody.has("title") ? requestBody.get("title").asText() : null;
            String msgType = requestBody.has("msg_type") ? requestBody.get("msg_type").asText() : null;
            String content = requestBody.has("content") ? requestBody.get("content").asText() : null;
            String password = requestBody.has("password") ? requestBody.get("password").asText() : null;

            updatePost(msgId, title, msgType, content, password, resp, mapper);
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
            int msgId = Integer.parseInt(pathInfo.substring(1));
            deletePost(msgId, resp, mapper);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid ID\"}");
        }
    }

    private void listPosts(String query, String msgType, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            StringBuilder sql = new StringBuilder("SELECT msg_id, author, title, msg_type, created_date, views FROM dashboard_messages WHERE 1=1");
            List<String> params = new ArrayList<>();

            if (msgType != null && !msgType.trim().isEmpty() && !msgType.equals("전체")) {
                sql.append(" AND msg_type = ?");
                params.add(msgType);
            }

            if (query != null && !query.trim().isEmpty()) {
                sql.append(" AND (title ILIKE ? OR content ILIKE ? OR author ILIKE ?)");
                String searchPattern = "%" + query + "%";
                params.add(searchPattern);
                params.add(searchPattern);
                params.add(searchPattern);
            }

            sql.append(" ORDER BY created_date DESC");

            stmt = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                stmt.setString(i + 1, params.get(i));
            }

            rs = stmt.executeQuery();

            ArrayNode rows = mapper.createArrayNode();
            while (rs.next()) {
                ObjectNode row = mapper.createObjectNode();
                row.put("msg_id", rs.getInt("msg_id"));
                row.put("author", rs.getString("author"));
                row.put("title", rs.getString("title"));
                row.put("msg_type", rs.getString("msg_type"));
                row.put("created_at", rs.getDate("created_date").toString());
                row.put("updated_at", rs.getDate("created_date").toString());
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

    private void getPost(int msgId, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "SELECT msg_id, author, password, title, msg_type, content, created_date, views FROM dashboard_messages WHERE msg_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, msgId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                ObjectNode row = mapper.createObjectNode();
                row.put("msg_id", rs.getInt("msg_id"));
                row.put("author", rs.getString("author"));
                row.put("password", rs.getString("password"));
                row.put("title", rs.getString("title"));
                row.put("msg_type", rs.getString("msg_type"));
                row.put("content", rs.getString("content"));
                row.put("created_at", rs.getDate("created_date").toString());
                row.put("updated_at", rs.getDate("created_date").toString());

                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.set("row", row);

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Post not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(rs, stmt, conn);
        }
    }

    private void createPost(String author, String password, String title, String msgType, String content, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "INSERT INTO dashboard_messages (author, password, title, msg_type, content) VALUES (?, ?, ?, ?, ?) RETURNING msg_id";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, author);
            stmt.setString(2, password);
            stmt.setString(3, title);
            stmt.setString(4, msgType);
            stmt.setString(5, content);

            rs = stmt.executeQuery();
            if (rs.next()) {
                int newId = rs.getInt(1);
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("msg_id", newId);
                response.put("message", "글이 작성되었습니다");

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

    private void updatePost(int msgId, String title, String msgType, String content, String password, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "UPDATE dashboard_messages SET title = ?, msg_type = ?, content = ? WHERE msg_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, title);
            stmt.setString(2, msgType);
            stmt.setString(3, content);
            stmt.setInt(4, msgId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "글이 수정되었습니다");

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Post not found\"}");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Database error: " + e.getMessage() + "\"}");
        } finally {
            closeResources(null, stmt, conn);
        }
    }

    private void deletePost(int msgId, HttpServletResponse resp, ObjectMapper mapper) throws IOException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "DELETE FROM dashboard_messages WHERE msg_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, msgId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("message", "글이 삭제되었습니다");

                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"ok\":false,\"error\":\"Post not found\"}");
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
