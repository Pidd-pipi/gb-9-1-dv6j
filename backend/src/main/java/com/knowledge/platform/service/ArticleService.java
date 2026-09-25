package com.knowledge.platform.service;

import com.knowledge.platform.entity.Article;
import com.knowledge.platform.entity.Subscription;
import com.knowledge.platform.repository.ArticleRepository;
import com.knowledge.platform.repository.ColumnRepository;
import com.knowledge.platform.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ArticleService {
    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ColumnRepository columnRepository;

    public Page<Article> getArticles(String columnId, Pageable pageable) {
        return articleRepository.findByColumnId(columnId, pageable);
    }

    public List<Article> getAllArticles(String columnId) {
        return articleRepository.findByColumnIdOrderBySequenceAsc(columnId);
    }

    public Optional<Article> getArticle(String columnId, String articleId) {
        return articleRepository.findByColumnIdAndId(columnId, articleId);
    }

    /**
     * 获取专栏文章列表，并为每篇文章标记当前用户是否可读全文。
     * 规则：专栏作者可读全部；订阅（含已过期）覆盖文章发布时间的可读，
     * 即订阅失效后仍可读到期前发布的文章，之后更新的文章不可读。
     */
    public List<Article> getArticlesWithAccess(String columnId, String userId) {
        List<Article> articles = getAllArticles(columnId);
        LocalDateTime subscriptionEndDate = getSubscriptionEndDate(userId, columnId);
        boolean creator = isColumnCreator(columnId, userId);
        for (Article article : articles) {
            article.setReadable(creator || isCoveredBySubscription(article.getCreatedAt(), subscriptionEndDate));
        }
        return articles;
    }

    /**
     * 获取单篇文章并计算可读性；不可读时不返回正文，只保留摘要。
     */
    public Optional<Article> getArticleWithAccess(String columnId, String articleId, String userId) {
        Optional<Article> articleOpt = getArticle(columnId, articleId);
        if (articleOpt.isEmpty()) {
            return Optional.empty();
        }
        Article article = articleOpt.get();
        LocalDateTime subscriptionEndDate = getSubscriptionEndDate(userId, columnId);
        boolean readable = isColumnCreator(columnId, userId)
                || isCoveredBySubscription(article.getCreatedAt(), subscriptionEndDate);
        article.setReadable(readable);
        if (!readable) {
            article.setContent(null);
        }
        return Optional.of(article);
    }

    private LocalDateTime getSubscriptionEndDate(String userId, String columnId) {
        if (userId == null) {
            return null;
        }
        return subscriptionRepository.findByUserIdAndColumnId(userId, columnId)
                .map(Subscription::getEndDate)
                .orElse(null);
    }

    private boolean isColumnCreator(String columnId, String userId) {
        if (userId == null) {
            return false;
        }
        return columnRepository.findById(columnId)
                .map(column -> userId.equals(column.getCreatorId()))
                .orElse(false);
    }

    private boolean isCoveredBySubscription(LocalDateTime articlePublishedAt, LocalDateTime subscriptionEndDate) {
        if (subscriptionEndDate == null) {
            return false;
        }
        // 历史数据可能没有发布时间，视为订阅期内已发布
        return articlePublishedAt == null || !articlePublishedAt.isAfter(subscriptionEndDate);
    }
}
