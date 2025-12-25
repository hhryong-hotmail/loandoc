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

@WebServlet(urlPatterns = { "/api/bank-info" })
public class BankInfoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(BankInfoServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        ArrayNode out = mapper.createArrayNode();

        // 운영/테스트 모드 파라미터 확인 (기본값: 운영 모드)
        String mode = req.getParameter("mode");
        boolean isTestMode = "test".equalsIgnoreCase(mode);
        String tableName = isTestMode ? "test_bank_info" : "bank_info";

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
            logger.info("Using default DB URL for bank info: " + dbUrl);
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
            logger.info("Attempting DB connection for bank info: " + dbUrl + " user=" + dbUser + " table=" + tableName);
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                // 테이블 존재 여부 확인
                String checkTableSql = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)";
                boolean tableExists = false;
                try (PreparedStatement checkPs = conn.prepareStatement(checkTableSql)) {
                    checkPs.setString(1, tableName);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next()) {
                            tableExists = rs.getBoolean(1);
                        }
                    }
                }

                if (tableExists) {
                    // use_it 컬럼 존재 여부 확인
                    String checkColumnSql = "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'use_it')";
                    boolean hasUseItColumn = false;
                    try (PreparedStatement checkColPs = conn.prepareStatement(checkColumnSql)) {
                        checkColPs.setString(1, tableName);
                        try (ResultSet rs = checkColPs.executeQuery()) {
                            if (rs.next()) {
                                hasUseItColumn = rs.getBoolean(1);
                            }
                        }
                    }

                    // use_it 컬럼이 있으면 WHERE 조건 추가, 없으면 모든 데이터 조회
                    String sql;
                    if (hasUseItColumn) {
                        sql = "SELECT bank_name, bank_code, current_rate, max_limit, weight, use_it FROM " + tableName + " WHERE use_it = 1 ORDER BY id";
                    } else {
                        sql = "SELECT bank_name, bank_code, current_rate, max_limit, weight FROM " + tableName + " ORDER BY id";
                    }
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                ObjectNode obj = mapper.createObjectNode();
                                obj.put("bank_name", rs.getString("bank_name"));
                                obj.put("bank_code", rs.getString("bank_code"));
                                obj.put("current_rate", rs.getBigDecimal("current_rate").doubleValue());
                                obj.put("max_limit", rs.getLong("max_limit"));
                                obj.put("weight", rs.getBigDecimal("weight").doubleValue());
                                if (hasUseItColumn) {
                                    obj.put("use_it", rs.getInt("use_it"));
                                } else {
                                    obj.put("use_it", 1); // 기본값으로 1 설정
                                }
                                out.add(obj);
                            }
                            dbWorked = true;
                        }
                    }
                } else {
                    logger.log(Level.WARNING, "Table " + tableName + " does not exist");
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Database query failed for bank info: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while fetching bank info", ex);
        }

        if (!dbWorked) {
            // Fallback sample data so frontend doesn't 404 or break
            logger.info("Returning fallback sample bank info (DB unavailable or table missing)");
            if (isTestMode) {
                ObjectNode a = mapper.createObjectNode();
                a.put("bank_name", "테스트은행1");
                a.put("bank_code", "T01");
                a.put("current_rate", 10.00);
                a.put("max_limit", 10000000);
                a.put("weight", 10.00);
                a.put("use_it", 1);
                out.add(a);
            } else {
                ObjectNode a = mapper.createObjectNode();
                a.put("bank_name", "전북은행");
                a.put("bank_code", "037");
                a.put("current_rate", 13.07);
                a.put("max_limit", 50000000);
                a.put("weight", 36.00);
                a.put("use_it", 1);
                out.add(a);
            }
            resp.setHeader("X-Data-Source", "fallback");
        } else {
            resp.setHeader("X-Data-Source", "db");
        }

        resp.getWriter().print(mapper.writeValueAsString(out));
    }
}

