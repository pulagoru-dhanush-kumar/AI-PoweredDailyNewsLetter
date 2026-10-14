	package com.dhanush.AI_powereddailynewsletter.model;
	
	
	
	public record NewsletterArticle(
	        String title,
	        String url,
	        String publishedAt,
	        String summary,
	        String keyTakeaway,
	        String source
	) {
	}
