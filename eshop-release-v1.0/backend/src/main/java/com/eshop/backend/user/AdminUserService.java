package com.eshop.backend.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.admin.operationlog.OperationLogAction;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.user.dto.AdminUserResponse;
import com.eshop.backend.user.dto.AdminUserSummaryResponse;
import com.eshop.backend.user.dto.ManagedUserCreateRequest;
import com.eshop.backend.user.entity.SysUser;
import com.eshop.backend.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final List<String> MANAGED_ROLES = List.of("USER", "SELLER");

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<AdminUserResponse> page(
            long current, long size, String keyword, String role, String status) {
        validateOptional(role, List.of("USER", "SELLER", "ADMIN"));
        validateOptional(status, List.of("ENABLED", "DISABLED"));
        Page<SysUser> page = userMapper.selectPage(
                new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), 50)),
                new LambdaQueryWrapper<SysUser>()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysUser::getUsername, keyword)
                                .or()
                                .like(SysUser::getNickname, keyword)
                                .or()
                                .like(SysUser::getPhone, keyword))
                        .eq(StringUtils.hasText(role), SysUser::getRole, role)
                        .eq(StringUtils.hasText(status), SysUser::getStatus, status)
                        .orderByDesc(SysUser::getCreatedAt));
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public AdminUserSummaryResponse summary() {
        long buyerCount = countByRole("USER");
        long sellerCount = countByRole("SELLER");
        long enabledCount = countManagedByStatus("ENABLED");
        long disabledCount = countManagedByStatus("DISABLED");
        return new AdminUserSummaryResponse(
                buyerCount + sellerCount,
                buyerCount,
                sellerCount,
                enabledCount,
                disabledCount);
    }

    @Transactional
    @OperationLogAction(module = "账号管理", action = "创建买家或卖家")
    public AdminUserResponse create(ManagedUserCreateRequest request) {
        validateManagedRole(request.role());
        validateOptional(request.status(), List.of("ENABLED", "DISABLED"));
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_LONG);
        }
        String username = request.username().trim();
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        user.setRole(request.role());
        user.setStatus(request.status());
        userMapper.insert(user);
        return toResponse(userMapper.selectById(user.getId()));
    }

    @Transactional
    @OperationLogAction(module = "用户管理", action = "修改用户状态")
    public AdminUserResponse updateStatus(Long id, String status, Long operatorId) {
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (id.equals(operatorId) && "DISABLED".equals(status)) {
            throw new BusinessException(ErrorCode.SELF_DISABLE_NOT_ALLOWED);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        user.setStatus(status);
        userMapper.updateById(user);
        return toResponse(user);
    }

    @Transactional
    @OperationLogAction(module = "账号管理", action = "修改买家或卖家角色")
    public AdminUserResponse updateRole(Long id, String role, Long operatorId) {
        validateManagedRole(role);
        SysUser user = requireUser(id);
        if (id.equals(operatorId)) {
            throw new BusinessException(ErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_PROTECTED);
        }
        user.setRole(role);
        userMapper.updateById(user);
        return toResponse(user);
    }

    private long countByRole(String role) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, role));
        return count == null ? 0L : count;
    }

    private long countManagedByStatus(String status) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, MANAGED_ROLES)
                .eq(SysUser::getStatus, status));
        return count == null ? 0L : count;
    }

    private void validateManagedRole(String role) {
        if (!MANAGED_ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private void validateOptional(String value, List<String> allowed) {
        if (StringUtils.hasText(value) && !allowed.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private AdminUserResponse toResponse(SysUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
