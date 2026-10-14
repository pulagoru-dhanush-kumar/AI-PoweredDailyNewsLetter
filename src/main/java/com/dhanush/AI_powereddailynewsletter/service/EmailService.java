package com.dhanush.AI_powereddailynewsletter.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.dhanush.AI_powereddailynewsletter.model.NewsletterArticle;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NewsService newsService;
    private final String from;
    private final String subject;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        NewsService newsService,
                        @Value("${newsletter.mail.from}") String from,
                        @Value("${newsletter.mail.subject:Daily AI News}") String subject) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.newsService = newsService;
        this.from = from;
        this.subject = subject;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> sendNewsletter(List<String> recipients, int limit) {

        Map<String, Object> newsletter = newsService.getNewsletter(limit);
        List<NewsletterArticle> articles = (List<NewsletterArticle>) newsletter.get("articles");

        if (articles.isEmpty()) {
            return Map.of("sent", List.of(), "failed", List.of(),
                    "message", "No articles retrieved, nothing sent.");
        }

        Context ctx = new Context();
        ctx.setVariable("title", newsletter.get("newsletter_title"));
        ctx.setVariable("generatedAt", newsletter.get("generated_at"));
        ctx.setVariable("articles", articles);
        String html = templateEngine.process("newsletter", ctx);

        List<String> sent = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        // One email per recipient so addresses are never exposed to each other.
        for (String to : recipients) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(message);
                sent.add(to);
            } catch (Exception e) {
                log.warn("Failed to send to {}: {}", to, e.getMessage());
                failed.add(to);
            }
        }

        return Map.of("sent", sent, "failed", failed, "article_count", articles.size());
    }
}