package com.eshop.backend.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.backend.auth.dto.AuthResponse;
import com.eshop.backend.auth.dto.CurrentUserResponse;
import com.eshop.backend.auth.dto.LoginRequest;
import com.eshop.backend.auth.dto.RegisterRequest;
import com.eshop.backend.auth.dto.UserProfileRequest;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import com.eshop.backend.security.JwtTokenService;
import com.eshop.backend.security.LoginUser;
import com.eshop.backend.user.entity.SysUser;
import com.eshop.backend.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public void register(RegisterRequest request) {
        validatePasswordLength(request.password());
        String role = request.role() == null ? "USER" : request.role();
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setRole(role);
        // Buyers can use the shop immediately. Merchant self-registration requires platform approval
        // before the account can access shared catalog, inventory and order data.
        user.setStatus("SELLER".equals(role) ? "DISABLED" : "ENABLED");
        userMapper.insert(user);
    }

    public AuthResponse login(LoginRequest request) {
        validatePasswordLength(request.password());
        SysUser account = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username()));
        if (account == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!"ENABLED".equals(account.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            LoginUser user = (LoginUser) authentication.getPrincipal();
            return new AuthResponse(user.getUserId(), user.getUsername(), user.getNickname(), user.getRole(),
                    jwtTokenService.createToken(user));
        } catch (BusinessException exception) {
            throw exception;
        } catch (BadCredentialsException exception) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        } catch (AuthenticationException exception) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }

    public CurrentUserResponse currentUser(LoginUser user) {
        return toCurrentUser(requireUser(user.getUserId()));
    }

    @Transactional
    public CurrentUserResponse updateProfile(LoginUser loginUser, UserProfileRequest request) {
        SysUser user = requireUser(loginUser.getUserId());
        user.setNickname(request.nickname().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        userMapper.updateById(user);
        return toCurrentUser(user);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private CurrentUserResponse toCurrentUser(SysUser user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getRole());
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_LONG);
        }
    }
}
