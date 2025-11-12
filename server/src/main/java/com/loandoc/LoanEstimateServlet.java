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
        // Set request encoding to UTF-8 BEFORE reading any data
        req.setCharacterEncoding("UTF-8");
        
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
            String healthInsurance = requestBody.has("healthInsurance") ? requestBody.get("healthInsurance").asText() : null;

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
                ObjectNode result = processBank(bank, nationality, remainMonths, annualIncome, age, workingMonths, normalizedVisaType, healthInsurance);
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

        // 1. KB저축은행: 가중치 35%, 최고한도 3000만원
        banks.add(new BankConfig("KB저축은행", 1,
                new String[]{"E-7", "E-9", "F-2", "F-6", "F-5"},
                null, new String[]{"Nepal", "Cambodia"}, 19, null, 8, 3, 1500, 
                2000.0, 14.7, 0.35, 3000.0));

        // 2. 전북은행: 가중치 36%, 최고한도 5000만원
        banks.add(new BankConfig("전북은행", null,
                new String[]{"E-7", "E-9", "F-2", "F-6", "F-5", "F-4"},
                null, null, 19, null, 6, 6, 2000, 
                2000.0, 13.07, 0.36, 5000.0));

        // 3. OK저축은행: 가중치 37%, 최고한도 3500만원
        banks.add(new BankConfig("OK저축은행", null,
                new String[]{"E-9"},
                null, null, 18, 45, 0, 0, 0, 
                2000.0, 15.0, 0.37, 3500.0));

        // 4. 웰컴저축은행: 가중치 36%, 최고한도 3000만원
        banks.add(new BankConfig("웰컴저축은행", null,
                new String[]{"E-9", "E-7"},
                null, null, 0, null, 1, 0, 0, 
                2000.0, 16.0, 0.36, 3000.0));

        // 5. 예가람저축은행: 가중치 38%, 최고한도 4000만원
        banks.add(new BankConfig("예가람저축은행", 5,
                new String[]{"E-7", "E-9", "F-2", "F-6", "F-5"},
                null, null, 20, null, 0, 0, 0, 
                null, null, 0.38, 4000.0));

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
            double annualIncome, int age, int workingMonths, String normalizedVisaType, String healthInsurance) {
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
            if (normalizedVisaType.equals("E-9") || normalizedVisaType.equals("E-7")) {
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
            if (normalizedVisaType.equals("E-9")) {
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

        // 예상한도 계산: 연소득 × 잔여체류기간 × 가중치 / 10
        // 단, 최고한도를 초과할 수 없음
        double calculatedLimit = (annualIncome * remainMonths * bank.weightFactor) / 10.0;
        double finalLimit = Math.min(calculatedLimit, bank.maxLimit);
        
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

        BankConfig(String name, Integer rank, String[] allowedVisaTypes,
                String[] excludedCountries, String[] requiredCountries,
                int minAge, Integer maxAge, int minVisaExpiryDays, int minEmploymentDays,
                double minAnnualIncome, Double estimatedLimit, Double estimatedRate,
                double weightFactor, double maxLimit) {
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
        }
    }
}
