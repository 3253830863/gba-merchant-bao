package com.gba.merchantbao.shipment.service;

import com.gba.merchantbao.auth.security.Actor;
import org.springframework.stereotype.Component;

/**
 * 联调态实现（订单模块未交付）：真实订单表不存在，无法判定订单归属与存在性，
 * 因此一律返回 exists=false。效果：
 *  - 有 shipment 记录 → 正常返回（租户隔离由 mapper 的 merchant_id 谓词保证）；
 *  - 无 shipment 记录 → 统一 404（无法区分「订单不存在/无权查看/未发货」）。
 * 订单模块就绪后替换为真实实现：按可信 Actor 校验订单归属（消费者本人/员工同商户），
 * 存在且无物流记录时返回 exists=true + orderNo，使服务合成 200/NOT_SHIPPED 响应。
 * 最终验收必须接真实订单授权（module-roadmap 明文）。
 */
@Component
public class TenantScopedOrderAccessGuard implements OrderAccessGuard {

    @Override
    public OrderAccess resolve(Actor actor, long orderId) {
        return new OrderAccess(false, null);
    }
}
