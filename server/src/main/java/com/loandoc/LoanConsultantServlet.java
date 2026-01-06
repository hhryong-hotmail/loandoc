package com.loandoc;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * LoanConsultantServlet
 * 대출 상담 관련 요청을 처리하는 서블릿
 * 404 오류 방지를 위한 기본 구현
 */
public class LoanConsultantServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        ObjectNode response = mapper.createObjectNode();
        response.put("ok", true);
        response.put("message", "LoanConsultantServlet is available");
        response.set("data", mapper.createObjectNode());
        
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(response));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        ObjectNode response = mapper.createObjectNode();
        response.put("ok", true);
        response.put("message", "LoanConsultantServlet POST is available");
        response.set("data", mapper.createObjectNode());
        
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(response));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        ObjectNode response = mapper.createObjectNode();
        response.put("ok", true);
        response.put("message", "LoanConsultantServlet PUT is available");
        response.set("data", mapper.createObjectNode());
        
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(response));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        ObjectNode response = mapper.createObjectNode();
        response.put("ok", true);
        response.put("message", "LoanConsultantServlet DELETE is available");
        response.set("data", mapper.createObjectNode());
        
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(mapper.writeValueAsString(response));
    }
}

