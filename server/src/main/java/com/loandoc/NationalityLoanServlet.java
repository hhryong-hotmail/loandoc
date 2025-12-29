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
import com.fasterxml.jackson.databind.SerializationFeature;

@WebServlet(urlPatterns = { "/api/nationality-loan" })
public class NationalityLoanServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(NationalityLoanServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    
    public NationalityLoanServlet() {
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=utf-8");
        resp.setCharacterEncoding("UTF-8");
        ArrayNode out = mapper.createArrayNode();

        // 운영/테스트 모드 파라미터 확인 (기본값: 운영 모드)
        String mode = req.getParameter("mode");
        boolean isTestMode = "test".equalsIgnoreCase(mode);
        String tableName = isTestMode ? "test_nationality_loan" : "nationality_loan";

        // Read DB connection settings
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPass = System.getenv("DB_PASSWORD");
        String defaultDbName = "loandoc";

        // Servlet context init parameters
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

        // classpath properties file
        Properties props = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
                if ((dbUrl == null || dbUrl.isEmpty()) && props.getProperty("DB_URL") != null) dbUrl = props.getProperty("DB_URL");
                if ((dbUser == null || dbUser.isEmpty()) && props.getProperty("DB_USER") != null) dbUser = props.getProperty("DB_USER");
                if ((dbPass == null || dbPass.isEmpty()) && props.getProperty("DB_PASSWORD") != null) dbPass = props.getProperty("DB_PASSWORD");
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "No db.properties found on classpath", e);
        }

        // Defaults
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:postgresql://localhost:5432/" + defaultDbName;
        } else if (!dbUrl.startsWith("jdbc:")) {
            dbUrl = "jdbc:postgresql://" + dbUrl;
        }
        if (dbUser == null || dbUser.isEmpty()) dbUser = "postgres";
        if (dbPass == null || dbPass.isEmpty()) dbPass = "postgres";

        try {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                logger.log(Level.WARNING, "PostgreSQL JDBC driver not found", e);
            }
            
            java.util.Properties connProps = new java.util.Properties();
            connProps.setProperty("user", dbUser);
            connProps.setProperty("password", dbPass);
            connProps.setProperty("prepareThreshold", "0");
            
            try (Connection conn = DriverManager.getConnection(dbUrl, connProps)) {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET client_encoding TO 'UTF8'");
                } catch (SQLException e) {
                    logger.log(Level.FINE, "Failed to set client_encoding", e);
                }
                
                String sql = "SELECT id, bank_name, product_name, eligible_visa, eligible_country, " +
                           "loan_limit_min, loan_limit_max, loan_period_min, loan_period_max, " +
                           "interest_rate_min, interest_rate_max, repayment_method, credit_rating, " +
                           "age_min, age_max, remaining_stay_period_min, employment_period_min, " +
                           "annual_income_min, health_insurance, required_documents " +
                           "FROM " + tableName + " ORDER BY id";
                
                logger.log(Level.INFO, "Executing SQL: " + sql);
                
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            ObjectNode row = mapper.createObjectNode();
                            row.put("id", rs.getInt("id"));
                            row.put("bank_name", rs.getString("bank_name"));
                            row.put("product_name", rs.getString("product_name"));
                            row.put("eligible_visa", rs.getString("eligible_visa"));
                            row.put("eligible_country", rs.getString("eligible_country"));
                            
                            Long loanLimitMin = rs.getObject("loan_limit_min") != null ? rs.getLong("loan_limit_min") : null;
                            if (loanLimitMin != null) {
                                row.put("loan_limit_min", loanLimitMin);
                            } else {
                                row.putNull("loan_limit_min");
                            }
                            
                            Long loanLimitMax = rs.getObject("loan_limit_max") != null ? rs.getLong("loan_limit_max") : null;
                            if (loanLimitMax != null) {
                                row.put("loan_limit_max", loanLimitMax);
                            } else {
                                row.putNull("loan_limit_max");
                            }
                            
                            Integer loanPeriodMin = rs.getObject("loan_period_min") != null ? rs.getInt("loan_period_min") : null;
                            if (loanPeriodMin != null) {
                                row.put("loan_period_min", loanPeriodMin);
                            } else {
                                row.putNull("loan_period_min");
                            }
                            
                            Integer loanPeriodMax = rs.getObject("loan_period_max") != null ? rs.getInt("loan_period_max") : null;
                            if (loanPeriodMax != null) {
                                row.put("loan_period_max", loanPeriodMax);
                            } else {
                                row.putNull("loan_period_max");
                            }
                            
                            java.math.BigDecimal interestRateMin = rs.getBigDecimal("interest_rate_min");
                            if (interestRateMin != null) {
                                row.put("interest_rate_min", interestRateMin.doubleValue());
                            } else {
                                row.putNull("interest_rate_min");
                            }
                            
                            java.math.BigDecimal interestRateMax = rs.getBigDecimal("interest_rate_max");
                            if (interestRateMax != null) {
                                row.put("interest_rate_max", interestRateMax.doubleValue());
                            } else {
                                row.putNull("interest_rate_max");
                            }
                            
                            row.put("repayment_method", rs.getString("repayment_method"));
                            row.put("credit_rating", rs.getString("credit_rating"));
                            
                            Integer ageMin = rs.getObject("age_min") != null ? rs.getInt("age_min") : null;
                            if (ageMin != null) {
                                row.put("age_min", ageMin);
                            } else {
                                row.putNull("age_min");
                            }
                            
                            Integer ageMax = rs.getObject("age_max") != null ? rs.getInt("age_max") : null;
                            if (ageMax != null) {
                                row.put("age_max", ageMax);
                            } else {
                                row.putNull("age_max");
                            }
                            
                            Integer remainingStayPeriodMin = rs.getObject("remaining_stay_period_min") != null ? rs.getInt("remaining_stay_period_min") : null;
                            if (remainingStayPeriodMin != null) {
                                row.put("remaining_stay_period_min", remainingStayPeriodMin);
                            } else {
                                row.putNull("remaining_stay_period_min");
                            }
                            
                            Integer employmentPeriodMin = rs.getObject("employment_period_min") != null ? rs.getInt("employment_period_min") : null;
                            if (employmentPeriodMin != null) {
                                row.put("employment_period_min", employmentPeriodMin);
                            } else {
                                row.putNull("employment_period_min");
                            }
                            
                            Long annualIncomeMin = rs.getObject("annual_income_min") != null ? rs.getLong("annual_income_min") : null;
                            if (annualIncomeMin != null) {
                                row.put("annual_income_min", annualIncomeMin);
                            } else {
                                row.putNull("annual_income_min");
                            }
                            
                            row.put("health_insurance", rs.getString("health_insurance"));
                            row.put("required_documents", rs.getString("required_documents"));
                            
                            out.add(row);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Database query failed for nationality_loan (mode: " + mode + ", table: " + tableName + "): " + ex.getMessage(), ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while fetching nationality_loan data (mode: " + mode + ", table: " + tableName + ")", ex);
        }

        mapper.writeValue(resp.getWriter(), out);
    }
}

