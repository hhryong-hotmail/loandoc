package com.loandoc;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = { "/api/documents/content" })
public class DocumentContentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DocumentContentServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        String title = req.getParameter("title");
        if (title == null || title.trim().isEmpty()) {
            resp.setStatus(400);
            ObjectNode err = mapper.createObjectNode();
            err.put("ok", false);
            err.put("error", "title is required");
            resp.getWriter().print(mapper.writeValueAsString(err));
            return;
        }

        // DB connection lookup
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPass = System.getenv("DB_PASSWORD");
        String defaultDbName = "loandoc";

        try {
            String ctxUrl = getServletContext().getInitParameter("DB_URL");
            String ctxUser = getServletContext().getInitParameter("DB_USER");
            String ctxPass = getServletContext().getInitParameter("DB_PASSWORD");
            if (dbUrl == null || dbUrl.isEmpty()) dbUrl = ctxUrl;
            if (dbUser == null || dbUser.isEmpty()) dbUser = ctxUser;
            if (dbPass == null || dbPass.isEmpty()) dbPass = ctxPass;
        } catch (Exception e) {
            logger.log(Level.FINE, "No servlet context DB init params available", e);
        }

        Properties props = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
                if ((dbUrl == null || dbUrl.isEmpty()) && props.getProperty("DB_URL") != null) dbUrl = props.getProperty("DB_URL");
                if ((dbUser == null || dbUser.isEmpty()) && props.getProperty("DB_USER") != null) dbUser = props.getProperty("DB_USER");
                if ((dbPass == null || dbPass.isEmpty()) && props.getProperty("DB_PASSWORD") != null) dbPass = props.getProperty("DB_PASSWORD");
                logger.info("Loaded DB properties from classpath db.properties");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "No db.properties found on classpath or failed to load", e);
        }

        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:postgresql://localhost:5432/" + defaultDbName;
            logger.info("Using default DB URL for document content: " + dbUrl);
        } else if (!dbUrl.startsWith("jdbc:")) {
            dbUrl = "jdbc:postgresql://" + dbUrl;
            logger.info("Prepending jdbc: to DB URL: " + dbUrl);
        }
        if (dbUser == null || dbUser.isEmpty()) dbUser = "postgres";
        if (dbPass == null || dbPass.isEmpty()) dbPass = "postgresql";

        boolean dbWorked = false;
        ObjectNode out = mapper.createObjectNode();
        try {
            try { Class.forName("org.postgresql.Driver"); } catch (ClassNotFoundException e) { logger.log(Level.WARNING, "PostgreSQL JDBC driver not found", e); }
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                String sql = "SELECT content FROM documents WHERE title = ? LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, title);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            out.put("content", rs.getString("content") != null ? rs.getString("content") : "");
                        }
                        dbWorked = true;
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database query failed for document content: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while fetching document content", ex);
        }

        if (!dbWorked) {
            logger.info("Returning fallback document content for title: " + title);
            out.put("ok", false);
            out.put("content", "(약관 내용을 불러올 수 없습니다 - DB 연결 실패)");
            resp.setHeader("X-Data-Source", "fallback");
        } else {
            out.put("ok", true);
            resp.setHeader("X-Data-Source", "db");
        }

        resp.getWriter().print(mapper.writeValueAsString(out));
    }
}
