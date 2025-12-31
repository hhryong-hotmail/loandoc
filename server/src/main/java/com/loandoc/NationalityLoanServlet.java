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

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=utf-8");
        resp.setCharacterEncoding("UTF-8");
        ObjectNode result = mapper.createObjectNode();
        
        try {
            // 요청 본문 읽기
            StringBuilder sb = new StringBuilder();
            String line;
            try (java.io.BufferedReader reader = req.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            ObjectNode requestData = (ObjectNode) mapper.readTree(sb.toString());
            
            // 운영/테스트 모드 파라미터 확인
            String mode = req.getParameter("mode");
            boolean isTestMode = "test".equalsIgnoreCase(mode);
            String tableName = isTestMode ? "test_nationality_loan" : "nationality_loan";
            
            // DB 연결 설정
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
                logger.log(Level.FINE, "No db.properties found on classpath or failed to load", e);
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
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                logger.log(Level.WARNING, "PostgreSQL JDBC driver not found on classpath", e);
            }
            
            // 연결 속성 설정
            java.util.Properties connProps = new java.util.Properties();
            connProps.setProperty("user", dbUser);
            connProps.setProperty("password", dbPass);
            connProps.setProperty("prepareThreshold", "0");
            try (Connection conn = DriverManager.getConnection(dbUrl, connProps)) {
                // PostgreSQL 클라이언트 인코딩을 UTF-8로 설정
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET client_encoding TO 'UTF8'");
                } catch (SQLException e) {
                    logger.log(Level.FINE, "Failed to set client_encoding, continuing anyway", e);
                }
                
                // UPDATE 쿼리 구성
                if (!requestData.has("id") || requestData.get("id").isNull()) {
                    result.put("ok", false);
                    result.put("error", "id 필드가 필요합니다");
                    mapper.writeValue(resp.getWriter(), result);
                    return;
                }
                
                int id = requestData.get("id").asInt();
                
                // UPDATE 쿼리 동적 구성
                StringBuilder updateSql = new StringBuilder("UPDATE " + tableName + " SET ");
                java.util.List<String> setParts = new java.util.ArrayList<>();
                java.util.List<Object> params = new java.util.ArrayList<>();
                
                if (requestData.has("bank_name")) {
                    setParts.add("bank_name = ?");
                    params.add(requestData.get("bank_name").asText());
                }
                if (requestData.has("product_name")) {
                    setParts.add("product_name = ?");
                    params.add(requestData.get("product_name").isNull() ? null : requestData.get("product_name").asText());
                }
                if (requestData.has("eligible_visa")) {
                    setParts.add("eligible_visa = ?");
                    params.add(requestData.get("eligible_visa").isNull() ? null : requestData.get("eligible_visa").asText());
                }
                if (requestData.has("eligible_country")) {
                    setParts.add("eligible_country = ?");
                    params.add(requestData.get("eligible_country").isNull() ? null : requestData.get("eligible_country").asText());
                }
                if (requestData.has("loan_limit_min")) {
                    setParts.add("loan_limit_min = ?");
                    params.add(requestData.get("loan_limit_min").isNull() ? null : requestData.get("loan_limit_min").asLong());
                }
                if (requestData.has("loan_limit_max")) {
                    setParts.add("loan_limit_max = ?");
                    params.add(requestData.get("loan_limit_max").isNull() ? null : requestData.get("loan_limit_max").asLong());
                }
                if (requestData.has("loan_period_min")) {
                    setParts.add("loan_period_min = ?");
                    params.add(requestData.get("loan_period_min").isNull() ? null : requestData.get("loan_period_min").asInt());
                }
                if (requestData.has("loan_period_max")) {
                    setParts.add("loan_period_max = ?");
                    params.add(requestData.get("loan_period_max").isNull() ? null : requestData.get("loan_period_max").asInt());
                }
                if (requestData.has("interest_rate_min")) {
                    setParts.add("interest_rate_min = ?");
                    params.add(requestData.get("interest_rate_min").isNull() ? null : requestData.get("interest_rate_min").asDouble());
                }
                if (requestData.has("interest_rate_max")) {
                    setParts.add("interest_rate_max = ?");
                    params.add(requestData.get("interest_rate_max").isNull() ? null : requestData.get("interest_rate_max").asDouble());
                }
                if (requestData.has("repayment_method")) {
                    setParts.add("repayment_method = ?");
                    params.add(requestData.get("repayment_method").isNull() ? null : requestData.get("repayment_method").asText());
                }
                if (requestData.has("credit_rating")) {
                    setParts.add("credit_rating = ?");
                    params.add(requestData.get("credit_rating").isNull() ? null : requestData.get("credit_rating").asText());
                }
                if (requestData.has("age_min")) {
                    setParts.add("age_min = ?");
                    params.add(requestData.get("age_min").isNull() ? null : requestData.get("age_min").asInt());
                }
                if (requestData.has("age_max")) {
                    setParts.add("age_max = ?");
                    params.add(requestData.get("age_max").isNull() ? null : requestData.get("age_max").asInt());
                }
                if (requestData.has("remaining_stay_period_min")) {
                    setParts.add("remaining_stay_period_min = ?");
                    params.add(requestData.get("remaining_stay_period_min").isNull() ? null : requestData.get("remaining_stay_period_min").asInt());
                }
                if (requestData.has("employment_period_min")) {
                    setParts.add("employment_period_min = ?");
                    params.add(requestData.get("employment_period_min").isNull() ? null : requestData.get("employment_period_min").asInt());
                }
                if (requestData.has("annual_income_min")) {
                    setParts.add("annual_income_min = ?");
                    params.add(requestData.get("annual_income_min").isNull() ? null : requestData.get("annual_income_min").asLong());
                }
                if (requestData.has("health_insurance")) {
                    setParts.add("health_insurance = ?");
                    params.add(requestData.get("health_insurance").isNull() ? null : requestData.get("health_insurance").asText());
                }
                if (requestData.has("required_documents")) {
                    setParts.add("required_documents = ?");
                    params.add(requestData.get("required_documents").isNull() ? null : requestData.get("required_documents").asText());
                }
                
                if (setParts.isEmpty()) {
                    result.put("ok", false);
                    result.put("error", "업데이트할 필드가 없습니다");
                    mapper.writeValue(resp.getWriter(), result);
                    return;
                }
                
                updateSql.append(String.join(", ", setParts));
                updateSql.append(" WHERE id = ?");
                params.add(id);
                
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql.toString())) {
                    int paramIndex = 1;
                    for (Object param : params) {
                        if (param == null) {
                            updatePs.setNull(paramIndex, java.sql.Types.NULL);
                        } else if (param instanceof String) {
                            updatePs.setString(paramIndex, (String) param);
                        } else if (param instanceof Integer) {
                            updatePs.setInt(paramIndex, (Integer) param);
                        } else if (param instanceof Long) {
                            updatePs.setLong(paramIndex, (Long) param);
                        } else if (param instanceof Double) {
                            updatePs.setDouble(paramIndex, (Double) param);
                        }
                        paramIndex++;
                    }
                    
                    int rowsAffected = updatePs.executeUpdate();
                    if (rowsAffected > 0) {
                        result.put("ok", true);
                        result.put("message", "업데이트 성공");
                    } else {
                        result.put("ok", false);
                        result.put("error", "업데이트할 행을 찾을 수 없습니다");
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Database update failed for nationality_loan (mode: " + req.getParameter("mode") + "): " + ex.getMessage(), ex);
            result.put("ok", false);
            result.put("error", "데이터베이스 오류: " + ex.getMessage());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while updating nationality_loan data", ex);
            result.put("ok", false);
            result.put("error", "예상치 못한 오류: " + ex.getMessage());
        }
        
        mapper.writeValue(resp.getWriter(), result);
    }
}

