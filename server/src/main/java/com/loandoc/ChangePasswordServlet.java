package com.loandoc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import at.favre.lib.crypto.bcrypt.BCrypt;

@WebServlet("/api/auth/change-password")
public class ChangePasswordServlet extends HttpServlet {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        ObjectNode response = mapper.createObjectNode();

        // 세션 확인
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.put("ok", false);
            response.put("error", "로그인이 필요합니다.");
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write(mapper.writeValueAsString(response));
            return;
        }

        String userId = (String) session.getAttribute("userId");

        try {
            // 요청 본문 파싱
            ObjectNode requestBody = mapper.readValue(req.getInputStream(), ObjectNode.class);
            String currentPassword = requestBody.has("currentPassword") ? requestBody.get("currentPassword").asText() : "";
            String newPassword = requestBody.has("newPassword") ? requestBody.get("newPassword").asText() : "";

            // 입력 검증
            if (currentPassword.isEmpty()) {
                response.put("ok", false);
                response.put("error", "기존 비밀번호를 입력하세요.");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(mapper.writeValueAsString(response));
                return;
            }

            if (newPassword.isEmpty()) {
                response.put("ok", false);
                response.put("error", "새 비밀번호를 입력하세요.");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(mapper.writeValueAsString(response));
                return;
            }

            // 비밀번호 정책 검증 (8자 이상, 숫자 + 특수문자 포함)
            if (newPassword.length() < 8 || 
                !newPassword.matches(".*[0-9].*") || 
                !newPassword.matches(".*[^A-Za-z0-9].*")) {
                response.put("ok", false);
                response.put("error", "새 비밀번호는 8자 이상, 숫자와 특수문자를 포함해야 합니다.");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write(mapper.writeValueAsString(response));
                return;
            }

            // 데이터베이스 연결
            String dbUrl = System.getenv("DB_URL");
            String dbUser = System.getenv("DB_USER");
            String dbPassword = System.getenv("DB_PASSWORD");

            // 기본값 설정
            if (dbUrl == null) dbUrl = "jdbc:postgresql://localhost:5432/loandoc";
            if (dbUser == null) dbUser = "postgres";
            if (dbPassword == null) dbPassword = "postgres";

            Class.forName("org.postgresql.Driver");

            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                // 1. 현재 비밀번호 확인
                String selectSql = "SELECT password FROM user_account WHERE user_id = ?";
                String currentHashedPassword = null;

                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    selectStmt.setString(1, userId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            currentHashedPassword = rs.getString("password");
                        } else {
                            response.put("ok", false);
                            response.put("error", "사용자를 찾을 수 없습니다.");
                            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            resp.getWriter().write(mapper.writeValueAsString(response));
                            return;
                        }
                    }
                }

                // 2. 기존 비밀번호 검증
                BCrypt.Result verifyResult = BCrypt.verifyer().verify(
                    currentPassword.toCharArray(), 
                    currentHashedPassword
                );

                if (!verifyResult.verified) {
                    response.put("ok", false);
                    response.put("error", "기존 비밀번호가 일치하지 않습니다.");
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    resp.getWriter().write(mapper.writeValueAsString(response));
                    return;
                }

                // 3. 새 비밀번호 해싱
                int bcryptCost = 12;
                String newHashedPassword = BCrypt.withDefaults().hashToString(
                    bcryptCost, 
                    newPassword.toCharArray()
                );

                // 4. 비밀번호 업데이트
                String updateSql = "UPDATE user_account SET password = ? WHERE user_id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, newHashedPassword);
                    updateStmt.setString(2, userId);
                    
                    int rowsAffected = updateStmt.executeUpdate();
                    
                    if (rowsAffected > 0) {
                        response.put("ok", true);
                        response.put("message", "비밀번호가 성공적으로 변경되었습니다.");
                        resp.setStatus(HttpServletResponse.SC_OK);
                    } else {
                        response.put("ok", false);
                        response.put("error", "비밀번호 변경에 실패했습니다.");
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
                response.put("ok", false);
                response.put("error", "데이터베이스 오류: " + e.getMessage());
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("error", "PostgreSQL 드라이버를 찾을 수 없습니다.");
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("error", "서버 오류: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        resp.getWriter().write(mapper.writeValueAsString(response));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        resp.setContentType("application/json; charset=UTF-8");
        
        ObjectNode response = mapper.createObjectNode();
        response.put("ok", false);
        response.put("error", "GET 메소드는 지원되지 않습니다. POST를 사용하세요.");
        resp.getWriter().write(mapper.writeValueAsString(response));
    }
}
