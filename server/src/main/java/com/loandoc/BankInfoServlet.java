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

@WebServlet(urlPatterns = { "/api/bank-info" })
public class BankInfoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(BankInfoServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    
    public BankInfoServlet() {
        // ObjectMapper가 UTF-8을 사용하도록 설정
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }
    
    /**
     * 잘못된 인코딩으로 저장된 문자열을 올바른 UTF-8로 변환
     * 이미 올바른 UTF-8인 경우는 변환하지 않음
     */
    private String fixEncoding(String str) {
        if (str == null || str.isEmpty()) return str;
        
        // "????" 패턴 감지 (유효하지 않은 바이트 시퀀스)
        if (str.contains("?") && str.matches(".*\\?{2,}.*")) {
            logger.log(Level.WARNING, "Detected '?' characters, possible encoding issue: " + str);
        }
        
        // 이미 올바른 한글이 있는지 확인
        boolean hasValidKorean = false;
        boolean hasValidAscii = false;
        for (char c : str.toCharArray()) {
            // 한글 유니코드 범위: AC00-D7AF (가-힣)
            if (c >= 0xAC00 && c <= 0xD7AF) {
                hasValidKorean = true;
                break;
            }
            // ASCII 문자 (영어, 숫자 등)
            if (c >= 32 && c <= 126) {
                hasValidAscii = true;
            }
        }
        
        // 이미 올바른 한글이 있으면 변환하지 않음 (예: "웰컴저축은행")
        if (hasValidKorean) {
            return str;
        }
        
        // ASCII만 있고 한글이 없는 경우, 잘못된 인코딩일 가능성 체크
        // 하지만 영어만 있는 경우는 변환하지 않음
        if (hasValidAscii && !hasValidKorean) {
            // 영어와 숫자, 공백만 있는 경우는 정상일 수 있음
            boolean onlyAscii = true;
            for (char c : str.toCharArray()) {
                if (c < 32 || c > 126) {
                    onlyAscii = false;
                    break;
                }
            }
            if (onlyAscii) {
                return str; // 순수 ASCII 문자열은 변환하지 않음
            }
        }
        
        // 잘못된 인코딩 패턴 감지 (예: "Ã¬Â Â", "Ã«Â¶Â", "Ã¬Â" 등)
        // ISO-8859-1로 잘못 해석된 UTF-8 바이트의 특징적인 패턴
        boolean hasInvalidPattern = false;
        for (char c : str.toCharArray()) {
            // 잘못된 인코딩의 특징: 0xC3, 0xC2 등으로 시작하는 바이트가 문자로 해석됨
            // Ã = 0xC3, Â = 0xC2, ì = 0xEC, ¬ = 0xAC 등
            if (c == 0xC3 || c == 0xC2 || c == 0xEC || c == 0xAC) {
                hasInvalidPattern = true;
                break;
            }
        }
        
        // 잘못된 인코딩 패턴이 있거나 한글이 예상되는데 없는 경우 변환 시도
        if (hasInvalidPattern || (!hasValidKorean && str.length() > 0)) {
            try {
                // 여러 인코딩으로 변환 시도
                String[] encodings = {"ISO-8859-1", "Windows-1252", "EUC-KR"};
                for (String encoding : encodings) {
                    try {
                        // 현재 문자열을 해당 인코딩의 바이트로 변환 후 UTF-8로 재해석
                        byte[] bytes = str.getBytes(encoding);
                        String converted = new String(bytes, "UTF-8");
                        
                        // 변환 후 한글이 정상적으로 나타나는지 확인
                        boolean hasKoreanAfterConversion = false;
                        boolean hasQuestionMark = false;
                        for (char c : converted.toCharArray()) {
                            if (c >= 0xAC00 && c <= 0xD7AF) {
                                hasKoreanAfterConversion = true;
                            }
                            if (c == '?') {
                                hasQuestionMark = true;
                            }
                        }
                        
                        // 변환 후 한글이 나타나고 "?"가 없으면 변환된 결과 사용
                        if (hasKoreanAfterConversion && !hasQuestionMark) {
                            logger.log(Level.INFO, "Fixed encoding using " + encoding + ": " + str + " -> " + converted);
                            return converted;
                        }
                    } catch (Exception e) {
                        // 다음 인코딩 시도
                    }
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "Encoding fix failed for: " + str, e);
            }
        }
        
        // 변환하지 않거나 변환 실패 시 원본 반환
        return str;
    }
    
    /**
     * ResultSet에서 문자열을 읽고 인코딩 변환 시도
     * 모든 bank_name에 대해 변환 시도
     */
    private String fixEncodingFromBytes(ResultSet rs, String columnName) throws SQLException {
        try {
            // 먼저 일반적인 방법으로 읽기
            String str = rs.getString(columnName);
            if (str == null) return null;
            
            // bank_name이고 id가 35인 경우 "예가람저축은행"으로 매핑
            if (columnName.equals("bank_name")) {
                try {
                    int id = rs.getInt("id");
                    if (id == 35) {
                        String rawName = str;
                        // "????" 패턴이 있으면 매핑
                        if (rawName.contains("?") && rawName.matches(".*\\?{2,}.*")) {
                            logger.log(Level.INFO, "Mapping id=35 bank_name from '" + rawName + "' to '예가람저축은행'");
                            return "예가람저축은행";
                        }
                    }
                } catch (SQLException e) {
                    // id를 읽을 수 없으면 계속 진행
                }
            }
            
            // 모든 문자열에 대해 인코딩 변환 시도
            String fixed = fixEncoding(str);
            
            // 변환 후 결과가 더 나은지 확인 (한글이 나타나는지)
            boolean originalHasKorean = false;
            boolean fixedHasKorean = false;
            for (char c : str.toCharArray()) {
                if (c >= 0xAC00 && c <= 0xD7AF) {
                    originalHasKorean = true;
                    break;
                }
            }
            for (char c : fixed.toCharArray()) {
                if (c >= 0xAC00 && c <= 0xD7AF) {
                    fixedHasKorean = true;
                    break;
                }
            }
            
            // 변환된 결과가 한글을 포함하고 원본이 한글을 포함하지 않으면 변환된 결과 사용
            if (fixedHasKorean && !originalHasKorean && !fixed.equals(str)) {
                logger.log(Level.INFO, "Encoding fixed: " + str + " -> " + fixed);
                return fixed;
            }
            
            // 원본이 이미 한글을 포함하거나 변환이 실패한 경우 원본 반환
            return str;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error in fixEncodingFromBytes for " + columnName + ": " + e.getMessage(), e);
            try {
                return rs.getString(columnName);
            } catch (SQLException ex) {
                return null;
            }
        }
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
        
        // JDBC URL에 인코딩 관련 파라미터는 추가하지 않음
        // PostgreSQL JDBC 드라이버는 데이터베이스 인코딩을 자동으로 처리
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
            // 연결 속성 설정
            java.util.Properties connProps = new java.util.Properties();
            connProps.setProperty("user", dbUser);
            connProps.setProperty("password", dbPass);
            // PostgreSQL 연결 속성에 인코딩 명시
            connProps.setProperty("prepareThreshold", "0"); // PreparedStatement 최적화 비활성화 (인코딩 문제 방지)
            try (Connection conn = DriverManager.getConnection(dbUrl, connProps)) {
                // PostgreSQL 클라이언트 인코딩을 UTF-8로 설정
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET client_encoding TO 'UTF8'");
                    // 데이터베이스 인코딩 확인
                    try (java.sql.ResultSet rs = stmt.executeQuery("SHOW server_encoding")) {
                        if (rs.next()) {
                            logger.log(Level.INFO, "Database server encoding: " + rs.getString(1));
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.FINE, "Failed to set client_encoding, continuing anyway", e);
                }
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

                    // comm 컬럼 존재 여부 확인
                    String checkCommColumnSql = "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'comm')";
                    boolean hasCommColumn = false;
                    try (PreparedStatement checkCommPs = conn.prepareStatement(checkCommColumnSql)) {
                        checkCommPs.setString(1, tableName);
                        try (ResultSet rs = checkCommPs.executeQuery()) {
                            if (rs.next()) {
                                hasCommColumn = rs.getBoolean(1);
                            }
                        }
                    }

                    // all 파라미터 확인 (모든 데이터 조회 여부)
                    String allParam = req.getParameter("all");
                    boolean showAll = "true".equalsIgnoreCase(allParam);

                    // SQL 쿼리 구성
                    String sql;
                    String selectColumns = "id, bank_name, bank_code, current_rate, max_limit, weight";
                    if (hasUseItColumn) {
                        selectColumns += ", use_it";
                    }
                    if (hasCommColumn) {
                        selectColumns += ", comm";
                    }
                    
                    if (showAll) {
                        // all=true인 경우 모든 데이터 조회 (use_it 조건 없이)
                        sql = "SELECT " + selectColumns + " FROM " + tableName + " ORDER BY id";
                    } else {
                        // 기본: use_it=1인 데이터만 조회
                        if (hasUseItColumn) {
                            sql = "SELECT " + selectColumns + " FROM " + tableName + " WHERE use_it = 1 ORDER BY id";
                        } else {
                            sql = "SELECT " + selectColumns + " FROM " + tableName + " ORDER BY id";
                        }
                    }
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                ObjectNode obj = mapper.createObjectNode();
                                obj.put("id", rs.getInt("id"));
                                // 한글 인코딩 처리 - fixEncodingFromBytes가 내부에서 fixEncoding도 호출
                                String bankName = fixEncodingFromBytes(rs, "bank_name");
                                String bankCode = fixEncoding(rs.getString("bank_code"));
                                obj.put("bank_name", bankName);
                                obj.put("bank_code", bankCode);
                                obj.put("current_rate", rs.getBigDecimal("current_rate").doubleValue());
                                obj.put("max_limit", rs.getLong("max_limit"));
                                obj.put("weight", rs.getBigDecimal("weight").doubleValue());
                                if (hasUseItColumn) {
                                    obj.put("use_it", rs.getInt("use_it"));
                                } else {
                                    obj.put("use_it", 1); // 기본값으로 1 설정
                                }
                                if (hasCommColumn) {
                                    Integer comm = rs.getObject("comm") != null ? rs.getInt("comm") : null;
                                    obj.put("comm", comm != null ? comm : 0);
                                } else {
                                    obj.put("comm", 0);
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
            String tableName = isTestMode ? "test_bank_info" : "bank_info";
            
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
            
            // JDBC URL에 인코딩 관련 파라미터는 추가하지 않음
            // PostgreSQL JDBC 드라이버는 데이터베이스 인코딩을 자동으로 처리
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
            connProps.setProperty("prepareThreshold", "0"); // PreparedStatement 최적화 비활성화 (인코딩 문제 방지)
            try (Connection conn = DriverManager.getConnection(dbUrl, connProps)) {
                // PostgreSQL 클라이언트 인코딩을 UTF-8로 설정
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET client_encoding TO 'UTF8'");
                    // 데이터베이스 인코딩 확인
                    try (java.sql.ResultSet rs = stmt.executeQuery("SHOW server_encoding")) {
                        if (rs.next()) {
                            logger.log(Level.INFO, "Database server encoding: " + rs.getString(1));
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.FINE, "Failed to set client_encoding, continuing anyway", e);
                }
                
                // 컬럼 존재 여부 확인
                String checkUseItColumnSql = "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'use_it')";
                boolean hasUseItColumn = false;
                try (PreparedStatement checkColPs = conn.prepareStatement(checkUseItColumnSql)) {
                    checkColPs.setString(1, tableName);
                    try (ResultSet rs = checkColPs.executeQuery()) {
                        if (rs.next()) {
                            hasUseItColumn = rs.getBoolean(1);
                        }
                    }
                }
                
                String checkCommColumnSql = "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'comm')";
                boolean hasCommColumn = false;
                try (PreparedStatement checkCommPs = conn.prepareStatement(checkCommColumnSql)) {
                    checkCommPs.setString(1, tableName);
                    try (ResultSet rs = checkCommPs.executeQuery()) {
                        if (rs.next()) {
                            hasCommColumn = rs.getBoolean(1);
                        }
                    }
                }
                
                // UPDATE 쿼리 구성
                if (!requestData.has("id") || requestData.get("id").isNull()) {
                    result.put("ok", false);
                    result.put("error", "id 필드가 필요합니다");
                    mapper.writeValue(resp.getWriter(), result);
                    return;
                }
                
                int id = requestData.get("id").asInt();
                
                // 먼저 기존 데이터 조회 (필드가 누락된 경우를 대비)
                String selectSql = "SELECT bank_name, bank_code, current_rate, max_limit, weight";
                if (hasUseItColumn) {
                    selectSql += ", use_it";
                }
                if (hasCommColumn) {
                    selectSql += ", comm";
                }
                selectSql += " FROM " + tableName + " WHERE id = ?";
                
                String bankName = null;
                String bankCode = null;
                Double currentRate = null;
                Long maxLimit = null;
                Double weight = null;
                Integer useIt = null;
                Integer comm = null;
                
                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setInt(1, id);
                    try (ResultSet rs = selectPs.executeQuery()) {
                        if (rs.next()) {
                            bankName = fixEncodingFromBytes(rs, "bank_name");
                            bankCode = fixEncoding(rs.getString("bank_code"));
                            currentRate = rs.getBigDecimal("current_rate") != null ? rs.getBigDecimal("current_rate").doubleValue() : null;
                            maxLimit = rs.getLong("max_limit");
                            weight = rs.getBigDecimal("weight") != null ? rs.getBigDecimal("weight").doubleValue() : null;
                            if (hasUseItColumn) {
                                useIt = rs.getInt("use_it");
                            }
                            if (hasCommColumn) {
                                comm = rs.getObject("comm") != null ? rs.getInt("comm") : null;
                            }
                        } else {
                            result.put("ok", false);
                            result.put("error", "해당 ID의 데이터를 찾을 수 없습니다: " + id);
                            mapper.writeValue(resp.getWriter(), result);
                            return;
                        }
                    }
                }
                
                // 요청 데이터로 덮어쓰기 (요청에 포함된 필드만)
                if (requestData.has("bank_name") && !requestData.get("bank_name").isNull()) {
                    bankName = requestData.get("bank_name").asText();
                }
                if (requestData.has("bank_code") && !requestData.get("bank_code").isNull()) {
                    bankCode = requestData.get("bank_code").asText();
                }
                if (requestData.has("current_rate") && !requestData.get("current_rate").isNull()) {
                    currentRate = requestData.get("current_rate").asDouble();
                }
                if (requestData.has("max_limit") && !requestData.get("max_limit").isNull()) {
                    maxLimit = requestData.get("max_limit").asLong();
                }
                if (requestData.has("weight") && !requestData.get("weight").isNull()) {
                    weight = requestData.get("weight").asDouble();
                }
                if (hasUseItColumn && requestData.has("use_it") && !requestData.get("use_it").isNull()) {
                    useIt = requestData.get("use_it").asInt();
                }
                if (hasCommColumn && requestData.has("comm") && !requestData.get("comm").isNull()) {
                    comm = requestData.get("comm").asInt();
                }
                
                StringBuilder updateSql = new StringBuilder("UPDATE " + tableName + " SET ");
                updateSql.append("bank_name = ?, bank_code = ?, current_rate = ?, max_limit = ?, weight = ?");
                
                if (hasUseItColumn) {
                    updateSql.append(", use_it = ?");
                }
                if (hasCommColumn) {
                    updateSql.append(", comm = ?");
                }
                
                updateSql.append(" WHERE id = ?");
                
                try (PreparedStatement ps = conn.prepareStatement(updateSql.toString())) {
                    int paramIndex = 1;
                    ps.setString(paramIndex++, bankName);
                    ps.setString(paramIndex++, bankCode);
                    ps.setBigDecimal(paramIndex++, currentRate != null ? java.math.BigDecimal.valueOf(currentRate) : null);
                    ps.setLong(paramIndex++, maxLimit != null ? maxLimit : 0);
                    ps.setBigDecimal(paramIndex++, weight != null ? java.math.BigDecimal.valueOf(weight) : null);
                    
                    if (hasUseItColumn) {
                        ps.setInt(paramIndex++, useIt != null ? useIt : 0);
                    }
                    if (hasCommColumn) {
                        ps.setObject(paramIndex++, comm);
                    }
                    
                    ps.setInt(paramIndex++, id);
                    
                    int rowsUpdated = ps.executeUpdate();
                    if (rowsUpdated > 0) {
                        result.put("ok", true);
                        result.put("message", "업데이트 성공");
                    } else {
                        result.put("ok", false);
                        result.put("error", "업데이트된 행이 없습니다");
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Database update failed: " + ex.getMessage(), ex);
                result.put("ok", false);
                result.put("error", ex.getMessage());
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while updating bank info", ex);
            result.put("ok", false);
            result.put("error", ex.getMessage());
        }
        
        mapper.writeValue(resp.getWriter(), result);
    }
}

