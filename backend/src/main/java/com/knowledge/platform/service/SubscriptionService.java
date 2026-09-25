package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.SubscribeRequest;
import com.knowledge.platform.entity.Column;
import com.knowledge.platform.entity.Subscription;
import com.knowledge.platform.repository.ColumnRepository;
import com.knowledge.platform.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class SubscriptionService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ColumnRepository columnRepository;

    @Autowired
    private PointsService pointsService;

    public ApiResponse<Page<Subscription>> getMySubscriptions(String userId, Pageable pageable) {
        Page<Subscription> subscriptions = subscriptionRepository.findByUserId(userId, pageable);
        subscriptions.forEach(this::refreshStatusIfExpired);
        return ApiResponse.success(subscriptions);
    }

    /**
     * 查询用户对某个专栏的当前订阅（含已过期），不存在时返回空。
     */
    public Optional<Subscription> getSubscription(String userId, String columnId) {
        if (userId == null) {
            return Optional.empty();
        }
        return subscriptionRepository.findByUserIdAndColumnId(userId, columnId)
                .map(this::refreshStatusIfExpired);
    }

    /**
     * 订阅已过期但状态仍未更新时，惰性刷新为 EXPIRED。
     */
    private Subscription refreshStatusIfExpired(Subscription subscription) {
        if (subscription.getStatus() == Subscription.Status.ACTIVE
                && subscription.getEndDate() != null
                && subscription.getEndDate().isBefore(LocalDateTime.now())) {
            subscription.setStatus(Subscription.Status.EXPIRED);
            subscription.setUpdatedAt(LocalDateTime.now());
            return subscriptionRepository.save(subscription);
        }
        return subscription;
    }

    /**
     * 订阅/续费统一入口：
     * - 首次订阅：从当前时间起算；
     * - 到期前续费：新时长在原到期日基础上往后叠加；
     * - 已过期续费：从当前时间重新起算。
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
        Optional<Subscription> existingOpt = subscriptionRepository.findByUserIdAndColumnId(userId, columnId);
        if (existingOpt.isPresent()) {
            Subscription subscription = existingOpt.get();
            boolean stillActive = subscription.getEndDate() != null
                    && subscription.getEndDate().isAfter(now);
            // 未过期则在原到期日上叠加；已过期则重新起算
            LocalDateTime base = stillActive ? subscription.getEndDate() : now;
            if (!stillActive) {
                subscription.setStartDate(now);
            }
            subscription.setEndDate(plusPlan(base, plan));
            subscription.setPlan(plan);
            subscription.setStatus(Subscription.Status.ACTIVE);
            subscription.setUpdatedAt(now);
            subscription = subscriptionRepository.save(subscription);

            String message = (stillActive ? "续费成功，有效期已延长至 " : "订阅已重新生效，有效期至 ")
                    + subscription.getEndDate().format(DATE_FORMATTER);
            return ApiResponse.success(message, subscription);
        }

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setColumnId(columnId);
        subscription.setPlan(plan);
        subscription.setStartDate(now);
        subscription.setEndDate(plusPlan(now, plan));
        subscription.setStatus(Subscription.Status.ACTIVE);
        subscription.setCreatedAt(now);
        subscription.setUpdatedAt(now);

        subscription = subscriptionRepository.save(subscription);

        Column column = columnOpt.get();
        column.setSubscriberCount(column.getSubscriberCount() + 1);
        columnRepository.save(column);

        return ApiResponse.success("订阅成功，有效期至 " + subscription.getEndDate().format(DATE_FORMATTER), subscription);
    }

    private LocalDateTime plusPlan(LocalDateTime base, Subscription.Plan plan) {
        return switch (plan) {
            case MONTHLY -> base.plusMonths(1);
            case QUARTERLY -> base.plusMonths(3);
            case YEARLY -> base.plusYears(1);
        };
    }
}
