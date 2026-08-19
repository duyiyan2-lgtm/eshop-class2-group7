package com.eshop.backend.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vehicle_sku_option_rule")
public class VehicleSkuOptionRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private Long optionValueId;
    private Boolean available;
    private Boolean included;
    private BigDecimal priceOverride;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
