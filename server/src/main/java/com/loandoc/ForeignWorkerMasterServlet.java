package com.loandoc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet("/api/foreign_worker_master")
public class ForeignWorkerMasterServlet extends HttpServlet {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/loandoc";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode requestBody;

        try {
            requestBody = mapper.readTree(req.getReader());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Invalid JSON\"}");
            return;
        }

        // Extract fields from request body
        String userId = requestBody.has("user_id") ? requestBody.get("user_id").asText() : null;
        String name = requestBody.has("name") ? requestBody.get("name").asText() : null;
        String nationality = requestBody.has("nationality") ? requestBody.get("nationality").asText() : null;
        String passportNumber = requestBody.has("passport_number") ? requestBody.get("passport_number").asText() : null;
        String birthDate = requestBody.has("birth_date") ? requestBody.get("birth_date").asText() : null;
        String entryDate = requestBody.has("entry_date") ? requestBody.get("entry_date").asText() : null;
        String phoneNumber = requestBody.has("phone_number") ? requestBody.get("phone_number").asText() : null;
        String currentCompany = requestBody.has("current_company") ? requestBody.get("current_company").asText() : null;
        String email = requestBody.has("email") ? requestBody.get("email").asText() : null;

        // Validate required fields
        if (userId == null || userId.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"ok\":false,\"error\":\"user_id is required\"}");
            return;
        }

        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // Check if user_id exists
            String checkSql = "SELECT COUNT(*) FROM foreign_worker_master WHERE user_id = ?";
            checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, userId);
            rs = checkStmt.executeQuery();
            
            boolean exists = false;
            if (rs.next()) {
                exists = rs.getInt(1) > 0;
            }
            rs.close();
            checkStmt.close();

            String action;
            if (exists) {
                // UPDATE existing record
                action = "update";
                String updateSql = "UPDATE foreign_worker_master SET " +
                        "name = ?, " +
                        "nationality = ?, " +
                        "passport_number = ?, " +
                        "birth_date = ?::date, " +
                        "entry_date = ?::date, " +
                        "phone_number = ?, " +
                        "current_company = ?, " +
                        "email = ? " +
                        "WHERE user_id = ?";
                
                stmt = conn.prepareStatement(updateSql);
                stmt.setString(1, name);
                stmt.setString(2, nationality);
                stmt.setString(3, passportNumber);
                stmt.setString(4, birthDate);
                stmt.setString(5, entryDate);
                stmt.setString(6, phoneNumber);
                stmt.setString(7, currentCompany);
                stmt.setString(8, email);
                stmt.setString(9, userId);
            } else {
                // INSERT new record
                action = "insert";
                String insertSql = "INSERT INTO foreign_worker_master " +
                        "(user_id, name, nationality, passport_number, birth_date, entry_date, phone_number, current_company, email) " +
                        "VALUES (?, ?, ?, ?, ?::date, ?::date, ?, ?, ?)";
                
                stmt = conn.prepareStatement(insertSql);
                stmt.setString(1, userId);
                stmt.setString(2, name);
                stmt.setString(3, nationality);
                stmt.setString(4, passportNumber);
                stmt.setString(5, birthDate);
                stmt.setString(6, entryDate);
                stmt.setString(7, phoneNumber);
                stmt.setString(8, currentCompany);
                stmt.setString(9, email);
            }

            int rowsAffected = stmt.executeUpdate();

            // Prepare response
            ObjectNode response = mapper.createObjectNode();
            response.put("ok", true);
            response.put("action", action);
            response.put("message", action.equals("update") ? 
                "✅ 정보가 성공적으로 업데이트되었습니다." : 
                "✅ 정보가 성공적으로 저장되었습니다.");
            
            // Return saved data
            ObjectNode data = mapper.createObjectNode();
            data.put("user_id", userId);
            data.put("name", name);
            data.put("nationality", nationality);
            data.put("passport_number", passportNumber);
            data.put("birth_date", birthDate);
            data.put("entry_date", entryDate);
            data.put("phone_number", phoneNumber);
            data.put("current_company", currentCompany);
            response.set("data", data);

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(mapper.writeValueAsString(response));

        } catch (SQLException e) {
            e.printStackTrace();
            ObjectNode errorResponse = mapper.createObjectNode();
            errorResponse.put("ok", false);
            
            // SQL State 확인하여 적절한 오류 메시지 반환
            String sqlState = e.getSQLState();
            String errorMessage = e.getMessage();
            
            // 고유 제약 조건 위반 (23505)
            if ("23505".equals(sqlState)) {
                // 여권번호 중복
                if (errorMessage.contains("foreign_worker_master_passport_number_key")) {
                    errorResponse.put("error", "오류: 중복된 여권번호입니다. 이미 등록된 여권번호입니다.");
                    resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409
                }
                // 사용자 ID 중복
                else if (errorMessage.contains("user_id")) {
                    errorResponse.put("error", "오류: 중복된 사용자 ID입니다.");
                    resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409
                }
                // 기타 중복 오류
                else {
                    errorResponse.put("error", "오류: 중복된 데이터입니다. " + errorMessage);
                    resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409
                }
            }
            // NOT NULL 제약 조건 위반 (23502)
            else if ("23502".equals(sqlState)) {
                errorResponse.put("error", "오류: 필수 입력 항목이 누락되었습니다. " + errorMessage);
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            }
            // 기타 데이터베이스 오류
            else {
                errorResponse.put("error", "데이터베이스 오류: " + errorMessage);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            }
            
            resp.getWriter().write(mapper.writeValueAsString(errorResponse));
        } finally {
            try {
                if (rs != null) rs.close();
                if (checkStmt != null) checkStmt.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        ObjectMapper mapper = new ObjectMapper();

        // Get userId from session
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"ok\":false,\"error\":\"Not authenticated\"}");
            return;
        }

        String userId = (String) session.getAttribute("userId");

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            String selectSql = "SELECT user_id, name, nationality, passport_number, " +
                    "birth_date, entry_date, phone_number, current_company, email " +
                    "FROM foreign_worker_master WHERE user_id = ?";
            
            stmt = conn.prepareStatement(selectSql);
            stmt.setString(1, userId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                // Data found - return it
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("found", true);

                ObjectNode data = mapper.createObjectNode();
                data.put("user_id", rs.getString("user_id"));
                data.put("name", rs.getString("name"));
                data.put("nationality", rs.getString("nationality"));
                data.put("passport_number", rs.getString("passport_number"));
                
                // Handle dates
                Date birthDate = rs.getDate("birth_date");
                if (birthDate != null) {
                    data.put("birth_date", birthDate.toString());
                }
                
                Date entryDate = rs.getDate("entry_date");
                if (entryDate != null) {
                    data.put("entry_date", entryDate.toString());
                }
                
                data.put("phone_number", rs.getString("phone_number"));
                data.put("current_company", rs.getString("current_company"));
                data.put("email", rs.getString("email"));

                response.set("data", data);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            } else {
                // No data found
                ObjectNode response = mapper.createObjectNode();
                response.put("ok", true);
                response.put("found", false);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(mapper.writeValueAsString(response));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            ObjectNode errorResponse = mapper.createObjectNode();
            errorResponse.put("ok", false);
            errorResponse.put("error", "Database error: " + e.getMessage());
            resp.getWriter().write(mapper.writeValueAsString(errorResponse));
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
