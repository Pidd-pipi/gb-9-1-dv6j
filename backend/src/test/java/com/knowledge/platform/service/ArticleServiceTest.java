package com.knowledge.platform.service;

import com.knowledge.platform.entity.Article;
import com.knowledge.platform.entity.Column;
import com.knowledge.platform.entity.Subscription;
import com.knowledge.platform.repository.ArticleRepository;
import com.knowledge.platform.repository.ColumnRepository;
import com.knowledge.platform.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ColumnRepository columnRepository;

    @InjectMocks
    private ArticleService articleService;

    private Article article(String id, LocalDateTime createdAt) {
        Article article = new Article();
        article.setId(id);
        article.setColumnId("col1");
        article.setTitle("title-" + id);
        article.setSummary("summary");
        article.setContent("full content");
        article.setCreatedAt(createdAt);
        return article;
    }

    private Subscription subscriptionEndingAt(LocalDateTime endDate) {
        Subscription sub = new Subscription();
        sub.setUserId("u1");
        sub.setColumnId("col1");
        sub.setEndDate(endDate);
        return sub;
    }

    @Test
    void activeSubscriberReadsAllArticles() {
        LocalDateTime now = LocalDateTime.now();
        List<Article> articles = List.of(
                article("a1", now.minusMonths(2)),
                article("a2", now.minusDays(1)));
        when(articleRepository.findByColumnIdOrderBySequenceAsc("col1")).thenReturn(articles);
        when(subscriptionRepository.findByUserIdAndColumnId("u1", "col1"))
                .thenReturn(Optional.of(subscriptionEndingAt(now.plusDays(10))));
        when(columnRepository.findById("col1")).thenReturn(Optional.empty());

        List<Article> result = articleService.getArticlesWithAccess("col1", "u1");

        assertTrue(result.get(0).getReadable());
        assertTrue(result.get(1).getReadable());
    }

    @Test
    void expiredSubscriberReadsOnlyArticlesPublishedBeforeExpiry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.minusDays(10);
        List<Article> articles = List.of(
                article("old", endDate.minusDays(1)),
                article("new", endDate.plusDays(1)));
        when(articleRepository.findByColumnIdOrderBySequenceAsc("col1")).thenReturn(articles);
        when(subscriptionRepository.findByUserIdAndColumnId("u1", "col1"))
                .thenReturn(Optional.of(subscriptionEndingAt(endDate)));
        when(columnRepository.findById("col1")).thenReturn(Optional.empty());

        List<Article> result = articleService.getArticlesWithAccess("col1", "u1");

        assertTrue(result.get(0).getReadable(), "到期前发布的文章仍可读");
        assertFalse(result.get(1).getReadable(), "到期后更新的文章不可读");
    }

    @Test
    void nonSubscriberCannotReadAndContentIsStripped() {
        LocalDateTime now = LocalDateTime.now();
        Article article = article("a1", now.minusDays(1));
        when(articleRepository.findByColumnIdAndId("col1", "a1")).thenReturn(Optional.of(article));
        when(subscriptionRepository.findByUserIdAndColumnId("u1", "col1")).thenReturn(Optional.empty());
        when(columnRepository.findById("col1")).thenReturn(Optional.empty());

        Optional<Article> result = articleService.getArticleWithAccess("col1", "a1", "u1");

        assertTrue(result.isPresent());
        assertFalse(result.get().getReadable());
        assertNull(result.get().getContent(), "不可读时不返回正文");
        assertNotNull(result.get().getSummary(), "不可读时仍返回摘要");
    }

    @Test
    void anonymousUserCannotRead() {
        LocalDateTime now = LocalDateTime.now();
        Article article = article("a1", now.minusDays(1));
        when(articleRepository.findByColumnIdAndId("col1", "a1")).thenReturn(Optional.of(article));

        Optional<Article> result = articleService.getArticleWithAccess("col1", "a1", null);

        assertTrue(result.isPresent());
        assertFalse(result.get().getReadable());
        assertNull(result.get().getContent());
    }

    @Test
    void columnCreatorReadsAllArticles() {
        LocalDateTime now = LocalDateTime.now();
        Column column = new Column();
        column.setId("col1");
        column.setCreatorId("creator1");
        Article article = article("a1", now.minusDays(1));
        when(articleRepository.findByColumnIdAndId("col1", "a1")).thenReturn(Optional.of(article));
        when(columnRepository.findById("col1")).thenReturn(Optional.of(column));

        Optional<Article> result = articleService.getArticleWithAccess("col1", "a1", "creator1");

        assertTrue(result.isPresent());
        assertTrue(result.get().getReadable());
        assertNotNull(result.get().getContent());
    }
}
