package com.smartstorm.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务：用于发送注册验证码。
 * 使用 QQ SMTP（587 + STARTTLS），配置见 application.yml / application-local.yml。
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    /** 发件人地址。取自 spring.mail.username —— QQ SMTP 要求发件人与认证账号一致 */
    @Value("${spring.mail.username:}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** 发送验证码邮件（register=注册，reset=重置密码） */
    public void sendCodeMail(String to, String code, String purpose) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("未配置发件邮箱：请在 application-local.yml 中设置 spring.mail.username");
        }
        boolean isReset = "reset".equals(purpose);
        String subject = isReset ? "SmartStorm 重置密码" : "SmartStorm 注册验证码";
        String action = isReset ? "重置您的 SmartStorm 账号密码" : "注册 SmartStorm 账号";
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(
                    "<div style=\"font-family:sans-serif;max-width:480px;margin:0 auto;padding:24px;border:1px solid #e5e9f2;border-radius:12px;\">"
                            + "<h2 style=\"margin-top:0;color:#3f4a5e;\">SmartStorm</h2>"
                            + "<p style=\"color:#5b6472;\">您正在" + action + "，验证码为：</p>"
                            + "<p style=\"font-size:32px;letter-spacing:6px;font-weight:700;color:#5b6cff;margin:12px 0;\">" + code + "</p>"
                            + "<p style=\"color:#9aa6bf;font-size:13px;\">验证码 5 分钟内有效。若非本人操作，请忽略本邮件。</p>"
                            + "</div>",
                    true);
            mailSender.send(mime);
            log.info("验证码邮件已发送 to={}, purpose={}", to, purpose);
        } catch (Exception e) {
            log.error("验证码邮件发送失败 to={}", to, e);
            throw new IllegalStateException("邮件发送失败，请稍后重试");
        }
    }
}
