package com.eshop.backend.common;

public enum ErrorCode {
    SUCCESS(0, "success"),
    VALIDATION_ERROR(40001, "参数校验失败"),
    PASSWORD_TOO_LONG(40002, "密码不能超过72个UTF-8字节"),
    UNAUTHORIZED(40101, "请先登录"),
    FORBIDDEN(40301, "没有操作权限"),
    USERNAME_EXISTS(40901, "用户名已存在"),
    LOGIN_FAILED(40102, "用户名或密码错误"),
    USER_DISABLED(40302, "该账号已被禁用"),
    INVALID_FILE(40020, "文件类型或内容不符合要求"),
    CATEGORY_CYCLE(40021, "分类层级不能形成循环"),
    INVALID_SKU_SPECS(40022, "SKU规格必须是JSON对象"),
    CART_QUANTITY_LIMIT(40023, "单个SKU最多购买99件"),
    EMPTY_CART(40030, "请先选择要结算的购物车商品"),
    NOT_FOUND(40401, "资源不存在"),
    CATEGORY_NOT_FOUND(40410, "商品分类不存在"),
    PRODUCT_NOT_FOUND(40411, "商品不存在"),
    SKU_NOT_FOUND(40412, "SKU不存在"),
    CART_ITEM_NOT_FOUND(40413, "购物车商品不存在"),
    ADDRESS_NOT_FOUND(40414, "收货地址不存在"),
    ORDER_NOT_FOUND(40415, "订单不存在"),
    USER_NOT_FOUND(40416, "用户不存在"),
    OUT_OF_STOCK(40910, "SKU库存不足"),
    SKU_CODE_EXISTS(40911, "SKU编码已存在"),
    INVALID_ORDER_STATUS(40912, "当前订单状态不允许此操作"),
    PRODUCT_NOT_ON_SALE(40913, "商品未上架"),
    PRODUCT_REQUIRES_SKU(40914, "商品至少需要一个启用的SKU才能上架"),
    CATEGORY_DISABLED(40915, "商品分类已停用"),
    PRODUCT_REQUIRES_ENABLED_SKU(40916, "上架商品至少需要保留一个启用的SKU"),
    CATEGORY_HAS_ON_SALE_PRODUCTS(40917, "分类下存在已上架商品，请先将商品下架"),
    SELF_DISABLE_NOT_ALLOWED(40918, "不能禁用当前登录账号"),
    CATEGORY_NAME_EXISTS(40920, "同级分类名称已存在"),
    PRODUCT_MUST_BE_OFF_SALE(40921, "请先将商品下架再删除"),
    CATEGORY_NOT_EMPTY(40922, "分类下存在子分类或商品，不能删除"),
    PRODUCT_HAS_ACTIVE_ORDERS(40923, "商品存在未完成订单，不能删除"),
    CATEGORY_PARENT_DISABLED(40924, "上级分类已停用，当前分类不能启用"),
    CATEGORY_HAS_ENABLED_CHILDREN(40925, "分类下存在启用的子分类，请先停用子分类"),
    ORDER_PAYMENT_EXPIRED(40926, "订单已超过支付时限"),
    SKU_HAS_ACTIVE_ORDERS(40927, "SKU存在未完成订单，不能删除"),
    ORDER_ITEM_NOT_FOUND(40417, "订单商品不存在"),
    FAVORITE_ALREADY_EXISTS(40928, "商品已经收藏"),
    ORDER_NOT_COMPLETED_FOR_REVIEW(40929, "订单尚未完成，不能评价"),
    REVIEW_ALREADY_EXISTS(40930, "订单商品已经评价"),
    REVIEW_RATING_INVALID(40031, "评分必须在1至5分之间"),
    REVIEW_CONTENT_INVALID(40032, "评价内容长度必须在1至1000个字符之间"),
    INTERNAL_ERROR(50000, "系统异常，请稍后重试"),
    FILE_SAVE_FAILED(50001, "文件保存失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
