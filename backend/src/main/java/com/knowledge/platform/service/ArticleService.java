package com.knowledge.platform.service;

import com.knowledge.platform.entity.Article;
import com.knowledge.platform.entity.Column;
import com.knowledge.platform.repository.ArticleRepository;
import com.knowledge.platform.repository.ColumnRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ArticleService {
    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    /**
     * 文章列表：按当前用户的订阅关系逐篇标记可读状态，专栏信息只查询一次。
     */
    public List<Article> getArticlesWithAccess(String columnId, String userId) {
        List<Article> articles = articleRepository.findByColumnIdOrderBySequenceAsc(columnId);
        Optional<Column> columnOpt = columnRepository.findById(columnId);
        Column column = columnOpt.orElse(null);
        articles.forEach(article -> {
            String lockReason = column == null
                    ? "NOT_SUBSCRIBED"
                    : subscriptionService.getLockReason(userId, column, article);
            applyAccess(article, lockReason);
        });
        return articles;
    }

    /**
     * 单篇文章：按当前用户的订阅关系补全阅读状态；不可读时不返回正文，仅保留摘要。
     */
    public Article getArticleWithAccess(String columnId, String articleId, String userId) {
        Optional<Article> articleOpt = articleRepository.findByColumnIdAndId(columnId, articleId);
        if (articleOpt.isEmpty()) {
            return null;
        }
        Article article = articleOpt.get();
        applyAccess(article, subscriptionService.getLockReason(userId, columnId, article));
        return article;
    }

    private void applyAccess(Article article, String lockReason) {
        if (lockReason == null) {
            article.setReadable(true);
        } else {
            article.setReadable(false);
            article.setLockReason(lockReason);
            // 锁定文章只下发摘要，正文不下发
            article.setContent(null);
        }
    }
}
