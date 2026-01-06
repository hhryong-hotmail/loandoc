package com.loandoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.io.InputStream;
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
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@WebServlet(urlPatterns = {"/api/server/loan-estimate"})
public class LoanEstimateServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(LoanEstimateServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        // Set request encoding to UTF-8 BEFORE reading any data
        req.setCharacterEncoding("UTF-8");
        
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");

        try {
            // Parse request body
            ObjectNode requestBody = mapper.readValue(req.getReader(), ObjectNode.class);

            // Short debug: write received request summary to a debug log file so we can inspect server-side failures
            try {
                String visaTypeDbg = requestBody.has("visaType") ? requestBody.get("visaType").asText() : "";
                String natDbg = requestBody.has("nationality") ? requestBody.get("nationality").asText() : "";
                String dbg = String.format("%s - REQ loginId=%s visaType=%s nationality=%s\n",
                        java.time.ZonedDateTime.now().toString(),
                        (requestBody.has("loginId") ? requestBody.get("loginId").asText() : ""),
                        visaTypeDbg, natDbg);
                writeDebugLog(dbg, null);
            } catch (Throwable _t) {
                // avoid failing request if debug logging fails
            }

            String loginId = requestBody.has("loginId") ? requestBody.get("loginId").asText() : null;
            String nationality = requestBody.has("nationality") ? requestBody.get("nationality").asText() : null;
            Integer remainMonths = requestBody.has("remainMonths") ? requestBody.get("remainMonths").asInt() : null;
            Double annualIncome = requestBody.has("annualIncome") ? requestBody.get("annualIncome").asDouble() : null;
            Integer age = requestBody.has("age") ? requestBody.get("age").asInt() : null;
            Integer workingMonths = requestBody.has("workingMonths") ? requestBody.get("workingMonths").asInt() : null;
            String visaType = requestBody.has("visaType") ? requestBody.get("visaType").asText() : null;
            String healthInsurance = requestBody.has("healthInsurance") ? requestBody.get("healthInsurance").asText() : null;
            boolean testMode = requestBody.has("testMode") && requestBody.get("testMode").asBoolean();

            // Validate required fields
            if (loginId == null || nationality == null || remainMonths == null
                    || annualIncome == null || age == null || workingMonths == null || visaType == null) {
                res.setStatus(400);
                ObjectNode error = mapper.createObjectNode();
                error.put("ok", false);
                error.put("error", "모든 필드가 필요합니다");
                res.getWriter().write(mapper.writeValueAsString(error));
                return;
            }

            // Normalize visa type
            String normalizedVisaType = normalizeVisaType(visaType);

            // Bank configurations - testMode에 따라 데이터베이스에서 가져오거나 하드코딩된 값 사용
            List<BankConfig> banks = getBankConfigurations(testMode);

            // Process each bank
            ArrayNode results = mapper.createArrayNode();
            for (BankConfig bank : banks) {
                ObjectNode result = processBank(bank, nationality, remainMonths, annualIncome, age, workingMonths, normalizedVisaType, healthInsurance, testMode);
                results.add(result);
            }

            // Sort by rank, then by comm (communication speed)
            // 금리와 대출금액의 순서대로 체크 후, 통신속도가 작은 것부터 1순위에 가깝게 정렬
            // 단, 음수(-) 값은 장애를 의미하므로 가장 뒤로 배치
            List<ObjectNode> sortedResults = new ArrayList<>();
            results.forEach(node -> sortedResults.add((ObjectNode) node));
            sortedResults.sort((a, b) -> {
                // 1순위: rank 비교 (금리와 대출금액이 반영된 순위)
                int rankA = a.has("rank") && !a.get("rank").isNull() ? a.get("rank").asInt() : 999;
                int rankB = b.has("rank") && !b.get("rank").isNull() ? b.get("rank").asInt() : 999;
                int rankCompare = Integer.compare(rankA, rankB);
                if (rankCompare != 0) {
                    return rankCompare;
                }
                
                // 2순위: comm 값 비교 (통신속도가 작은 것부터 1순위에 가깝게)
                // 음수(-) 값은 장애를 의미하므로 가장 큰 값으로 처리하여 뒤로 배치
                int commA = 999999; // 기본값 (장애 또는 null)
                int commB = 999999; // 기본값 (장애 또는 null)
                
                if (a.has("comm") && !a.get("comm").isNull()) {
                    commA = a.get("comm").asInt();
                    if (commA < 0) {
                        commA = 999999; // 음수는 장애로 처리하여 뒤로 배치
                    }
                }
                
                if (b.has("comm") && !b.get("comm").isNull()) {
                    commB = b.get("comm").asInt();
                    if (commB < 0) {
                        commB = 999999; // 음수는 장애로 처리하여 뒤로 배치
                    }
                }
                
                int commCompare = Integer.compare(commA, commB);
                if (commCompare != 0) {
                    return commCompare; // 작은 값이 앞으로 (빠른 통신이 우선)
                }
                
                // 3순위: 모든 값이 같으면 원래 순서 유지
                return 0;
            });

            ArrayNode finalResults = mapper.createArrayNode();
            sortedResults.forEach(finalResults::add);

            // Wrap in response object
            ObjectNode response = mapper.createObjectNode();
            response.set("banks", finalResults);

            res.getWriter().write(mapper.writeValueAsString(response));

        } catch (Exception e) {
            // print to console and also write full stacktrace to debug file
            e.printStackTrace();
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                writeDebugLog("EXCEPTION during doPost:\n", sw.toString());
            } catch (Throwable _t) {
                // ignore
            }
            res.setStatus(500);
            ObjectNode error = mapper.createObjectNode();
            error.put("ok", false);
            error.put("error", "server error");
            res.getWriter().write(mapper.writeValueAsString(error));
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        // If user opens the API URL in a browser (GET), redirect to the application page
        // to avoid showing a default 405 page. This keeps the API POST-only while
        // giving a friendly landing for manual browser visits.
        try {
            String context = req.getContextPath();
            // Redirect to the loan application page in the same webapp
            res.sendRedirect(context + "/loanAppl.html");
        } catch (Exception e) {
            // As a fallback, return a small JSON explaining usage
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.setStatus(405);
            ObjectNode err = mapper.createObjectNode();
            err.put("ok", false);
            err.put("error", "This endpoint accepts POST requests with JSON body. Use POST /api/server/loan-estimate");
            res.getWriter().write(mapper.writeValueAsString(err));
        }
    }

    private void writeDebugLog(String header, String stack) {
        try {
            Path p = Paths.get("D:/LoanDoc/server/deploy_debug.log");
            File parent = p.toFile().getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter fw = new FileWriter(p.toFile(), true); PrintWriter pw = new PrintWriter(fw)) {
                pw.println(header);
                if (stack != null) {
                    pw.println(stack);
                }
                pw.flush();
            }
        } catch (Throwable t) {
            // swallow to avoid affecting request handling
        }
    }

    private String normalizeVisaType(String visa) {
        if (visa == null) {
            return "";
        }
        // remove hyphens/spaces and normalize to compact form like E9, E7, F4, etc.
        String cleaned = visa.replaceAll("[-\\s]", "").toUpperCase();
        return cleaned;
    }

    private List<BankConfig> getBankConfigurations(boolean testMode) {
        // testMode와 운영 모드 모두 데이터베이스에서 가져옴
        return getBankConfigurationsFromDB(testMode);
        
        // 운영 모드: 기존 하드코딩된 로직 사용 (주석 처리 - DB에서 가져오도록 변경)
        /*
        List<BankConfig> banks = new ArrayList<>();
        // 1. KB저축은행: 가중치 35%, 최고한도 3000만원
        banks.add(new BankConfig("KB저축은행", 1,
            new String[]{"E7", "E9", "F2", "F6", "F5"},
                null, new String[]{"Nepal", "Cambodia"}, 19, null, 8, 3, 1500,
                2000.0, 14.7, 0.35, 3000.0, null));

        // 2. 전북은행: 가중치 36%, 최고한도 5000만원
        // 전북은행은 E9 비자에 대해 국가별 심사기준을 별도로 적용합니다.
        banks.add(new BankConfig("전북은행", null,
            new String[]{"E7", "E9", "F2", "F6", "F5", "F4"},
                null, null, 19, null, 6, 6, 2000,
                2000.0, 13.07, 0.36, 5000.0, null));

        // 3. OK저축은행: 가중치 37%, 최고한도 3500만원
        banks.add(new BankConfig("OK저축은행", null,
            new String[]{"E9"},
                null, null, 18, 45, 0, 0, 0,
                2000.0, 15.0, 0.37, 3500.0, null));

        // 4. 웰컴저축은행: 가중치 36%, 최고한도 3000만원
        banks.add(new BankConfig("웰컴저축은행", null,
            new String[]{"E9", "E7"},
                null, null, 0, null, 1, 0, 0,
                2000.0, 16.0, 0.36, 3000.0, null));

        // 5. 예가람저축은행: 가중치 38%, 최고한도 4000만원
        banks.add(new BankConfig("예가람저축은행", 5,
            new String[]{"E7", "E9", "F2", "F6", "F5"},
                null, null, 20, null, 0, 0, 0,
                null, null, 0.38, 4000.0, null));

        // Assign ranks to null-rank banks based on interest rate
        List<BankConfig> middleBanks = new ArrayList<>();
        for (BankConfig bank : banks) {
            if (bank.rank == null && bank.estimatedRate != null) {
                middleBanks.add(bank);
            }
        }
        middleBanks.sort((a, b) -> Double.compare(a.estimatedRate, b.estimatedRate));
        for (int i = 0; i < middleBanks.size(); i++) {
            middleBanks.get(i).rank = i + 2;
        }

        return banks;
        */
    }

    private List<BankConfig> getBankConfigurationsFromDB(boolean testMode) {
        List<BankConfig> banks = new ArrayList<>();
        String tableName = testMode ? "test_bank_info" : "bank_info";
        
        logger.log(Level.INFO, "Loading bank configurations from DB - testMode: " + testMode + ", tableName: " + tableName);
        
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
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                logger.log(Level.WARNING, "PostgreSQL JDBC driver not found on classpath", e);
            }
            
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
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

                // use_it=1인 데이터만 조회 (testMode일 때는 use_it 필터링 제외)
                String sql;
                if (hasUseItColumn && !testMode) {
                    // 운영 모드: use_it=1인 데이터만 조회
                    sql = "SELECT bank_name, bank_code, current_rate, max_limit, weight, comm FROM " + tableName + " WHERE use_it = 1 ORDER BY id";
                } else {
                    // testMode일 때는 모든 데이터 조회 (use_it 필터링 없음)
                    sql = "SELECT bank_name, bank_code, current_rate, max_limit, weight, comm FROM " + tableName + " ORDER BY id";
                }
                logger.log(Level.INFO, "SQL query: " + sql + " (hasUseItColumn: " + hasUseItColumn + ", testMode: " + testMode + ")");
                
                logger.log(Level.INFO, "Executing SQL: " + sql);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    try (ResultSet rs = ps.executeQuery()) {
                        int rank = 1;
                        int rowCount = 0;
                        while (rs.next()) {
                            rowCount++;
                            String bankName = rs.getString("bank_name");
                            Double currentRate = rs.getBigDecimal("current_rate") != null ? rs.getBigDecimal("current_rate").doubleValue() : null;
                            Long maxLimit = rs.getLong("max_limit");
                            Double weight = rs.getBigDecimal("weight") != null ? rs.getBigDecimal("weight").doubleValue() : 0.0;
                            Integer comm = rs.getObject("comm") != null ? rs.getInt("comm") : null;
                            
                            logger.log(Level.FINE, "Loaded bank: " + bankName + ", rate: " + currentRate + ", limit: " + maxLimit + ", weight: " + weight + ", comm: " + comm);
                            
                            // testMode일 때는 간단한 BankConfig 생성 (기본값 사용)
                            // 실제 검증 로직은 운영 모드에서만 사용
                            double weightFactor;
                            double maxLimitValue;
                            
                            if (testMode) {
                                // testMode일 때: weight를 100으로 나눔 (test_bank_info의 weight는 35.00, 37.00 등이므로 0.35, 0.37로 변환)
                                weightFactor = weight / 100.0;
                                // test_bank_info의 max_limit은 만원 단위이므로 그대로 사용
                                maxLimitValue = maxLimit.doubleValue();
                            } else {
                                // 운영 모드: bank_info의 max_limit도 만원 단위이므로 그대로 사용
                                weightFactor = weight / 100.0; // weight를 100으로 나눔
                                maxLimitValue = maxLimit.doubleValue(); // bank_info의 max_limit도 만원 단위
                            }
                            
                            banks.add(new BankConfig(
                                bankName,
                                rank++,
                                new String[]{"E7", "E9", "F2", "F6", "F5", "F4"}, // 기본 허용 비자
                                null, // excludedCountries
                                null, // requiredCountries
                                0, // minAge (검증 안 함)
                                null, // maxAge
                                0, // minVisaExpiryDays (검증 안 함)
                                0, // minEmploymentDays (검증 안 함)
                                0, // minAnnualIncome (검증 안 함)
                                2000.0, // estimatedLimit
                                currentRate, // estimatedRate
                                weightFactor, // weightFactor
                                maxLimitValue, // maxLimit
                                comm // comm
                            ));
                        }
                        logger.log(Level.INFO, "Loaded " + rowCount + " banks from " + tableName);
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Database query failed for bank configurations (testMode: " + testMode + ", table: " + tableName + "): " + ex.getMessage(), ex);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.log(Level.SEVERE, "Stack trace: " + sw.toString());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error while fetching bank configurations from DB (testMode: " + testMode + ", table: " + tableName + ")", ex);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.log(Level.SEVERE, "Stack trace: " + sw.toString());
        }

        // 데이터베이스에서 가져온 데이터가 없으면 빈 리스트 반환
        logger.log(Level.INFO, "Returning " + banks.size() + " banks from getBankConfigurationsFromDB (testMode: " + testMode + ")");
        return banks;
    }

    private ObjectNode processBank(BankConfig bank, String nationality, int remainMonths,
            double annualIncome, int age, int workingMonths, String normalizedVisaType, String healthInsurance, boolean testMode) {
        ObjectNode result = mapper.createObjectNode();
        result.put("bankName", bank.name);

        // Country validation - 먼저 국적 변수들을 정의해야 함
        ObjectNode countryNode = mapper.createObjectNode();
        // normalize nationality for server-side checks (영문으로만 비교)
        String natLower = nationality == null ? "" : nationality.trim().toLowerCase();
        boolean isChina = natLower.contains("china");
        boolean isIndia = natLower.contains("india");
        boolean isUzbek = natLower.contains("uzbek");
        boolean isKazakh = natLower.contains("kazakh");
        boolean isVietnam = natLower.contains("vietnam");
        boolean isPhilippines = natLower.contains("philippine");
        boolean isCambodia = natLower.contains("cambodia");
        boolean isNepal = natLower.contains("nepal");
        boolean isIndonesia = natLower.contains("indonesia");
        
        // Visa type validation with bank-specific rules
        ObjectNode visaTypeNode = mapper.createObjectNode();
        boolean visaTypeValid = false;
        
        // 은행별 비자 종류 및 국적 제한 적용
        if (bank.name.equals("예가람저축은행")) {
            // 예가람저축은행: E-7, E-9, F-2, F-6, F-5 만 대출승인
            visaTypeValid = normalizedVisaType.equals("E7") || normalizedVisaType.equals("E9") 
                    || normalizedVisaType.equals("F2") || normalizedVisaType.equals("F6") 
                    || normalizedVisaType.equals("F5");
        } else if (bank.name.equals("웰컴저축은행")) {
            // 웰컴저축은행: E-7, E-9 만 대출승인
            visaTypeValid = normalizedVisaType.equals("E7") || normalizedVisaType.equals("E9");
        } else if (bank.name.equals("OK저축은행")) {
            // OK저축은행: E-9 만 대출승인
            visaTypeValid = normalizedVisaType.equals("E9");
        } else if (bank.name.equals("KB저축은행")) {
            // KB저축은행: E-7, E-9, F-2, F-6, F-5 만 허용 (비자 종류만 확인, 국적은 country 노드에서 확인)
            visaTypeValid = normalizedVisaType.equals("E7") || normalizedVisaType.equals("E9") 
                    || normalizedVisaType.equals("F2") || normalizedVisaType.equals("F6") 
                    || normalizedVisaType.equals("F5");
        } else {
            // 다른 은행들은 기존 로직 사용
            visaTypeValid = containsString(bank.allowedVisaTypes, normalizedVisaType);
        }
        
        visaTypeNode.put("valid", visaTypeValid);
        visaTypeNode.put("error", visaTypeValid ? "" : "E비자종류");
        result.set("visaType", visaTypeNode);
        // Additional countries (F4 blacklist additions) - 영문으로만 비교
        boolean isMyanmar = natLower.contains("myanmar") || natLower.contains("burma");
        boolean isBangladesh = natLower.contains("bangladesh");
        boolean isThailand = natLower.contains("thailand");
        boolean isSriLanka = natLower.contains("sri lanka") || natLower.contains("srilanka");
        // E4 blacklist countries (Pakistan, Kyrgyzstan, Laos, East Timor) - 영문으로만 비교
        boolean isPakistan = natLower.contains("pakistan");
        boolean isKyrgyz = natLower.contains("kyrgyz") || natLower.contains("kyrgyzstan");
        boolean isLaos = natLower.contains("laos");
        boolean isTimor = natLower.contains("timor") || natLower.contains("east timor");
        // F4 special checks
        boolean isF4 = "F4".equals(normalizedVisaType);
        // F2/F5/F6 checks (for India rule)
        boolean isF2 = "F2".equals(normalizedVisaType);
        boolean isF5 = "F5".equals(normalizedVisaType);
        boolean isF6 = "F6".equals(normalizedVisaType);

        // initial computed country validity based on config lists
        boolean countryValidComputed = true;
        if (bank.excludedCountries != null && containsString(bank.excludedCountries, nationality)) {
            countryValidComputed = false;
        }
        if (bank.requiredCountries != null && !containsString(bank.requiredCountries, nationality)) {
            countryValidComputed = false;
        }

        // Apply bank-specific override rules
        boolean countryValid = countryValidComputed;
        String countryError = countryValid ? "" : "E국가";
        
        // KB저축은행: 국적이 nepal 또는 cambodia일 때만 허용
        if (bank.name != null && bank.name.equals("KB저축은행")) {
            countryValid = isNepal || isCambodia;
            countryError = countryValid ? "" : "E국가";
            try {
                writeDebugLog("KB_BANK_NATIONALITY_CHECK: bank=" + bank.name + " nat=" + natLower + " isNepal=" + isNepal + " isCambodia=" + isCambodia + " countryValid=" + countryValid, null);
            } catch (Throwable _t) {
                // ignore
            }
        }
        
        if (bank.name != null && bank.name.equals("전북은행")) {
            // 전북은행: E-9 + China/India -> 불가
            if ("E9".equals(normalizedVisaType) && (isChina || isIndia)) {
                countryValid = false;
                countryError = "E국가비자";
                try {
                    writeDebugLog("JEONBUK_OVERRIDE: bank=" + bank.name + " nat=" + natLower + " visa=" + normalizedVisaType, null);
                } catch (Throwable _t) {
                    // ignore
                }
            }
            // 전북은행: E-4 + 특정 국가들 -> 불가
            if ("E4".equals(normalizedVisaType) && (isPakistan || isKyrgyz || isLaos || isTimor)) {
                countryValid = false;
                countryError = "E국가비자";
                try {
                    writeDebugLog("JEONBUK_OVERRIDE_E4: bank=" + bank.name + " nat=" + natLower + " visa=" + normalizedVisaType, null);
                } catch (Throwable _t) {
                    // ignore
                }
            }
            // 전북은행: F-4 + India -> 불가 (요청사항)
            if (isF4 && isIndia) {
                countryValid = false;
                countryError = "E국가비자";
                try {
                    writeDebugLog("JEONBUK_OVERRIDE_F4: bank=" + bank.name + " nat=" + natLower + " visa=" + normalizedVisaType, null);
                } catch (Throwable _t) {
                    // ignore
                }
            }
            // 전북은행: F-4 + Vietnam/Philippines -> 불가 (추가 요청)
            if (isF4 && (isVietnam || isPhilippines)) {
                countryValid = false;
                countryError = "E국가비자";
                try {
                    writeDebugLog("JEONBUK_OVERRIDE_F4_VN_PH: bank=" + bank.name + " nat=" + natLower + " visa=" + normalizedVisaType, null);
                } catch (Throwable _t) {
                    // ignore
                }
            }
            // 전북은행: F-4 + Cambodia/Nepal/Indonesia -> 불가 (추가 요청)
            if (isF4 && (isCambodia || isNepal || isIndonesia)) {
                countryValid = false;
                countryError = "E국가비자";
                try {
                    writeDebugLog("JEONBUK_OVERRIDE_F4_CNI: bank=" + bank.name + " nat=" + natLower + " visa=" + normalizedVisaType, null);
                } catch (Throwable _t) {
                    // ignore
                }
            }
            // 전북은행: F-4 + (Myanmar, Bangladesh, Thailand, Sri Lanka, Pakistan, Kyrgyzstan, Laos, East Timor) -> 불가 (요청)
            if (isF4 && (isMyanmar || isBangladesh || isThailand || isSriLanka || isPakistan || isKyrgyz || isLaos || isTimor)) {
                countryValid = false;
                countryError = "E국가비자";
                try {
                    writeDebugLog("JEONBUK_OVERRIDE_F4_EXT: bank=" + bank.name + " nat=" + natLower + " visa=" + normalizedVisaType, null);
                } catch (Throwable _t) {
                    // ignore
                }
            }
            // 전북은행: F2/F5/F6 + India -> 불가 (요청)
            if ((isF2 || isF5 || isF6) && isIndia) {
                countryValid = false;
                countryError = "E국가비자";
                try {
                    writeDebugLog("JEONBUK_OVERRIDE_F_INDIA: bank=" + bank.name + " nat=" + natLower + " visa=" + normalizedVisaType, null);
                } catch (Throwable _t) {
                    // ignore
                }
            }
        }

        countryNode.put("valid", countryValid);
        countryNode.put("error", countryError);
        result.set("country", countryNode);

        // Expose country debug flags so frontend can display server logic details
        try {
            ObjectNode countryDebug = mapper.createObjectNode();
            countryDebug.put("natLower", natLower);
            countryDebug.put("countryValidComputed", countryValidComputed);
            countryDebug.put("isChina", isChina);
            countryDebug.put("isIndia", isIndia);
            countryDebug.put("isUzbek", isUzbek);
            countryDebug.put("isKazakh", isKazakh);
            countryDebug.put("isVietnam", isVietnam);
            countryDebug.put("isPhilippines", isPhilippines);
            countryDebug.put("isCambodia", isCambodia);
            countryDebug.put("isNepal", isNepal);
            countryDebug.put("isIndonesia", isIndonesia);
            countryDebug.put("isMyanmar", isMyanmar);
            countryDebug.put("isBangladesh", isBangladesh);
            countryDebug.put("isThailand", isThailand);
            countryDebug.put("isSriLanka", isSriLanka);
            countryDebug.put("isPakistan", isPakistan);
            countryDebug.put("isKyrgyz", isKyrgyz);
            countryDebug.put("isLaos", isLaos);
            countryDebug.put("isTimor", isTimor);
            countryDebug.put("isF4", isF4);
            countryDebug.put("isF2", isF2);
            countryDebug.put("isF5", isF5);
            countryDebug.put("isF6", isF6);
            result.set("countryDebug", countryDebug);
        } catch (Exception _e) {
            // do not fail the request if debug info cannot be attached
        }

        // Age validation
        ObjectNode ageNode = mapper.createObjectNode();
        boolean ageValid = age >= bank.minAge;
        if (bank.maxAge != null) {
            ageValid = ageValid && age <= bank.maxAge;
        }
        ageNode.put("valid", ageValid);
        ageNode.put("error", ageValid ? "" : "E나이");
        result.set("age", ageNode);

        // Visa expiry validation (잔여체류기간)
        ObjectNode visaExpiryNode = mapper.createObjectNode();
        boolean visaExpiryValid = remainMonths >= bank.minVisaExpiryDays;  // >= for inclusive comparison
        visaExpiryNode.put("valid", visaExpiryValid);
        visaExpiryNode.put("error", visaExpiryValid ? "" : "E비자만료");
        result.set("visaExpiry", visaExpiryNode);

        // Employment date validation
        ObjectNode employmentDateNode = mapper.createObjectNode();
        boolean employmentDateValid = workingMonths >= bank.minEmploymentDays;
        
        // 전북은행: E-9, E-7 비자만 재직기간 체크 (E-9: 1개월 이상, E-7: 1개월 이상)
        // 그 외 비자는 재직기간 체크 안 함
        if (bank.name.equals("전북은행")) {
            if (normalizedVisaType.equals("E9") || normalizedVisaType.equals("E7")) {
                employmentDateValid = workingMonths >= 1;
            } else {
                // E-9, E-7 이외의 비자는 재직기간 체크 안 함 (항상 통과)
                employmentDateValid = true;
            }            
        }
        
        employmentDateNode.put("valid", employmentDateValid);
        employmentDateNode.put("error", employmentDateValid ? "" : "E재직일자");
        result.set("employmentDate", employmentDateNode);

        // Annual income validation
        ObjectNode annualIncomeNode = mapper.createObjectNode();
        boolean annualIncomeValid = annualIncome >= bank.minAnnualIncome;
        
        // 전북은행: E-9 비자만 연소득 체크 (1500만원 이상)
        // 그 외 비자는 연소득 체크 안 함
        if (bank.name.equals("전북은행")) {
            if (normalizedVisaType.equals("E9")) {
                annualIncomeValid = annualIncome >= 1500;
            } else {
                // E-9 이외의 비자는 연소득 체크 안 함 (항상 통과)
                annualIncomeValid = true;
            }
        }
        
        annualIncomeNode.put("valid", annualIncomeValid);
        annualIncomeNode.put("error", annualIncomeValid ? "" : "E연소득");
        result.set("annualIncome", annualIncomeNode);

        // Health insurance validation for KB저축은행
        if (bank.name.equals("KB저축은행")) {
            ObjectNode healthInsuranceNode = mapper.createObjectNode();
            boolean healthInsuranceValid = healthInsurance != null && !healthInsurance.equals("지역");
            System.out.println("=== KB저축은행 의료보험 검증 ===");
            System.out.println("healthInsurance 값: " + healthInsurance);
            System.out.println("healthInsurance.equals(\"지역\"): " + (healthInsurance != null && healthInsurance.equals("지역")));
            System.out.println("healthInsuranceValid: " + healthInsuranceValid);
            healthInsuranceNode.put("valid", healthInsuranceValid);
            healthInsuranceNode.put("error", healthInsuranceValid ? "" : "E의료보험");
            result.set("healthInsurance", healthInsuranceNode);
        }

        // 예상한도 계산
        double calculatedLimit;
        double maxLimitValue;
        
        if (testMode) {
            // testMode일 때: 연소득 × 잔여체류개월수 × 가중치 (test_bank_info의 weight를 100으로 나눈 값) / 10
            // weightFactor는 이미 weight / 100.0으로 저장되어 있음
            calculatedLimit = (annualIncome * remainMonths * bank.weightFactor) / 10.0;
            // test_bank_info의 max_limit은 만원 단위이므로 그대로 사용
            maxLimitValue = bank.maxLimit;
            
            // 디버그 로그 추가
            logger.log(Level.INFO, String.format("[TEST MODE] 예상한도 계산 - 은행: %s, 연소득: %.0f, 잔여체류: %d, weightFactor: %.4f, calculatedLimit: %.2f, maxLimit: %.0f", 
                bank.name, annualIncome, remainMonths, bank.weightFactor, calculatedLimit, maxLimitValue));
        } else {
            // 운영 모드: 기존 로직 유지
            calculatedLimit = (annualIncome * remainMonths * bank.weightFactor) / 10.0;
            maxLimitValue = bank.maxLimit;
        }
        
        // 단, 최고한도를 초과할 수 없음
        double finalLimit = Math.min(calculatedLimit, maxLimitValue);
        
        // 디버그 로그 추가
        if (testMode) {
            logger.log(Level.INFO, String.format("[TEST MODE] 최종 예상한도 - 은행: %s, calculatedLimit: %.2f, maxLimit: %.0f, finalLimit: %.0f", 
                bank.name, calculatedLimit, maxLimitValue, finalLimit));
        }
        
        // 소수점 이하 반올림
        finalLimit = Math.round(finalLimit);
        
        result.put("estimatedLimit", finalLimit);
        
        if (bank.estimatedRate != null) {
            // 소수점 2자리까지 반올림
            double roundedRate = Math.round(bank.estimatedRate * 100.0) / 100.0;
            result.put("estimatedRate", roundedRate);
        } else {
            result.putNull("estimatedRate");
        }

        if (bank.rank != null) {
            result.put("rank", bank.rank);
        } else {
            result.putNull("rank");
        }

        // comm 값 추가
        if (bank.comm != null) {
            result.put("comm", bank.comm);
        } else {
            result.putNull("comm");
        }

        return result;
    }

    private boolean containsString(String[] array, String value) {
        if (array == null || value == null) {
            return false;
        }
        for (String s : array) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static class BankConfig {

        String name;
        Integer rank;
        String[] allowedVisaTypes;
        String[] excludedCountries;
        String[] requiredCountries;
        int minAge;
        Integer maxAge;  // null means no max age limit
        int minVisaExpiryDays;
        int minEmploymentDays;
        double minAnnualIncome;
        Double estimatedLimit;
        Double estimatedRate;
        double weightFactor;  // 가중치 (예: 0.35, 0.36, 0.37, 0.38)
        double maxLimit;      // 최고한도
        Integer comm;         // 통신 상태 값

        BankConfig(String name, Integer rank, String[] allowedVisaTypes,
                String[] excludedCountries, String[] requiredCountries,
                int minAge, Integer maxAge, int minVisaExpiryDays, int minEmploymentDays,
                double minAnnualIncome, Double estimatedLimit, Double estimatedRate,
                double weightFactor, double maxLimit, Integer comm) {
            this.name = name;
            this.rank = rank;
            this.allowedVisaTypes = allowedVisaTypes;
            this.excludedCountries = excludedCountries;
            this.requiredCountries = requiredCountries;
            this.minAge = minAge;
            this.maxAge = maxAge;
            this.minVisaExpiryDays = minVisaExpiryDays;
            this.minEmploymentDays = minEmploymentDays;
            this.minAnnualIncome = minAnnualIncome;
            this.estimatedLimit = estimatedLimit;
            this.estimatedRate = estimatedRate;
            this.weightFactor = weightFactor;
            this.maxLimit = maxLimit;
            this.comm = comm;
        }
    }
}
