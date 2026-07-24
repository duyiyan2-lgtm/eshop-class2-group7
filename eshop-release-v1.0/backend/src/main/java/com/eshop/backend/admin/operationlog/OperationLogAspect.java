package com.eshop.backend.admin.operationlog;

import com.eshop.backend.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {
    private final OperationLogService operationLogService;
    private final HttpServletRequest request;

    @Around("@annotation(action)")
    public Object record(ProceedingJoinPoint joinPoint, OperationLogAction action) throws Throwable {
        LoginUser operator = currentUser();
        try {
            Object result = joinPoint.proceed();
            safeRecord(operator, action, true, "成功");
            return result;
        } catch (Throwable throwable) {
            safeRecord(operator, action, false, "失败：" + throwable.getClass().getSimpleName());
            throw throwable;
        }
    }

    private void safeRecord(LoginUser operator, OperationLogAction action, boolean success, String message) {
        try {
            operationLogService.record(
                    operator,
                    action.module(),
                    action.action(),
                    request.getRequestURI(),
                    success,
                    message);
        } catch (RuntimeException exception) {
            log.error("Failed to persist operation log for {}:{}", action.module(), action.action(), exception);
        }
    }

    private LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        return null;
    }
}
