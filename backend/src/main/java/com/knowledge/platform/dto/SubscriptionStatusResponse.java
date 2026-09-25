package com.knowledge.platform.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 当前用户对某专栏的订阅状态，以及每个续费档位叠加/重新起算后的到期日。
 */
@Data
public class SubscriptionStatusResponse {
    /** NONE 从未订阅；ACTIVE 订阅有效；EXPIRED 曾订阅但已过期 */
    private String status;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private String plan;

    /** key 为档位（MONTHLY/QUARTERLY/YEARLY），value 为选择该档位续费后的到期日 */
    private Map<String, LocalDateTime> planEndDates;
}
