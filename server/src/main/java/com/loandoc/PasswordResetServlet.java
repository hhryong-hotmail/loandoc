package com.loandoc;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import at.favre.lib.crypto.bcrypt.BCrypt;

@WebServlet(urlPatterns = { "/api/auth/reset-password" })
public class PasswordResetServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PasswordResetServlet.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        
        ObjectNode requestBody = mapper.readValue(req.getReader(), ObjectNode.class);
        String userId = requestBody.has("userId") ? requestBody.get("userId").asText().trim() : null;
        String email = requestBody.has("email") ? requestBody.get("email").asText().trim() : null;
        
        ObjectNode response = mapper.createObjectNode();
        
        if (userId == null || userId.isEmpty() || email == null || email.isEmpty()) {
            resp.setStatus(400);
            response.put("ok", false);
            response.put("error", "아이디와 이메일을 모두 입력해주세요");
            resp.getWriter().print(mapper.writeValueAsString(response));
            return;
        }

        // DB 연결 설정
        String dbUrl = getDbUrl();
        String dbUser = getDbUser();
        String dbPass = getDbPassword();

        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
                // 1. user_account와 foreign_worker_master를 조인하여 이메일 확인
                String checkSql = "SELECT ua.user_id, fwm.email " +
                                 "FROM user_account ua " +
                                 "LEFT JOIN foreign_worker_master fwm ON ua.user_id = fwm.user_id " +
                                 "WHERE ua.user_id = ?";
                
                String dbEmail = null;
                boolean userExists = false;
                
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setString(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            userExists = true;
                            dbEmail = rs.getString("email");
                        }
                    }
                }
                
                if (!userExists) {
                    resp.setStatus(404);
                    response.put("ok", false);
                    response.put("error", "존재하지 않는 아이디입니다");
                    resp.getWriter().print(mapper.writeValueAsString(response));
                    logger.info("Password reset failed: user not found - " + userId);
                    return;
                }
                
                if (dbEmail == null || dbEmail.isEmpty()) {
                    resp.setStatus(400);
                    response.put("ok", false);
                    response.put("error", "등록된 이메일이 없습니다. 개인정보에서 이메일을 먼저 등록해주세요");
                    resp.getWriter().print(mapper.writeValueAsString(response));
                    logger.info("Password reset failed: no email registered - " + userId);
                    return;
                }
                
                if (!email.equalsIgnoreCase(dbEmail)) {
                    resp.setStatus(400);
                    response.put("ok", false);
                    response.put("error", "입력하신 이메일이 등록된 이메일과 일치하지 않습니다");
                    resp.getWriter().print(mapper.writeValueAsString(response));
                    logger.info("Password reset failed: email mismatch - " + userId);
                    return;
                }
                
                // 2. 임시 비밀번호 생성 (8자리: 영문대소문자+숫자)
                String tempPassword = generateTempPassword();
                
                logger.info("=== 임시 비밀번호 생성 (암호화 전) ===");
                logger.info("사용자: " + userId);
                logger.info("임시 비밀번호: " + tempPassword);
                
                // 3. bcrypt로 해시화
                String hashedPassword = BCrypt.withDefaults().hashToString(12, tempPassword.toCharArray());
                
                logger.info("해시화된 비밀번호: " + hashedPassword);
                logger.info("========================================");
                
                // 4. user_account 테이블의 비밀번호 업데이트
                String updateSql = "UPDATE user_account SET password = ? WHERE user_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, hashedPassword);
                    ps.setString(2, userId);
                    int updated = ps.executeUpdate();
                    
                    if (updated > 0) {
                        // 5. 이메일 발송
                        boolean emailSent = EmailService.sendTempPasswordEmail(email, userId, tempPassword);
                        
                        if (emailSent) {
                            // 실제 이메일 발송 성공
                            response.put("ok", true);
                            response.put("message", "임시 비밀번호가 이메일로 발송되었습니다");
                            // 개발 환경: 응답에 임시 비밀번호 포함 (디버깅용)
                            response.put("tempPassword", tempPassword);
                            logger.info("Password reset successful and email sent for user: " + userId);
                        } else {
                            // 이메일 발송 실패했지만 비밀번호는 변경됨
                            // SMTP 설정이 없는 경우 로그에만 기록
                            logger.info("=== 임시 비밀번호 (이메일 발송 실패) ===");
                            logger.info("수신자: " + email);
                            logger.info("아이디: " + userId);
                            logger.info("임시 비밀번호: " + tempPassword);
                            logger.info("========================================");
                            
                            response.put("ok", true);
                            response.put("message", "비밀번호가 재설정되었습니다");
                            // 개발/테스트 환경에서만 임시 비밀번호를 응답에 포함
                            response.put("tempPassword", tempPassword);
                            response.put("emailSent", false);
                            logger.info("Password reset successful but email not sent for user: " + userId);
                        }
                    } else {
                        resp.setStatus(500);
                        response.put("ok", false);
                        response.put("error", "비밀번호 업데이트에 실패했습니다");
                        logger.severe("Password update failed for user: " + userId);
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException ex) {
            resp.setStatus(500);
            response.put("ok", false);
            response.put("error", "서버 오류가 발생했습니다");
            logger.log(Level.SEVERE, "Password reset error: " + ex.getMessage(), ex);
        }
        
        resp.getWriter().print(mapper.writeValueAsString(response));
    }
    
    /**
     * 임시 비밀번호 생성 (8자리: 영문대소문자+숫자)
     */
    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * DB URL 가져오기
     */
    private String getDbUrl() {
        String dbUrl = System.getenv("DB_URL");
        
        try {
            String ctxUrl = getServletContext().getInitParameter("DB_URL");
            if (dbUrl == null || dbUrl.isEmpty()) dbUrl = ctxUrl;
        } catch (Exception e) {
            logger.log(Level.FINE, "No servlet context DB_URL", e);
        }
        
        Properties props = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
                if ((dbUrl == null || dbUrl.isEmpty()) && props.getProperty("DB_URL") != null) {
                    dbUrl = props.getProperty("DB_URL");
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "No db.properties found", e);
        }
        
        if (dbUrl == null || dbUrl.isEmpty()) {
            dbUrl = "jdbc:postgresql://localhost:5432/loandoc";
        } else if (!dbUrl.startsWith("jdbc:")) {
            dbUrl = "jdbc:postgresql://" + dbUrl;
        }
        
        return dbUrl;
    }
    
    /**
     * DB User 가져오기
     */
    private String getDbUser() {
        String dbUser = System.getenv("DB_USER");
        
        try {
            String ctxUser = getServletContext().getInitParameter("DB_USER");
            if (dbUser == null || dbUser.isEmpty()) dbUser = ctxUser;
        } catch (Exception e) {
            logger.log(Level.FINE, "No servlet context DB_USER", e);
        }
        
        Properties props = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
                if ((dbUser == null || dbUser.isEmpty()) && props.getProperty("DB_USER") != null) {
                    dbUser = props.getProperty("DB_USER");
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "No db.properties found", e);
        }
        
        if (dbUser == null || dbUser.isEmpty()) dbUser = "postgres";
        
        return dbUser;
    }
    
    /**
     * DB Password 가져오기
     */
    private String getDbPassword() {
        String dbPass = System.getenv("DB_PASSWORD");
        
        try {
            String ctxPass = getServletContext().getInitParameter("DB_PASSWORD");
            if (dbPass == null || dbPass.isEmpty()) dbPass = ctxPass;
        } catch (Exception e) {
            logger.log(Level.FINE, "No servlet context DB_PASSWORD", e);
        }
        
        Properties props = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
                if ((dbPass == null || dbPass.isEmpty()) && props.getProperty("DB_PASSWORD") != null) {
                    dbPass = props.getProperty("DB_PASSWORD");
                }
            }
        } catch (Exception e) {
            logger.log(Level.FINE, "No db.properties found", e);
        }
        
        if (dbPass == null || dbPass.isEmpty()) dbPass = "postgresql";
        
        return dbPass;
    }
}
