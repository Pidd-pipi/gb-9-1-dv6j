package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.dto.SubscribeRequest;
import com.knowledge.platform.entity.Column;
import com.knowledge.platform.entity.Subscription;
import com.knowledge.platform.repository.ColumnRepository;
import com.knowledge.platform.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ColumnRepository columnRepository;

    @Mock
    private PointsService pointsService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private Column sampleColumn() {
        Column column = new Column();
        column.setId("col1");
        column.setSubscriberCount(10);
        return column;
    }

    private SubscribeRequest request(String plan) {
        SubscribeRequest request = new SubscribeRequest();
        request.setPlan(plan);
        return request;
    }

    @Test
    void firstSubscribeStartsFromNow() {
        when(columnRepository.findById("col1")).thenReturn(Optional.of(sampleColumn()));
        when(subscriptionRepository.findByUserIdAndColumnId("u1", "col1")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        ApiResponse<Subscription> res = subscriptionService.subscribe("u1", "col1", request("MONTHLY"));
        LocalDateTime after = LocalDateTime.now();

        assertTrue(res.isSuccess());
        Subscription sub = res.getData();
        assertEquals(Subscription.Status.ACTIVE, sub.getStatus());
        assertFalse(sub.getStartDate().isBefore(before));
        assertFalse(sub.getStartDate().isAfter(after));
        // 月付：到期日约为 1 个月后
        assertTrue(sub.getEndDate().isAfter(before.plusMonths(1).minusSeconds(5)));
        assertTrue(sub.getEndDate().isBefore(after.plusMonths(1).plusSeconds(5)));
        // 首次订阅订阅数 +1
        assertEquals(11, columnRepository.findById("col1").get().getSubscriberCount());
    }

    @Test
    void renewBeforeExpiryStacksOnOriginalEndDate() {
        LocalDateTime originalEnd = LocalDateTime.now().plusDays(10);
        Subscription existing = new Subscription();
        existing.setId("sub1");
        existing.setUserId("u1");
        existing.setColumnId("col1");
        existing.setPlan(Subscription.Plan.MONTHLY);
        existing.setStartDate(LocalDateTime.now().minusDays(20));
        existing.setEndDate(originalEnd);
        existing.setStatus(Subscription.Status.ACTIVE);

        when(columnRepository.findById("col1")).thenReturn(Optional.of(sampleColumn()));
        when(subscriptionRepository.findByUserIdAndColumnId("u1", "col1")).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiResponse<Subscription> res = subscriptionService.subscribe("u1", "col1", request("QUARTERLY"));

        assertTrue(res.isSuccess());
        assertTrue(res.getMessage().contains("续费成功"));
        Subscription sub = res.getData();
        // 新时长从原到期日往后叠加 3 个月
        assertEquals(originalEnd.plusMonths(3), sub.getEndDate());
        // 开始时间不变
        assertEquals(existing.getStartDate(), sub.getStartDate());
        assertEquals(Subscription.Plan.QUARTERLY, sub.getPlan());
        assertEquals(Subscription.Status.ACTIVE, sub.getStatus());
        // 续费不重复累计订阅数
        verify(columnRepository, never()).save(any(Column.class));
    }

    @Test
    void renewAfterExpiryRestartsFromNow() {
        LocalDateTime expiredEnd = LocalDateTime.now().minusDays(5);
        Subscription existing = new Subscription();
        existing.setId("sub1");
        existing.setUserId("u1");
        existing.setColumnId("col1");
        existing.setPlan(Subscription.Plan.MONTHLY);
        existing.setStartDate(LocalDateTime.now().minusMonths(2));
        existing.setEndDate(expiredEnd);
        existing.setStatus(Subscription.Status.EXPIRED);

        when(columnRepository.findById("col1")).thenReturn(Optional.of(sampleColumn()));
        when(subscriptionRepository.findByUserIdAndColumnId("u1", "col1")).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        ApiResponse<Subscription> res = subscriptionService.subscribe("u1", "col1", request("YEARLY"));
        LocalDateTime after = LocalDateTime.now();

        assertTrue(res.isSuccess());
        Subscription sub = res.getData();
        // 已过期：从当前时间重新起算，而不是叠加在旧到期日上
        assertFalse(sub.getStartDate().isBefore(before));
        assertFalse(sub.getStartDate().isAfter(after));
        assertTrue(sub.getEndDate().isAfter(before.plusYears(1).minusSeconds(5)));
        assertTrue(sub.getEndDate().isBefore(after.plusYears(1).plusSeconds(5)));
        assertEquals(Subscription.Status.ACTIVE, sub.getStatus());
    }

    @Test
    void invalidPlanReturnsError() {
        when(columnRepository.findById("col1")).thenReturn(Optional.of(sampleColumn()));

        ApiResponse<Subscription> res = subscriptionService.subscribe("u1", "col1", request("WEEKLY"));

        assertFalse(res.isSuccess());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void getSubscriptionLazilyMarksExpired() {
        Subscription existing = new Subscription();
        existing.setId("sub1");
        existing.setUserId("u1");
        existing.setColumnId("col1");
        existing.setEndDate(LocalDateTime.now().minusDays(1));
        existing.setStatus(Subscription.Status.ACTIVE);

        when(subscriptionRepository.findByUserIdAndColumnId("u1", "col1")).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Subscription> result = subscriptionService.getSubscription("u1", "col1");

        assertTrue(result.isPresent());
        assertEquals(Subscription.Status.EXPIRED, result.get().getStatus());
        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(Subscription.Status.EXPIRED, captor.getValue().getStatus());
    }
}
