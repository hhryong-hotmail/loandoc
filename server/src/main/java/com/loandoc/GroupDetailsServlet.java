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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = { "/api/server/group-details" })
public class GroupDetailsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(GroupDetailsServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        String groupName = req.getParameter("group_name");
        if (groupName == null || groupName.trim().isEmpty()) {
            resp.setStatus(400);
            ObjectNode err = mapper.createObjectNode();
            err.put("ok", false);
            err.put("error", "group_name is required");
            resp.getWriter().print(mapper.writeValueAsString(err));
            return;
        }

        // DB connection lookup (env, servlet context, classpath)
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
            logger.info("Using default DB URL for group details: " + dbUrl);
        } else if (!dbUrl.startsWith("jdbc:")) {
            dbUrl = "jdbc:postgresql://" + dbUrl;
            logger.info("Prepending jdbc: to DB URL: " + dbUrl);
        }
        if (dbUser == null || dbUser.isEmpty()) dbUser = "postgres";
        if (dbPass == null || dbPass.isEmpty()) dbPass = "postgres";

        ArrayNode out = mapper.createArrayNode();
        boolean dbWorked = false;
        try {
            try { Class.forName("org.postgresql.Driver"); } catch (ClassNotFoundException e) { logger.log(Level.WARNING, "PostgreSQL JDBC driver not found", e); }
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                String sql = "SELECT title FROM documents d WHERE d.group_name = ? ORDER BY title";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, groupName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode obj = mapper.createObjectNode();
                            obj.put("title", rs.getString("title") != null ? rs.getString("title") : "");
                            out.add(obj);
                        }
                        dbWorked = true;
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database query failed for group details: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while fetching group details", ex);
        }

        if (!dbWorked) {
            logger.info("Returning fallback group detail sample for: " + groupName);
            ObjectNode a = mapper.createObjectNode(); a.put("title", "이용약관"); out.add(a);
            ObjectNode b = mapper.createObjectNode(); b.put("title", "개인정보처리방침"); out.add(b);
            resp.setHeader("X-Data-Source", "fallback");
        } else {
            resp.setHeader("X-Data-Source", "db");
        }

        resp.getWriter().print(mapper.writeValueAsString(out));
    }
}
