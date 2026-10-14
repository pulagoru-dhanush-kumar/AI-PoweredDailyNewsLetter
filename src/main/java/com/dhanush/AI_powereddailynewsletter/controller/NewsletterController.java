package com.dhanush.AI_powereddailynewsletter.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dhanush.AI_powereddailynewsletter.model.SendRequest;
import com.dhanush.AI_powereddailynewsletter.service.EmailService;
import com.dhanush.AI_powereddailynewsletter.service.GroqService;
import com.dhanush.AI_powereddailynewsletter.service.NewsService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {

    private final NewsService newsService;
    private final GroqService groqService;
    private final EmailService emailService;

    public NewsletterController(NewsService newsService,
                                GroqService groqService,
                                EmailService emailService) {
        this.newsService = newsService;
        this.groqService = groqService;
        this.emailService = emailService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> newsletter(@RequestParam(defaultValue = "5") int limit) {
        if (limit < 1 || limit > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "limit must be 1-20"));
        }
        return ResponseEntity.ok(newsService.getNewsletter(limit));
    }

    @GetMapping("/test-ai")
    public String testAi() {
        return groqService.summarize("Open-source AI models are easier to run locally. Explain in three bullets.");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody SendRequest request) {
        int limit = request.limit() == null ? 5 : request.limit();
        if (limit < 1 || limit > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "limit must be 1-20"));
        }
        return ResponseEntity.ok(emailService.sendNewsletter(request.emails(), limit));
    }
}