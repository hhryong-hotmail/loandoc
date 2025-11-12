package com.loandoc;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * 이메일 발송 서비스
 * Gmail SMTP를 사용하여 이메일을 발송합니다.
 */
public class EmailService {
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());
    
    // 환경 변수 또는 시스템 속성에서 이메일 설정 가져오기
    private static final String SMTP_HOST = getConfigValue("SMTP_HOST", "smtp.gmail.com");
    private static final String SMTP_PORT = getConfigValue("SMTP_PORT", "587");
    private static final String SMTP_USERNAME = getConfigValue("SMTP_USERNAME", "");
    private static final String SMTP_PASSWORD = getConfigValue("SMTP_PASSWORD", "");
    private static final String FROM_EMAIL = getConfigValue("FROM_EMAIL", SMTP_USERNAME);
    private static final String FROM_NAME = getConfigValue("FROM_NAME", "LOANDOC");
    
    /**
     * 환경 변수 또는 시스템 속성에서 설정 값 가져오기
     */
    private static String getConfigValue(String key, String defaultValue) {
        // 환경 변수 확인
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        
        // 시스템 속성 확인
        String propValue = System.getProperty(key);
        if (propValue != null && !propValue.isEmpty()) {
            return propValue;
        }
        
        return defaultValue;
    }
    
    /**
     * 임시 비밀번호 이메일 발송
     * 
     * @param toEmail 수신자 이메일
     * @param userId 사용자 아이디
     * @param tempPassword 임시 비밀번호
     * @return 발송 성공 여부
     */
    public static boolean sendTempPasswordEmail(String toEmail, String userId, String tempPassword) {
        // SMTP 설정이 없으면 로그만 출력
        if (SMTP_USERNAME.isEmpty() || SMTP_PASSWORD.isEmpty()) {
            logger.warning("SMTP 설정이 없습니다. 이메일을 발송하지 않고 로그에만 기록합니다.");
            logger.info("=== 임시 비밀번호 (로그 전용) ===");
            logger.info("수신자: " + toEmail);
            logger.info("아이디: " + userId);
            logger.info("임시 비밀번호: " + tempPassword);
            logger.info("================================");
            return false; // 실제 발송은 실패
        }
        
        try {
            // SMTP 서버 속성 설정
            Properties props = new Properties();
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", SMTP_HOST);
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
            
            // 인증 설정
            Authenticator auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
                }
            };
            
            // 세션 생성 (디버그 모드 활성화)
            Session session = Session.getInstance(props, auth);
            session.setDebug(true); // SMTP 통신 과정을 로그로 출력
            
            logger.info("=== SMTP 연결 시도 ===");
            logger.info("Host: " + SMTP_HOST);
            logger.info("Port: " + SMTP_PORT);
            logger.info("Username: " + SMTP_USERNAME);
            logger.info("From: " + FROM_EMAIL);
            logger.info("To: " + toEmail);
            
            // 이메일 메시지 작성
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM_EMAIL, FROM_NAME, "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("[LOANDOC] 임시 비밀번호 발급 안내");
            
            // HTML 형식의 이메일 본문
            String htmlContent = 
                "<html>" +
                "<body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<div style='max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px; padding: 30px;'>" +
                "<h2 style='color: #2d6cdf; margin-top: 0;'>LOANDOC 임시 비밀번호 발급</h2>" +
                "<p>안녕하세요, <strong>" + userId + "</strong>님.</p>" +
                "<p>임시 비밀번호가 발급되었습니다.</p>" +
                "<div style='background-color: #f5f5f5; padding: 20px; border-radius: 5px; margin: 20px 0;'>" +
                "<p style='margin: 0; font-size: 14px; color: #666;'>임시 비밀번호</p>" +
                "<p style='margin: 10px 0 0 0; font-size: 24px; font-weight: bold; color: #2d6cdf; letter-spacing: 2px;'>" + tempPassword + "</p>" +
                "</div>" +
                "<p style='color: #e74c3c; font-weight: bold;'>⚠️ 보안 안내</p>" +
                "<ul style='color: #666; line-height: 1.8;'>" +
                "<li>로그인 후 반드시 비밀번호를 변경해 주세요.</li>" +
                "<li>임시 비밀번호는 타인에게 노출되지 않도록 주의하세요.</li>" +
                "<li>본인이 요청하지 않은 경우, 즉시 고객센터로 문의해 주세요.</li>" +
                "</ul>" +
                "<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;'>" +
                "<p style='color: #999; font-size: 12px; margin: 0;'>본 메일은 발신 전용입니다. 문의사항은 고객센터를 이용해 주세요.</p>" +
                "<p style='color: #999; font-size: 12px; margin: 5px 0 0 0;'>© 2024 LOANDOC. All rights reserved.</p>" +
                "</div>" +
                "</body>" +
                "</html>";
            
            message.setContent(htmlContent, "text/html; charset=UTF-8");
            
            // 이메일 발송
            logger.info("=== 이메일 발송 시작 ===");
            Transport.send(message);
            logger.info("=== 이메일 발송 완료 (Transport.send 성공) ===");
            
            logger.info("임시 비밀번호 이메일 발송 성공: " + toEmail);
            return true;
            
        } catch (MessagingException e) {
            logger.log(Level.SEVERE, "이메일 발송 실패: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "이메일 발송 중 오류 발생: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * SMTP 설정 확인
     * 
     * @return SMTP 설정 완료 여부
     */
    public static boolean isConfigured() {
        return !SMTP_USERNAME.isEmpty() && !SMTP_PASSWORD.isEmpty();
    }
}
