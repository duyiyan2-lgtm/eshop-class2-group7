package com.eshop.backend.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.admin.operationlog.OperationLogAction;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.user.dto.AdminUserResponse;
import com.eshop.backend.user.entity.SysUser;
import com.eshop.backend.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final SysUserMapper userMapper;

    public PageResult<AdminUserResponse> page(
            long current, long size, String keyword, String role, String status) {
        validateOptional(role, List.of("USER", "ADMIN"));
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
        user.setStatus(status);
        userMapper.updateById(user);
        return toResponse(user);
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
