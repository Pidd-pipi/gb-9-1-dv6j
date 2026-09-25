package com.knowledge.platform.repository;

import com.knowledge.platform.entity.Subscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends MongoRepository<Subscription, String> {
    Page<Subscription> findByUserId(String userId, Pageable pageable);
    List<Subscription> findByUserIdAndStatus(String userId, Subscription.Status status);

    /**
     * 取用户在某专栏的最新一条订阅（支持续费后状态由 ACTIVE 转 EXPIRED 再转回 ACTIVE 的场景）。
     */
    Optional<Subscription> findFirstByUserIdAndColumnIdOrderByCreatedAtDesc(String userId, String columnId);
    List<Subscription> findByEndDateBeforeAndStatus(LocalDateTime date, Subscription.Status status);
}
