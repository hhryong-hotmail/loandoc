package com.loandoc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = { "/api/register" })
public class RegisterServlet extends HttpServlet {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path usersFile;
    private static final Logger logger = Logger.getLogger(RegisterServlet.class.getName());

    public RegisterServlet() {
        // store in webapp working directory
        usersFile = Paths.get(System.getProperty("catalina.base", "."), "webapps", "server", "users.json");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long requestStart = System.currentTimeMillis();
        logger.info("=== REGISTER REQUEST START ===");
        logger.info("Request from: " + req.getRemoteAddr() + ":" + req.getRemotePort());
        logger.info("Request method: " + req.getMethod() + ", URI: " + req.getRequestURI() + ", Query: " + req.getQueryString());
        logger.info("Remote host: " + req.getRemoteHost() + ", Remote user: " + req.getRemoteUser());
        logger.info("Content length: " + req.getContentLengthLong());
        logger.info("User-Agent: " + req.getHeader("User-Agent") + ", Referer: " + req.getHeader("Referer"));
        logger.info("Request headers: " + java.util.Collections.list(req.getHeaderNames()).toString());

        resp.setContentType("application/json; charset=utf-8");
        ObjectNode result = mapper.createObjectNode();
        try (InputStream is = req.getInputStream()) {
            // Read raw request body for debugging
            byte[] rawBody = is.readAllBytes();
            String rawBodyStr = new String(rawBody, StandardCharsets.UTF_8);
            // Avoid logging raw passwords - log raw body for debugging but ensure it's clear
            logger.info("Raw request body (trimmed to 200 chars): " + (rawBodyStr.length() > 200 ? rawBodyStr.substring(0,200) + "..." : rawBodyStr));

            @SuppressWarnings("unchecked")
            Map<String, String> body = mapper.readValue(rawBody, Map.class);
            String id = (body.get("id") != null) ? body.get("id").trim() : null;
            String password = (body.get("password") != null) ? body.get("password") : null;

        logger.info("Parsed ID: '" + id + "' (length: " + (id != null ? id.length() : "null") + ")");
        logger.info("Parsed password: '" + (password != null ? "[HIDDEN, length: " + password.length() + "]" : "null") + "'");

            if (id == null || id.isEmpty()) {
                logger.warning("Validation failed: ID is null or empty");
                result.put("ok", false);
                result.put("error", "아이디를 입력하세요.");
                resp.setStatus(400);
                resp.getWriter().print(mapper.writeValueAsString(result));
                logger.info("=== REGISTER REQUEST END (400 - ID validation failed) ===");
                return;
            }
            if (password == null || password.length() < 8) {
                logger.warning("Validation failed: Password too short (length: "
                        + (password != null ? password.length() : "null") + ")");
                result.put("ok", false);
                result.put("error", "비밀번호는 최소 8자 이상이어야 합니다.");
                resp.setStatus(400);
                resp.getWriter().print(mapper.writeValueAsString(result));
                logger.info("=== REGISTER REQUEST END (400 - Password validation failed) ===");
                return;
            }

            // Read DB connection settings from env (optional)
            String dbUrl = System.getenv("DB_URL");
            String dbUser = System.getenv("DB_USER");
            String dbPass = System.getenv("DB_PASSWORD");
            String defaultDbName = "loandoc";

            logger.info("Environment variables - DB_URL: " + (dbUrl != null ? dbUrl : "null") +
                    ", DB_USER: " + (dbUser != null ? dbUser : "null") +
                    ", DB_PASSWORD: " + (dbPass != null ? "[SET]" : "null"));

            if (dbUrl == null || dbUrl.isEmpty()) {
                dbUrl = "jdbc:postgresql://localhost:5432/" + defaultDbName;
                logger.info("Using default DB URL: " + dbUrl);
            } else if (!dbUrl.startsWith("jdbc:")) {
                dbUrl = "jdbc:postgresql://" + dbUrl;
                logger.info("Prepending jdbc: to DB URL: " + dbUrl);
            }
            // ensure DB name present in jdbc url; if missing, append default DB name
            try {
                int idx = dbUrl.indexOf('/', "jdbc:postgresql://".length());
                if (idx == -1) {
                    if (!dbUrl.endsWith("/"))
                        dbUrl = dbUrl + "/" + defaultDbName;
                    else
                        dbUrl = dbUrl + defaultDbName;
                    logger.info("Appended default DB name, final URL: " + dbUrl);
                }
            } catch (Exception e) {
                logger.warning("Error processing DB URL: " + e.getMessage());
                // ignore, keep provided dbUrl
            }

            if (dbUser == null) {
                dbUser = "postgres";
                logger.info("Using default DB user: " + dbUser);
            }
            if (dbPass == null) {
                dbPass = "postgresql";
                logger.info("Using default DB password");
            }

            boolean dbAvailable = false;

            logger.info("Register attempt for user='" + id + "' using DB URL='" + dbUrl + "' user='" + dbUser + "'");

            // Attempt INSERT-first into DB to avoid race conditions; on DB connection
            // failure fall back to file-based storage
            try {
                logger.info("Attempting to load PostgreSQL JDBC driver...");
                // Ensure JDBC driver is loaded in environments where ServiceLoader registration
                // may not occur
                try {
                    Class.forName("org.postgresql.Driver");
                    logger.info("PostgreSQL JDBC Driver loaded successfully");
                } catch (ClassNotFoundException cnfe) {
                    logger.log(Level.WARNING, "PostgreSQL JDBC Driver class not found on classpath", cnfe);
                }

                logger.info("Attempting database connection to: " + dbUrl);
                long connStart = System.currentTimeMillis();
                try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                    long connElapsed = System.currentTimeMillis() - connStart;
                    dbAvailable = true;
                    logger.info("Database connection established successfully (ms=" + connElapsed + ")");

                    int bcryptCost = 12;
                    long hashStart = System.currentTimeMillis();
                    String hashed = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(bcryptCost,
                            password.toCharArray());
                    long hashElapsed = System.currentTimeMillis() - hashStart;
                    logger.info("Password hashed successfully (cost=" + bcryptCost + ", ms=" + hashElapsed + ")");

                    // Insert into a simple auth table to avoid NOT NULL constraints on the main worker table
                    String insertSql = "INSERT INTO user_account (user_id, password) VALUES (?, ?)";
                    logger.info("Preparing SQL statement: " + insertSql);

                    try (PreparedStatement ips = conn.prepareStatement(insertSql)) {
                        ips.setString(1, id);
                        ips.setString(2, hashed);
                        // log parameter summary (mask password hash except prefix)
                        String hashPreview = (hashed != null && hashed.length() > 10) ? hashed.substring(0, 10) + "..." : "[masked]";
                        logger.info("Executing INSERT with params: user_id='" + id + "', password_hash_preview='" + hashPreview + "'");

                        long execStart = System.currentTimeMillis();
                        int updated = ips.executeUpdate();
                        long execElapsed = System.currentTimeMillis() - execStart;
                        logger.info("DB insert executed, update count=" + updated + " for user='" + id + "' (ms=" + execElapsed + ")");

                        if (updated > 0) {
                            result.put("ok", true);
                            resp.setStatus(201);
                            logger.info("User registration successful in database");
                        } else {
                            // unexpected: no rows affected
                            logger.log(Level.WARNING, "DB insert returned 0 rows affected for user='" + id + "'");
                            result.put("ok", false);
                            result.put("error", "서버 내부 오류(데이터베이스 업데이트 실패)");
                            resp.setStatus(500);
                        }
                        resp.getWriter().print(mapper.writeValueAsString(result));
                        logger.info("=== REGISTER REQUEST END (DB success/failure) ===");
                        return;
                    } catch (SQLException e) {
                        String sqlState = e.getSQLState();
                        logger.log(Level.WARNING, "SQLException during DB insert for user='" + id + "'", e);
                        logger.warning("SQL State: " + sqlState + ", Error Code: " + e.getErrorCode() + ", Message: "
                                + e.getMessage());

                        // Only treat PostgreSQL unique-violation (23505) as '409 Conflict'.
                        // Other SQL state codes in the '23' class indicate other constraint
                        // violations (e.g. NOT NULL = 23502). Those should not be reported
                        // as "이미 존재하는 ID". Return 500 and surface a clear message so
                        // the issue can be investigated.
                        if ("23505".equals(sqlState)) {
                            logger.warning("Duplicate key violation detected for user='" + id + "'");
                            result.put("ok", false);
                            result.put("error", "이미 존재하는 ID입니다.");
                            resp.setStatus(409);
                            resp.getWriter().print(mapper.writeValueAsString(result));
                            logger.info("=== REGISTER REQUEST END (409 - Duplicate ID) ===");
                            return;
                        }

                        if (sqlState != null && sqlState.startsWith("23")) {
                            // Other integrity constraint violation (not unique) — treat as server
                            // side data/schema problem. Return 500 and include brief hint.
                            logger.warning("Integrity constraint violation (non-unique) for user='" + id + "'");
                            result.put("ok", false);
                            result.put("error", "서버 내부 오류(데이터 무결성 위반): " + e.getMessage());
                            resp.setStatus(500);
                            resp.getWriter().print(mapper.writeValueAsString(result));
                            logger.info("=== REGISTER REQUEST END (500 - Integrity constraint) ===");
                            return;
                        }

                        // other DB errors -> return 500
                        result.put("ok", false);
                        result.put("error", "서버 내부 오류(데이터베이스): " + e.getMessage());
                        resp.setStatus(500);
                        resp.getWriter().print(mapper.writeValueAsString(result));
                        logger.info("=== REGISTER REQUEST END (500 - DB error) ===");
                        return;
                    }
                } catch (SQLException sqe) {
                    // DB not available - will fall back to file-based storage
                    dbAvailable = false;
                    logger.log(Level.INFO, "DB unavailable (will consider fallback). Reason: " + sqe.getMessage(), sqe);
                    logger.info("SQL State: " + sqe.getSQLState() + ", Error Code: " + sqe.getErrorCode());
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during database connection attempt", e);
                dbAvailable = false;
            }

            if (!dbAvailable) {
                logger.info("Database not available, checking file fallback option...");
                // Decide whether to use file-based fallback. Default: enabled for backward
                // compatibility.
                String fallbackEnv = System.getenv("ENABLE_FILE_FALLBACK");
                boolean useFileFallback = (fallbackEnv == null) ? true : fallbackEnv.equalsIgnoreCase("true");
                logger.info("ENABLE_FILE_FALLBACK env var: " + fallbackEnv + ", useFileFallback: " + useFileFallback);

                if (!useFileFallback) {
                    // If fallback is disabled, return 503 Service Unavailable to force DB
                    // availability
                    logger.warning("File fallback disabled and DB unavailable — returning 503");
                    result.put("ok", false);
                    result.put("error", "데이터베이스에 연결할 수 없습니다. 관리자에게 문의하세요.");
                    resp.setStatus(503);
                    resp.getWriter().print(mapper.writeValueAsString(result));
                    logger.info("=== REGISTER REQUEST END (503 - DB unavailable, fallback disabled) ===");
                    return;
                }

                logger.info("Using file-based fallback storage");
                // fallback: file-based check (legacy behavior)
                List<Map<String, String>> users = new ArrayList<>();
                try {
                    logger.info("Checking for existing users file: " + usersFile.toString());
                    if (Files.exists(usersFile)) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> loadedUsers = mapper.readValue(usersFile.toFile(), List.class);
                        users = loadedUsers;
                        logger.info("Loaded " + users.size() + " existing users from file");
                    } else {
                        logger.info("Users file does not exist, creating directory structure");
                        Files.createDirectories(usersFile.getParent());
                    }
                    } catch (Exception e) {
                    logger.log(Level.WARNING, "Error reading users file: " + e.getMessage(), e);
                    // ignore, start fresh
                }

                boolean exists = false;
                for (Map<String, String> u : users) {
                    if (id.equals(u.get("user_id")) || id.equals(u.get("id"))) {
                        exists = true;
                        logger.info("Found existing user in file storage: " + id);
                        break;
                    }
                }

                if (!exists) {
                    logger.info("User does not exist in file storage, creating new user");
                    int bcryptCost = 12;
                    long hashStartFile = System.currentTimeMillis();
                    String hashed = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults().hashToString(bcryptCost,
                            password.toCharArray());
                    long hashElapsedFile = System.currentTimeMillis() - hashStartFile;
                    logger.info("Password hashed for file fallback (cost=" + bcryptCost + ", ms=" + hashElapsedFile + ")");

                    ObjectNode newUser = mapper.createObjectNode();
                    newUser.put("user_id", id);
                    newUser.put("password", hashed);
                    @SuppressWarnings("unchecked")
                    Map<String, String> userMap = mapper.convertValue(newUser, Map.class);
                    users.add(userMap);

                    Path tmp = Files.createTempFile(usersFile.getParent(), "users", ".tmp");
                    logger.info("Writing to temporary file: " + tmp.toString());
                    long writeStart = System.currentTimeMillis();
                    try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                        mapper.writeValue(w, users);
                    }
                    long writeElapsed = System.currentTimeMillis() - writeStart;
                    logger.info("Temporary users file written (ms=" + writeElapsed + ")");
                    try {
                        Files.move(tmp, usersFile);
                        result.put("ok", true);
                        resp.setStatus(201);
                        logger.info("User successfully saved to file storage: " + usersFile.toString());
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to move temp users file into place", e);
                        result.put("ok", false);
                        result.put("error", "서버 내부 오류(파일 저장 실패)");
                        resp.setStatus(500);
                    }
                } else {
                    logger.warning("User already exists in file storage: " + id);
                    result.put("ok", false);
                    result.put("error", "이미 존재하는 ID입니다.");
                    resp.setStatus(409);
                }
                resp.getWriter().print(mapper.writeValueAsString(result));
                logger.info("=== REGISTER REQUEST END (File fallback) ===");
                return;
            }
            resp.getWriter().print(mapper.writeValueAsString(result));
            logger.info("=== REGISTER REQUEST END (Unexpected path) ===");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error in register handler", ex);
            result.put("ok", false);
            result.put("error", "서버 내부 오류: " + ex.getMessage());
            resp.setStatus(500);
            resp.getWriter().print(mapper.writeValueAsString(result));
            logger.info("=== REGISTER REQUEST END (500 - Unexpected error) ===");
        }
    }
}
