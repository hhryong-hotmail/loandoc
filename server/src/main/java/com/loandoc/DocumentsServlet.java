package com.loandoc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = { "/api/documents" })
public class DocumentsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DocumentsServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        
        // Get group_name parameter
        String groupName = req.getParameter("group_name");
        
        ObjectNode response = mapper.createObjectNode();
        ArrayNode documents = mapper.createArrayNode();

        // DB connection settings
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPass = System.getenv("DB_PASSWORD");

        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:postgresql://localhost:5432/loandoc";
        } else if (!dbUrl.startsWith("jdbc:")) {
            dbUrl = "jdbc:postgresql://" + dbUrl;
        }
        
        if (dbUser == null || dbUser.isEmpty()) dbUser = "postgres";
        if (dbPass == null || dbPass.isEmpty()) dbPass = "postgres";

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);

            String sql;
            if (groupName != null && !groupName.trim().isEmpty()) {
                // Filter by group_name and title containing '개인정보'
                sql = "SELECT id, title, group_name, content " +
                      "FROM documents " +
                      "WHERE group_name = ? AND title LIKE '%개인정보%' " +
                      "ORDER BY id";
                stmt = conn.prepareStatement(sql);
                stmt.setString(1, groupName);
            } else {
                // Get all documents
                sql = "SELECT id, title, group_name, content " +
                      "FROM documents " +
                      "ORDER BY id";
                stmt = conn.prepareStatement(sql);
            }

            rs = stmt.executeQuery();

            while (rs.next()) {
                ObjectNode doc = mapper.createObjectNode();
                doc.put("id", rs.getInt("id"));
                doc.put("title", rs.getString("title"));
                doc.put("group_name", rs.getString("group_name"));
                doc.put("content", rs.getString("content"));
                documents.add(doc);
            }

            response.put("ok", true);
            response.set("documents", documents);
            response.put("count", documents.size());

        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "PostgreSQL JDBC Driver not found", e);
            response.put("ok", false);
            response.put("error", "Database driver not found");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error", e);
            response.put("ok", false);
            response.put("error", "Database error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing database resources", e);
            }
        }

        resp.getWriter().print(mapper.writeValueAsString(response));
    }
}
