package com.loandoc;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/api/auth/check-session")
public class CheckSessionServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");
        HttpSession session = req.getSession(false);
        String userId = null;
        boolean authenticated = false;
        if (session != null) {
            Object uid = session.getAttribute("userId");
            if (uid != null) {
                userId = uid.toString();
                authenticated = true;
            }
        }
        PrintWriter out = resp.getWriter();
        if (authenticated) {
            out.print("{\"ok\":true,\"userId\":\"" + userId + "\"}");
        } else {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"ok\":false,\"error\":\"로그인이 필요합니다\"}");
        }
        out.flush();
    }
}
