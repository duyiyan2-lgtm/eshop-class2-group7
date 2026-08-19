package com.eshop.backend.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vehicle_option_group")
public class VehicleOptionGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String code;
    private String name;
    private String selectionType;
    private Boolean required;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
