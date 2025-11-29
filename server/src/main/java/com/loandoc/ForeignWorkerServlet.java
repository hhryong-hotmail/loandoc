package com.loandoc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = {"/api/foreign_worker_master/*"})
public class ForeignWorkerServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/loandoc";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    static {
        try { Class.forName("org.postgresql.Driver"); } catch (ClassNotFoundException e) { e.printStackTrace(); }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        String pathInfo = req.getPathInfo();
        String userId = null;
        if (pathInfo != null && pathInfo.length() > 1) {
            userId = pathInfo.substring(1);
        } else {
            userId = req.getParameter("user_id");
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode respJson = mapper.createObjectNode();

        if (userId == null || userId.trim().isEmpty()) {
            respJson.put("ok", false);
            respJson.put("error", "user_id required");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(mapper.writeValueAsString(respJson));
            return;
        }

        Connection conn = null; PreparedStatement stmt = null; ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            String sql = "SELECT name, phone_number, nationality FROM foreign_worker_master WHERE user_id = ? LIMIT 1";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, userId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                ObjectNode data = mapper.createObjectNode();
                data.put("name", rs.getString("name"));
                data.put("phone_number", rs.getString("phone_number"));
                data.put("nationality", rs.getString("nationality"));
                respJson.put("ok", true);
                respJson.put("found", true);
                respJson.set("data", data);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(respJson));
            } else {
                respJson.put("ok", true);
                respJson.put("found", false);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(respJson));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            respJson.put("ok", false);
            respJson.put("error", "Database error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(mapper.writeValueAsString(respJson));
        } finally {
            try { if (rs != null) rs.close(); if (stmt != null) stmt.close(); if (conn != null) conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
        }
    }
}
