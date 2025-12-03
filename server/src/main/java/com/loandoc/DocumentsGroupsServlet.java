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
import java.io.InputStream;
import java.util.Properties;

@WebServlet(urlPatterns = { "/api/documents/groups" })
public class DocumentsGroupsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DocumentsGroupsServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        ArrayNode out = mapper.createArrayNode();

        // Read DB connection settings from multiple sources (env, servlet context init params, classpath db.properties)
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPass = System.getenv("DB_PASSWORD");
        String defaultDbName = "loandoc";

        // 1) Servlet context init parameters (web.xml context-param or similar)
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

        // 2) classpath properties file (WEB-INF/classes/db.properties)
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

        // Defaults if still missing
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:postgresql://localhost:5432/" + defaultDbName;
            logger.info("Using default DB URL for documents groups: " + dbUrl);
        } else if (!dbUrl.startsWith("jdbc:")) {
            dbUrl = "jdbc:postgresql://" + dbUrl;
            logger.info("Prepending jdbc: to DB URL: " + dbUrl);
        }
        if (dbUser == null || dbUser.isEmpty()) dbUser = "postgres";
        if (dbPass == null || dbPass.isEmpty()) dbPass = "postgres";

        boolean dbWorked = false;
        try {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                logger.log(Level.WARNING, "PostgreSQL JDBC driver not found on classpath", e);
            }
            logger.info("Attempting DB connection for documents groups: " + dbUrl + " user=" + dbUser);
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                String sql = "SELECT DISTINCT ON (group_name) group_name, select_option FROM documents WHERE group_name LIKE '%론닥%' ORDER BY group_name, group_number";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String groupName = rs.getString("group_name");
                            String selectOption = rs.getString("select_option");
                            ObjectNode obj = mapper.createObjectNode();
                            obj.put("group_name", groupName != null ? groupName : "");
                            obj.put("select_option", selectOption != null ? selectOption : "");
                            out.add(obj);
                        }
                        dbWorked = true;
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database query failed for documents groups: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while fetching documents groups", ex);
        }

        if (!dbWorked) {
            // Fallback sample data so frontend doesn't 404 or break. Replace with real DB when available.
            logger.info("Returning fallback sample documents groups (DB unavailable)");
            ObjectNode a = mapper.createObjectNode(); a.put("group_name", "이용약관"); a.put("select_option", "A"); out.add(a);
            ObjectNode b = mapper.createObjectNode(); b.put("group_name", "개인정보처리방침"); b.put("select_option", "B"); out.add(b);
            ObjectNode c = mapper.createObjectNode(); c.put("group_name", "마케팅 수신 동의"); c.put("select_option", "C"); out.add(c);
            ObjectNode d = mapper.createObjectNode(); d.put("group_name", "개인(신용)정보 조회 동의"); d.put("select_option", "D"); out.add(d);
            ObjectNode e = mapper.createObjectNode(); e.put("group_name", "개인(신용)정보 제공 동의"); e.put("select_option", "E"); out.add(e);
            ObjectNode f = mapper.createObjectNode(); f.put("group_name", "고유식별정보 처리 동의"); f.put("select_option", "F"); out.add(f);
            ObjectNode g = mapper.createObjectNode(); g.put("group_name", "상품설명서 및 약관 교부 확인"); g.put("select_option", "G"); out.add(g);
            resp.setHeader("X-Data-Source", "fallback");
        } else {
            resp.setHeader("X-Data-Source", "db");
        }

        resp.getWriter().print(mapper.writeValueAsString(out));
    }
}
