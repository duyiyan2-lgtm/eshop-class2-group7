package com.eshop.backend.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vehicle_option_value")
public class VehicleOptionValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String code;
    private String name;
    private BigDecimal priceDelta;
    private String previewImage;
    private String colorHex;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
