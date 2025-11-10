package com.loandoc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@WebServlet(urlPatterns = {"/api/server/loan-estimate"})
public class LoanEstimateServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");

        try {
            // Parse request body
            ObjectNode requestBody = mapper.readValue(req.getReader(), ObjectNode.class);

            String loginId = requestBody.has("loginId") ? requestBody.get("loginId").asText() : null;
            String nationality = requestBody.has("nationality") ? requestBody.get("nationality").asText() : null;
            Integer remainMonths = requestBody.has("remainMonths") ? requestBody.get("remainMonths").asInt() : null;
            Double annualIncome = requestBody.has("annualIncome") ? requestBody.get("annualIncome").asDouble() : null;
            Integer age = requestBody.has("age") ? requestBody.get("age").asInt() : null;
            Integer workingMonths = requestBody.has("workingMonths") ? requestBody.get("workingMonths").asInt() : null;
            String visaType = requestBody.has("visaType") ? requestBody.get("visaType").asText() : null;

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

            // Bank configurations
            List<BankConfig> banks = getBankConfigurations();

            // Process each bank
            ArrayNode results = mapper.createArrayNode();
            for (BankConfig bank : banks) {
                ObjectNode result = processBank(bank, nationality, remainMonths, annualIncome, age, workingMonths, normalizedVisaType);
                results.add(result);
            }

            // Sort by rank
            List<ObjectNode> sortedResults = new ArrayList<>();
            results.forEach(node -> sortedResults.add((ObjectNode) node));
            sortedResults.sort((a, b) -> {
                int rankA = a.has("rank") && !a.get("rank").isNull() ? a.get("rank").asInt() : 999;
                int rankB = b.has("rank") && !b.get("rank").isNull() ? b.get("rank").asInt() : 999;
                return Integer.compare(rankA, rankB);
            });

            ArrayNode finalResults = mapper.createArrayNode();
            sortedResults.forEach(finalResults::add);

            // Wrap in response object
            ObjectNode response = mapper.createObjectNode();
            response.set("banks", finalResults);

            res.getWriter().write(mapper.writeValueAsString(response));

        } catch (Exception e) {
            e.printStackTrace();
            res.setStatus(500);
            ObjectNode error = mapper.createObjectNode();
            error.put("ok", false);
            error.put("error", "server error");
            res.getWriter().write(mapper.writeValueAsString(error));
        }
    }

    private String normalizeVisaType(String visa) {
        if (visa == null) {
            return "";
        }
        String cleaned = visa.replaceAll("[-\\s]", "").toUpperCase();
        return cleaned.replaceAll("([A-Z])(\\d+)", "$1-$2");
    }

    private List<BankConfig> getBankConfigurations() {
        List<BankConfig> banks = new ArrayList<>();

        // KB저축은행: Vietnam 제외, 나이 19세 이상, 연소득 2000만원 이상, 잔여체류기간별 한도 차등
        banks.add(new BankConfig("KB저축은행", 1,
                new String[]{"E-7", "F-2", "E-9", "F-4", "F-5", "F-6"},
                new String[]{"Vietnam"}, null, 19, 12, 1, 2000, 2000.0, 14.7));

        banks.add(new BankConfig("전북은행", null,
                new String[]{"E-7", "F-2", "E-9", "F-4", "F-5", "F-6"},
                null, null, 19, 1, 30, 2000, 2000.0, 12.0));

        banks.add(new BankConfig("OK저축은행", null,
                new String[]{"E-7", "F-2", "E-9", "F-4", "F-5", "F-6"},
                null, null, 19, 1, 30, 2000, 2000.0, 15.0));

        banks.add(new BankConfig("웰컴저축은행", null,
                new String[]{"E-7", "F-2", "E-9", "F-4", "F-5", "F-6"},
                null, null, 19, 1, 30, 2000, 2000.0, 16.0));

        banks.add(new BankConfig("예가람저축은행", 5,
                new String[]{"E-7", "F-2", "E-9", "F-4", "F-5", "F-6"},
                null, null, 20, 1, 30, 2000, null, null));

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
    }

    private ObjectNode processBank(BankConfig bank, String nationality, int remainMonths,
            double annualIncome, int age, int workingMonths, String normalizedVisaType) {
        ObjectNode result = mapper.createObjectNode();
        result.put("bankName", bank.name);

        // Visa type validation
        ObjectNode visaTypeNode = mapper.createObjectNode();
        boolean visaTypeValid = containsString(bank.allowedVisaTypes, normalizedVisaType);
        visaTypeNode.put("valid", visaTypeValid);
        visaTypeNode.put("error", visaTypeValid ? "" : "E비자종류");
        result.set("visaType", visaTypeNode);

        // Country validation
        ObjectNode countryNode = mapper.createObjectNode();
        boolean countryValid = true;
        if (bank.excludedCountries != null && containsString(bank.excludedCountries, nationality)) {
            countryValid = false;
        }
        if (bank.requiredCountries != null && !containsString(bank.requiredCountries, nationality)) {
            countryValid = false;
        }
        countryNode.put("valid", countryValid);
        countryNode.put("error", countryValid ? "" : "E국가");
        result.set("country", countryNode);

        // Age validation
        ObjectNode ageNode = mapper.createObjectNode();
        boolean ageValid = age >= bank.minAge;
        ageNode.put("valid", ageValid);
        ageNode.put("error", ageValid ? "" : "E나이");
        result.set("age", ageNode);

        // Visa expiry validation (잔여체류기간)
        ObjectNode visaExpiryNode = mapper.createObjectNode();
        boolean visaExpiryValid = remainMonths >= bank.minVisaExpiryDays;
        visaExpiryNode.put("valid", visaExpiryValid);
        visaExpiryNode.put("error", visaExpiryValid ? "" : "E비자만료");
        result.set("visaExpiry", visaExpiryNode);

        // Employment date validation
        ObjectNode employmentDateNode = mapper.createObjectNode();
        boolean employmentDateValid = workingMonths >= bank.minEmploymentDays / 30.0;
        employmentDateNode.put("valid", employmentDateValid);
        employmentDateNode.put("error", employmentDateValid ? "" : "E재직일자");
        result.set("employmentDate", employmentDateNode);

        // Annual income validation
        ObjectNode annualIncomeNode = mapper.createObjectNode();
        boolean annualIncomeValid = annualIncome >= bank.minAnnualIncome;
        annualIncomeNode.put("valid", annualIncomeValid);
        annualIncomeNode.put("error", annualIncomeValid ? "" : "E연소득");
        result.set("annualIncome", annualIncomeNode);

        // KB저축은행의 경우 잔여체류기간에 따라 예상한도를 다르게 설정
        if (bank.name.equals("KB저축은행")) {
            if (remainMonths >= 24) {
                result.put("estimatedLimit", 2000.0);
            } else if (remainMonths >= 18) {
                result.put("estimatedLimit", 1500.0);
            } else if (remainMonths >= 12) {
                result.put("estimatedLimit", 1000.0);
            } else {
                result.putNull("estimatedLimit");
            }
            result.put("estimatedRate", 14.7);
        } else {
            if (bank.estimatedLimit != null) {
                result.put("estimatedLimit", bank.estimatedLimit);
            } else {
                result.putNull("estimatedLimit");
            }
            
            if (bank.estimatedRate != null) {
                result.put("estimatedRate", bank.estimatedRate);
            } else {
                result.putNull("estimatedRate");
            }
        }

        if (bank.rank != null) {
            result.put("rank", bank.rank);
        } else {
            result.putNull("rank");
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
        int minVisaExpiryDays;
        int minEmploymentDays;
        double minAnnualIncome;
        Double estimatedLimit;
        Double estimatedRate;

        BankConfig(String name, Integer rank, String[] allowedVisaTypes,
                String[] excludedCountries, String[] requiredCountries,
                int minAge, int minVisaExpiryDays, int minEmploymentDays,
                double minAnnualIncome, Double estimatedLimit, Double estimatedRate) {
            this.name = name;
            this.rank = rank;
            this.allowedVisaTypes = allowedVisaTypes;
            this.excludedCountries = excludedCountries;
            this.requiredCountries = requiredCountries;
            this.minAge = minAge;
            this.minVisaExpiryDays = minVisaExpiryDays;
            this.minEmploymentDays = minEmploymentDays;
            this.minAnnualIncome = minAnnualIncome;
            this.estimatedLimit = estimatedLimit;
            this.estimatedRate = estimatedRate;
        }
    }
}
