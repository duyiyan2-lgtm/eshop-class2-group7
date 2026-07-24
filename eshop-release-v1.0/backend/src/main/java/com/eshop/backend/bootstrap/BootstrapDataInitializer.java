package com.eshop.backend.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.backend.user.entity.SysUser;
import com.eshop.backend.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapDataInitializer implements CommandLineRunner {
    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    @Override
    public void run(String... args) {
        validateProperties();
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, properties.username()));
        if (count != null && count > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(properties.username());
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setNickname(properties.nickname());
        admin.setRole("ADMIN");
        admin.setStatus("ENABLED");
        userMapper.insert(admin);
        log.warn("Development bootstrap administrator '{}' was created; disable bootstrap after initialization.",
                properties.username());
    }

    private void validateProperties() {
        if (!StringUtils.hasText(properties.username())
                || !StringUtils.hasText(properties.password())
                || !StringUtils.hasText(properties.nickname())) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_USERNAME, BOOTSTRAP_ADMIN_PASSWORD and BOOTSTRAP_ADMIN_NICKNAME are required "
                            + "when BOOTSTRAP_ADMIN_ENABLED=true");
        }
        if (properties.password().length() < 8) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must contain at least 8 characters");
        }
    }
}
