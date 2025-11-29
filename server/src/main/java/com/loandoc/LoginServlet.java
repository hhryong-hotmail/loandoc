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
import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = { "/api/auth/login" })
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LoginServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        
        ObjectNode requestBody = mapper.readValue(req.getReader(), ObjectNode.class);
        String userId = requestBody.has("id") ? requestBody.get("id").asText().trim() : null;
        String password = requestBody.has("password") ? requestBody.get("password").asText().trim() : null;
        
        ObjectNode response = mapper.createObjectNode();
        
        if (userId == null || userId.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            resp.setStatus(400);
            response.put("ok", false);
            response.put("error", "아이디와 비밀번호를 입력하세요");
            resp.getWriter().print(mapper.writeValueAsString(response));
            return;
        }

        // DB 연결 설정
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
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "No db.properties found on classpath", e);
        }

        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:postgresql://localhost:5432/" + defaultDbName;
        } else if (!dbUrl.startsWith("jdbc:")) {
            dbUrl = "jdbc:postgresql://" + dbUrl;
        }
        if (dbUser == null || dbUser.isEmpty()) dbUser = "postgres";
        if (dbPass == null || dbPass.isEmpty()) dbPass = "postgres";

        // DB에서 사용자 확인 및 비밀번호 검증
        boolean authenticated = false;
        String userName = null;
        
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                // user_account 테이블에서 bcrypt 해시 비밀번호 확인
                String sql = "SELECT user_id, password FROM user_account WHERE user_id = ? LIMIT 1";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String hashedPassword = rs.getString("password");
                            
                            // Log only non-sensitive info. Avoid logging plaintext passwords or stored hashes.
                            logger.fine("Login attempt for user: " + userId);

                            // bcrypt 해시 검증
                            at.favre.lib.crypto.bcrypt.BCrypt.Result verifyResult =
                                    at.favre.lib.crypto.bcrypt.BCrypt.verifyer().verify(password.toCharArray(), hashedPassword);

                            if (verifyResult.verified) {
                                authenticated = true;
                                userName = userId; // user_account 테이블에 name 없으므로 user_id 사용
                                logger.fine("Password verification succeeded for user: " + userId);
                            } else {
                                // Keep failed attempts at INFO for visibility, still avoid sensitive data
                                logger.info("Password verification failed for user: " + userId);
                            }
                        } else {
                            logger.info("User not found in user_account: " + userId);
                        }
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "DB authentication failed: " + ex.getMessage(), ex);
        }

        if (authenticated) {
            // 세션 생성
            HttpSession session = req.getSession(true);
            session.setAttribute("userId", userId);
            session.setAttribute("userName", userName);
            session.setMaxInactiveInterval(3600); // 1시간
            
            response.put("ok", true);
            response.put("userId", userId);
            response.put("userName", userName != null ? userName : userId);
            logger.info("Login successful for user: " + userId);
        } else {
            resp.setStatus(401);
            response.put("ok", false);
            response.put("error", "아이디 또는 비밀번호가 올바르지 않습니다");
            logger.info("Login failed for user: " + userId);
        }
        
        resp.getWriter().print(mapper.writeValueAsString(response));
    }
}
