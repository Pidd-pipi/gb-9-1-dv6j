package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.SubscribeRequest;
import com.knowledge.platform.dto.SubscriptionStatusResponse;
import com.knowledge.platform.entity.Article;
import com.knowledge.platform.entity.Column;
import com.knowledge.platform.entity.Subscription;
import com.knowledge.platform.repository.ColumnRepository;
import com.knowledge.platform.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SubscriptionService {
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private PointsService pointsService;

    public ApiResponse<Page<Subscription>> getMySubscriptions(String userId, Pageable pageable) {
        Page<Subscription> subscriptions = subscriptionRepository.findByUserId(userId, pageable);
        subscriptions.forEach(this::refreshStatusByDate);
        return ApiResponse.success(subscriptions);
    }

    /**
     * 订阅/续费同一入口：
     * - 从未订阅：当前时间起算，新增订阅并增加专栏订阅人数；
     * - 尚未到期提前续费：新时长从原到期日往后叠加；
     * - 已经过期再续费：从当前时间重新起算。
     */
    @Transactional
    public ApiResponse<Subscription> subscribe(String userId, String columnId, SubscribeRequest request) {
        Optional<Column> columnOpt = columnRepository.findById(columnId);
        if (columnOpt.isEmpty()) {
            return ApiResponse.error("专栏不存在");
        }

        Subscription.Plan plan;
        try {
            plan = Subscription.Plan.valueOf(request.getPlan().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ApiResponse.error("无效的订阅档位");
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<Subscription> latestOpt =
                subscriptionRepository.findFirstByUserIdAndColumnIdOrderByCreatedAtDesc(userId, columnId);

        Subscription subscription;
        String message;

        if (latestOpt.isEmpty()) {
            subscription = new Subscription();
            subscription.setUserId(userId);
            subscription.setColumnId(columnId);
            subscription.setStartDate(now);
            subscription.setEndDate(plusDuration(now, plan));
            subscription.setCreatedAt(now);
            subscription.setPlan(plan);
            subscription.setStatus(Subscription.Status.ACTIVE);

            Column column = columnOpt.get();
            column.setSubscriberCount(column.getSubscriberCount() + 1);
            columnRepository.save(column);
            message = "订阅成功";
        } else {
            subscription = latestOpt.get();
            boolean wasActive = isActive(subscription, now);
            LocalDateTime base = wasActive ? subscription.getEndDate() : now;
            subscription.setEndDate(plusDuration(base, plan));
            if (!wasActive) {
                subscription.setStartDate(now);
            }
            subscription.setPlan(plan);
            subscription.setStatus(Subscription.Status.ACTIVE);
            message = wasActive ? "续费成功" : "重新订阅成功";
        }

        subscription.setUpdatedAt(now);
        subscription = subscriptionRepository.save(subscription);
        return ApiResponse.success(message, subscription);
    }

    /**
     * 当前用户在专栏的订阅状态，以及每个续费档位调整后的到期日。
     */
    public SubscriptionStatusResponse getSubscriptionStatus(String userId, String columnId) {
        LocalDateTime now = LocalDateTime.now();
        SubscriptionStatusResponse response = new SubscriptionStatusResponse();
        response.setStatus("NONE");

        LocalDateTime base = now;
        if (userId != null) {
            Optional<Subscription> latestOpt =
                    subscriptionRepository.findFirstByUserIdAndColumnIdOrderByCreatedAtDesc(userId, columnId);
            if (latestOpt.isPresent()) {
                Subscription subscription = latestOpt.get();
                boolean active = isActive(subscription, now);
                response.setStatus(active ? "ACTIVE" : "EXPIRED");
                response.setStartDate(subscription.getStartDate());
                response.setEndDate(subscription.getEndDate());
                response.setPlan(subscription.getPlan().name());
                if (active) {
                    base = subscription.getEndDate();
                }
            }
        }

        Map<String, LocalDateTime> planEndDates = new LinkedHashMap<>();
        for (Subscription.Plan plan : Subscription.Plan.values()) {
            // 有效订阅续费在原到期日上叠加，其余情况（未订阅/已过期）从当前时间起算
            planEndDates.put(plan.name(), plusDuration(base, plan));
        }
        response.setPlanEndDates(planEndDates);
        return response;
    }

    /**
     * 判断当前用户能否阅读某篇文章：
     * - 专栏创作者：始终可读；
     * - 订阅有效：专栏内所有文章可读；
     * - 订阅已失效：到期日之前（含到期当日）已发布的文章可读，之后更新的文章不可读；
     * - 从未订阅：所有文章不可读。
     * 返回 null 表示可读，否则返回锁定原因（NOT_SUBSCRIBED / EXPIRED_AFTER）。
     */
    public String getLockReason(String userId, String columnId, Article article) {
        if (userId != null) {
            Optional<Column> columnOpt = columnRepository.findById(columnId);
            if (columnOpt.isEmpty()) {
                return "NOT_SUBSCRIBED";
            }
            return getLockReason(userId, columnOpt.get(), article);
        }
        return "NOT_SUBSCRIBED";
    }

    /**
     * 列表场景使用：专栏在外层只查一次后传入，避免逐篇重复查询。
     */
    public String getLockReason(String userId, Column column, Article article) {
        if (userId == null) {
            return "NOT_SUBSCRIBED";
        }
        if (userId.equals(column.getCreatorId())) {
            return null;
        }
        Optional<Subscription> latestOpt =
                subscriptionRepository.findFirstByUserIdAndColumnIdOrderByCreatedAtDesc(
                        userId, column.getId());
        if (latestOpt.isEmpty()) {
            return "NOT_SUBSCRIBED";
        }
        Subscription subscription = latestOpt.get();
        if (isActive(subscription, LocalDateTime.now())) {
            return null;
        }
        LocalDateTime publishedAt = article.getCreatedAt();
        if (publishedAt != null && !publishedAt.isAfter(subscription.getEndDate())) {
            return null;
        }
        return "EXPIRED_AFTER";
    }

    /**
     * 定时把已过到期日的订阅标记为 EXPIRED（访问判断本身以到期日为准，此任务仅做状态收敛）。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    @Transactional
    public void markExpiredSubscriptions() {
        List<Subscription> expired = subscriptionRepository.findByEndDateBeforeAndStatus(
                LocalDateTime.now(), Subscription.Status.ACTIVE
        );
        expired.forEach(subscription -> subscription.setStatus(Subscription.Status.EXPIRED));
        subscriptionRepository.saveAll(expired);
    }

    private LocalDateTime plusDuration(LocalDateTime base, Subscription.Plan plan) {
        return switch (plan) {
            case MONTHLY -> base.plusMonths(1);
            case QUARTERLY -> base.plusMonths(3);
            case YEARLY -> base.plusYears(1);
        };
    }

    private boolean isActive(Subscription subscription, LocalDateTime now) {
        return subscription.getEndDate() != null && !subscription.getEndDate().isBefore(now);
    }

    private void refreshStatusByDate(Subscription subscription) {
        if (subscription.getStatus() == Subscription.Status.ACTIVE && !isActive(subscription, LocalDateTime.now())) {
            subscription.setStatus(Subscription.Status.EXPIRED);
            subscriptionRepository.save(subscription);
        }
    }
}
