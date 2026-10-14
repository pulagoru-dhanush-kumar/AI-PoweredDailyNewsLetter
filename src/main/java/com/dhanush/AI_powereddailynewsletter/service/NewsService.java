package com.dhanush.AI_powereddailynewsletter.service;

import java.net.URI;
import java.net.URLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dhanush.AI_powereddailynewsletter.llm.Summarizer;
import com.dhanush.AI_powereddailynewsletter.model.NewsletterArticle;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private static final List<String> RSS_FEEDS = List.of(
            "https://techcrunch.com/category/artificial-intelligence/feed/",
            "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml"
    );

    private final Summarizer summarizer;
    private final ObjectMapper objectMapper;

    public NewsService(Summarizer summarizer, ObjectMapper objectMapper) {
        this.summarizer = summarizer;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getNewsletter(int requestedLimit) {

        int limit = Math.max(1, Math.min(requestedLimit, 20));

        List<NewsletterArticle> articles = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();

        for (String feedUrl : RSS_FEEDS) {
            if (articles.size() >= limit) {
                break;
            }

            try {
                SyndFeed feed = readFeed(feedUrl);

                for (SyndEntry entry : feed.getEntries()) {
                    if (articles.size() >= limit) {
                        break;
                    }

                    String title = cleanText(entry.getTitle());
                    String url = entry.getLink();

                    if (title.isBlank() || url == null || url.isBlank() || !seenUrls.add(url)) {
                        continue;
                    }

                    String articleText = extractArticleText(entry);
                    if (articleText.isBlank()) {
                        articleText = title;
                    }

                    try {
                        String aiResponse = summarizer.summarize(title, articleText);
                        articles.add(buildArticle(
                                title,
                                url,
                                feed.getTitle(),
                                entry.getPublishedDate(),
                                aiResponse));
                    } catch (Exception e) {
                        // One failed summary should not lose the whole feed.
                        log.warn("Summarizing '{}' failed: {}", title, e.getMessage());
                    }
                }

            } catch (Exception e) {
                log.warn("Unable to read RSS feed {}: {}", feedUrl, e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newsletter_title", "Daily AI News");
        result.put("generated_at", Instant.now().toString());
        result.put("article_count", articles.size());
        result.put("articles", articles);

        if (articles.isEmpty()) {
            result.put("message", "No articles retrieved. Check the RSS feeds and logs.");
        }

        return result;
    }

    private SyndFeed readFeed(String feedUrl) throws Exception {

        URLConnection connection = URI.create(feedUrl).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("User-Agent", "AI-Daily-Newsletter/1.0");

        try (XmlReader reader = new XmlReader(connection.getInputStream())) {
            return new SyndFeedInput().build(reader);
        }
    }

    private String extractArticleText(SyndEntry entry) {

        StringBuilder text = new StringBuilder();

        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            text.append(entry.getDescription().getValue()).append(' ');
        }

        if (entry.getContents() != null) {
            for (SyndContent content : entry.getContents()) {
                if (content != null && content.getValue() != null) {
                    text.append(content.getValue()).append(' ');
                }
            }
        }

        return cleanText(text.toString());
    }

    private String cleanText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        return Jsoup.clean(html, Safelist.none())
                .replaceAll("\\s+", " ")
                .strip();
    }

    private NewsletterArticle buildArticle(
            String originalTitle,
            String url,
            String source,
            Date publishedDate,
            String aiResponse) {

        String title = originalTitle;
        String summary = aiResponse == null ? "" : aiResponse.strip();
        String keyTakeaway = "Read the original article for more details.";

        try {
            JsonNode json = objectMapper.readTree(summary);

            if (json.hasNonNull("title") && !json.get("title").asString().isBlank()) {
                title = json.get("title").asString();
            }
            if (json.hasNonNull("summary")) {
                summary = json.get("summary").asString();
            }
            if (json.hasNonNull("key_takeaway")) {
                keyTakeaway = json.get("key_takeaway").asString();
            }

        } catch (Exception e) {
            log.debug("AI response was not JSON for '{}'", originalTitle);
        }

        return new NewsletterArticle(
                title,
                url,
                publishedDate == null ? null : publishedDate.toInstant().toString(),
                summary,
                keyTakeaway,
                source == null || source.isBlank() ? "RSS feed" : source);
    }
}