package com.eshop.backend.admin.operationlog;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eshop.backend.common.PageResult;
import com.eshop.backend.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationLogService {
    private final OperationLogMapper operationLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LoginUser operator, String module, String action, String requestUri,
                       boolean success, String message) {
        OperationLog log = new OperationLog();
        if (operator != null) {
            log.setOperatorId(operator.getUserId());
            log.setOperatorName(operator.getUsername());
        }
        log.setModule(module);
        log.setAction(action);
        log.setRequestUri(requestUri);
        log.setSuccess(success);
        log.setMessage(message);
        operationLogMapper.insert(log);
    }

    public PageResult<OperationLog> page(long current, long size) {
        Page<OperationLog> page = operationLogMapper.selectPage(
                new Page<>(current, Math.min(Math.max(size, 1), 100)),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<OperationLog>lambdaQuery()
                        .orderByDesc(OperationLog::getCreatedAt));
        return PageResult.from(page);
    }
}
