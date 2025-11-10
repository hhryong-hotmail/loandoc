package com.loandoc;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = { "/api/auth/session", "/api/auth/logout" })
public class SessionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        
        if (req.getRequestURI().endsWith("/session")) {
            checkSession(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=utf-8");
        
        if (req.getRequestURI().endsWith("/logout")) {
            logout(req, resp);
        }
    }

    private void checkSession(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ObjectNode response = mapper.createObjectNode();
        HttpSession session = req.getSession(false);
        
        if (session != null && session.getAttribute("userId") != null) {
            response.put("ok", true);
            response.put("authenticated", true);
            response.put("userId", (String) session.getAttribute("userId"));
            String userName = (String) session.getAttribute("userName");
            response.put("userName", userName != null ? userName : session.getAttribute("userId").toString());
        } else {
            response.put("ok", true);
            response.put("authenticated", false);
        }
        
        resp.getWriter().print(mapper.writeValueAsString(response));
    }

    private void logout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ObjectNode response = mapper.createObjectNode();
        HttpSession session = req.getSession(false);
        
        if (session != null) {
            session.invalidate();
        }
        
        response.put("ok", true);
        response.put("message", "로그아웃되었습니다");
        
        resp.getWriter().print(mapper.writeValueAsString(response));
    }
}
