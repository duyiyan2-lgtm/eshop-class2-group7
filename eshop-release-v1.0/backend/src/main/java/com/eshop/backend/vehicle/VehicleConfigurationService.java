package com.eshop.backend.vehicle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.backend.catalog.entity.Product;
import com.eshop.backend.catalog.entity.ProductSku;
import com.eshop.backend.catalog.mapper.ProductMapper;
import com.eshop.backend.catalog.mapper.ProductSkuMapper;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.vehicle.dto.VehicleConfiguratorResponse;
import com.eshop.backend.vehicle.dto.VehicleQuoteResponse;
import com.eshop.backend.vehicle.dto.VehicleSelectedOptionResponse;
import com.eshop.backend.vehicle.entity.VehicleOptionGroup;
import com.eshop.backend.vehicle.entity.VehicleOptionValue;
import com.eshop.backend.vehicle.entity.VehicleSkuOptionRule;
import com.eshop.backend.vehicle.mapper.VehicleOptionGroupMapper;
import com.eshop.backend.vehicle.mapper.VehicleOptionValueMapper;
import com.eshop.backend.vehicle.mapper.VehicleSkuOptionRuleMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VehicleConfigurationService {
    public static final String PRODUCT_KIND_VEHICLE = "VEHICLE";
    public static final String PRODUCT_KIND_STANDARD = "STANDARD";

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final VehicleOptionGroupMapper groupMapper;
    private final VehicleOptionValueMapper valueMapper;
    private final VehicleSkuOptionRuleMapper ruleMapper;
    private final ObjectMapper objectMapper;

    public VehicleConfiguratorResponse getConfigurator(Long productId) {
        Product product = requireOnSaleVehicle(productId);
        List<ProductSku> skus = skuMapper.selectList(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getProductId, productId)
                .eq(ProductSku::getStatus, "ENABLED")
                .orderByAsc(ProductSku::getPrice)
                .orderByAsc(ProductSku::getId));
        if (skus.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_REQUIRES_ENABLED_SKU);
        }
        List<VehicleOptionGroup> groups = listEnabledGroups(productId);
        List<VehicleOptionValue> values = listEnabledValues(groups.stream().map(VehicleOptionGroup::getId).collect(Collectors.toSet()));
        Map<Long, List<VehicleOptionValue>> valuesByGroup = values.stream()
                .collect(Collectors.groupingBy(VehicleOptionValue::getGroupId, LinkedHashMap::new, Collectors.toList()));
        List<VehicleSkuOptionRule> rules = skus.isEmpty()
                ? List.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<VehicleSkuOptionRule>()
                        .in(VehicleSkuOptionRule::getSkuId, skus.stream().map(ProductSku::getId).toList()));

        ProductSku defaultSku = skus.get(0);
        Map<Long, VehicleSkuOptionRule> defaultRules = rules.stream()
                .filter(rule -> Objects.equals(rule.getSkuId(), defaultSku.getId()))
                .collect(Collectors.toMap(VehicleSkuOptionRule::getOptionValueId, Function.identity(), (left, right) -> left));
        List<Long> defaultOptionIds = new ArrayList<>();
        for (VehicleOptionGroup group : groups) {
            if (!Boolean.TRUE.equals(group.getRequired()) || !"SINGLE".equals(group.getSelectionType())) {
                continue;
            }
            valuesByGroup.getOrDefault(group.getId(), List.of()).stream()
                    .filter(value -> {
                        VehicleSkuOptionRule rule = defaultRules.get(value.getId());
                        return rule == null || !Boolean.FALSE.equals(rule.getAvailable());
                    })
                    .findFirst()
                    .ifPresent(value -> defaultOptionIds.add(value.getId()));
        }

        return new VehicleConfiguratorResponse(
                product.getId(),
                product.getName(),
                product.getSubtitle(),
                product.getDetail(),
                product.getMainImage(),
                "非小米汽车官方页面，仅用于课程项目演示",
                defaultSku.getId(),
                VehicleConfigurationHasher.normalizeIds(defaultOptionIds),
                skus.stream().map(sku -> new VehicleConfiguratorResponse.VehicleSkuCardResponse(
                        sku.getId(),
                        sku.getSkuCode(),
                        versionName(sku),
                        driveType(sku),
                        money(sku.getPrice()),
                        sku.getStock(),
                        sku.getStatus())).toList(),
                groups.stream().map(group -> new VehicleConfiguratorResponse.VehicleOptionGroupResponse(
                        group.getId(),
                        group.getCode(),
                        group.getName(),
                        group.getSelectionType(),
                        Boolean.TRUE.equals(group.getRequired()),
                        group.getSortOrder(),
                        valuesByGroup.getOrDefault(group.getId(), List.of()).stream()
                                .map(value -> new VehicleConfiguratorResponse.VehicleOptionValueCardResponse(
                                        value.getId(),
                                        value.getCode(),
                                        value.getName(),
                                        money(value.getPriceDelta()),
                                        value.getPreviewImage(),
                                        value.getColorHex(),
                                        value.getSortOrder()))
                                .toList())).toList(),
                rules.stream().map(rule -> new VehicleConfiguratorResponse.VehicleOptionRuleResponse(
                        rule.getSkuId(),
                        rule.getOptionValueId(),
                        !Boolean.FALSE.equals(rule.getAvailable()),
                        Boolean.TRUE.equals(rule.getIncluded()),
                        rule.getPriceOverride() == null ? null : money(rule.getPriceOverride()))).toList());
    }

    public VehicleQuoteResponse quote(Long productId, Long skuId, Collection<Long> optionValueIds) {
        Product product = requireOnSaleVehicle(productId);
        ProductSku sku = requireEnabledSku(productId, skuId);
        List<VehicleOptionGroup> groups = listEnabledGroups(productId);
        Map<Long, VehicleOptionGroup> groupById = groups.stream()
                .collect(Collectors.toMap(VehicleOptionGroup::getId, Function.identity()));
        Map<Long, VehicleOptionValue> valueById = listEnabledValues(groupById.keySet()).stream()
                .collect(Collectors.toMap(VehicleOptionValue::getId, Function.identity()));
        Map<Long, VehicleSkuOptionRule> ruleByValueId = listRules(sku.getId()).stream()
                .collect(Collectors.toMap(VehicleSkuOptionRule::getOptionValueId, Function.identity(), (left, right) -> left));

        List<Long> requestedIds = VehicleConfigurationHasher.normalizeIds(optionValueIds);
        List<Long> effectiveIds = mergeIncludedOptions(requestedIds, ruleByValueId);
        Map<Long, Integer> selectedCountByGroup = new LinkedHashMap<>();
        List<VehicleSelectedOptionResponse> selected = new ArrayList<>();
        BigDecimal optionAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Long valueId : effectiveIds) {
            VehicleOptionValue value = valueById.get(valueId);
            if (value == null) {
                throw new BusinessException(ErrorCode.VEHICLE_OPTION_NOT_FOUND);
            }
            VehicleOptionGroup group = groupById.get(value.getGroupId());
            if (group == null) {
                throw new BusinessException(ErrorCode.VEHICLE_OPTION_NOT_FOUND);
            }
            VehicleSkuOptionRule rule = ruleByValueId.get(valueId);
            if (rule != null && Boolean.FALSE.equals(rule.getAvailable())) {
                throw new BusinessException(ErrorCode.VEHICLE_OPTION_NOT_COMPATIBLE);
            }
            selectedCountByGroup.merge(group.getId(), 1, Integer::sum);
            if ("SINGLE".equals(group.getSelectionType()) && selectedCountByGroup.get(group.getId()) > 1) {
                throw new BusinessException(ErrorCode.VEHICLE_OPTION_GROUP_INVALID);
            }
            boolean included = rule != null && Boolean.TRUE.equals(rule.getIncluded());
            BigDecimal delta = resolvePriceDelta(value, rule, included);
            optionAmount = optionAmount.add(delta);
            selected.add(new VehicleSelectedOptionResponse(
                    group.getId(),
                    group.getCode(),
                    group.getName(),
                    value.getId(),
                    value.getCode(),
                    value.getName(),
                    delta,
                    value.getColorHex(),
                    included));
        }

        for (VehicleOptionGroup group : groups) {
            int count = selectedCountByGroup.getOrDefault(group.getId(), 0);
            if (Boolean.TRUE.equals(group.getRequired()) && count < 1) {
                throw new BusinessException(ErrorCode.VEHICLE_OPTION_REQUIRED);
            }
            if ("SINGLE".equals(group.getSelectionType()) && count > 1) {
                throw new BusinessException(ErrorCode.VEHICLE_OPTION_GROUP_INVALID);
            }
        }

        BigDecimal basePrice = money(sku.getPrice());
        optionAmount = money(optionAmount);
        BigDecimal unitPrice = money(basePrice.add(optionAmount));
        String summary = buildSummary(sku, selected);
        String json = writeSnapshot(product, sku, selected, basePrice, optionAmount, unitPrice);
        String hash = VehicleConfigurationHasher.hash(effectiveIds);
        boolean purchasable = sku.getStock() > 0;
        return new VehicleQuoteResponse(
                product.getId(),
                product.getName(),
                sku.getId(),
                sku.getSkuCode(),
                versionName(sku),
                selected,
                summary,
                json,
                hash,
                basePrice,
                optionAmount,
                unitPrice,
                sku.getStock(),
                purchasable,
                purchasable ? null : "当前版本暂时缺货");
    }

    public boolean isVehicle(Product product) {
        return product != null && PRODUCT_KIND_VEHICLE.equals(product.getProductKind());
    }

    private Product requireOnSaleVehicle(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (!"ON_SALE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        if (!isVehicle(product)) {
            throw new BusinessException(ErrorCode.VEHICLE_PRODUCT_REQUIRED);
        }
        return product;
    }

    private ProductSku requireEnabledSku(Long productId, Long skuId) {
        ProductSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(ErrorCode.SKU_NOT_FOUND);
        }
        if (!Objects.equals(sku.getProductId(), productId) || !"ENABLED".equals(sku.getStatus())) {
            throw new BusinessException(ErrorCode.VEHICLE_SKU_INVALID);
        }
        return sku;
    }

    private List<VehicleOptionGroup> listEnabledGroups(Long productId) {
        return groupMapper.selectList(new LambdaQueryWrapper<VehicleOptionGroup>()
                .eq(VehicleOptionGroup::getProductId, productId)
                .eq(VehicleOptionGroup::getStatus, "ENABLED")
                .orderByAsc(VehicleOptionGroup::getSortOrder)
                .orderByAsc(VehicleOptionGroup::getId));
    }

    private List<VehicleOptionValue> listEnabledValues(Set<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        return valueMapper.selectList(new LambdaQueryWrapper<VehicleOptionValue>()
                .in(VehicleOptionValue::getGroupId, groupIds)
                .eq(VehicleOptionValue::getStatus, "ENABLED")
                .orderByAsc(VehicleOptionValue::getSortOrder)
                .orderByAsc(VehicleOptionValue::getId));
    }

    private List<VehicleSkuOptionRule> listRules(Long skuId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<VehicleSkuOptionRule>()
                .eq(VehicleSkuOptionRule::getSkuId, skuId));
    }

    private List<Long> mergeIncludedOptions(List<Long> requestedIds, Map<Long, VehicleSkuOptionRule> ruleByValueId) {
        List<Long> merged = new ArrayList<>(requestedIds);
        ruleByValueId.values().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getIncluded()) && !Boolean.FALSE.equals(rule.getAvailable()))
                .map(VehicleSkuOptionRule::getOptionValueId)
                .filter(id -> !merged.contains(id))
                .forEach(merged::add);
        return VehicleConfigurationHasher.normalizeIds(merged);
    }

    private BigDecimal resolvePriceDelta(VehicleOptionValue value, VehicleSkuOptionRule rule, boolean included) {
        if (included) {
            return money(BigDecimal.ZERO);
        }
        if (rule != null && rule.getPriceOverride() != null) {
            return money(rule.getPriceOverride());
        }
        return money(value.getPriceDelta());
    }

    private String buildSummary(ProductSku sku, List<VehicleSelectedOptionResponse> selected) {
        List<String> parts = new ArrayList<>();
        parts.add(versionName(sku));
        selected.forEach(option -> parts.add(option.groupName() + " " + option.valueName()));
        return String.join(" / ", parts);
    }

    public List<Long> optionIdsFromSnapshot(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return List.of();
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(configurationJson, Map.class);
            Object raw = parsed.get("optionValueIds");
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            return VehicleConfigurationHasher.normalizeIds(list.stream()
                    .map(item -> item instanceof Number number ? number.longValue() : null)
                    .toList());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.VEHICLE_OPTION_NOT_FOUND);
        }
    }

    private String driveType(ProductSku sku) {
        Object value = specValue(sku, "驱动形式");
        return value == null ? "" : value.toString();
    }

    private String versionName(ProductSku sku) {
        Object version = specValue(sku, "车型版本");
        return version == null || version.toString().isBlank() ? sku.getSkuCode() : version.toString();
    }

    private Object specValue(ProductSku sku, String key) {
        String specs = sku.getSpecsJson();
        if (specs == null || specs.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(specs, Map.class);
            return parsed.get(key);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String writeSnapshot(
            Product product,
            ProductSku sku,
            List<VehicleSelectedOptionResponse> selected,
            BigDecimal basePrice,
            BigDecimal optionAmount,
            BigDecimal unitPrice) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", product.getId());
        snapshot.put("productName", product.getName());
        snapshot.put("skuId", sku.getId());
        snapshot.put("skuCode", sku.getSkuCode());
        snapshot.put("versionName", versionName(sku));
        snapshot.put("basePrice", basePrice);
        snapshot.put("optionAmount", optionAmount);
        snapshot.put("unitPrice", unitPrice);
        snapshot.put("optionValueIds", selected.stream().map(VehicleSelectedOptionResponse::valueId).toList());
        snapshot.put("options", selected);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
