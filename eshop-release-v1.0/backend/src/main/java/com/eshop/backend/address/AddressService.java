package com.eshop.backend.address;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eshop.backend.common.BusinessException;
import com.eshop.backend.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final UserAddressMapper addressMapper;

    public List<UserAddress> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getUpdatedAt));
    }

    @Transactional
    public UserAddress create(Long userId, AddressRequest request) {
        long existing = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            clearDefault(userId);
        }
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        copy(address, request);
        address.setIsDefault(makeDefault);
        addressMapper.insert(address);
        return address;
    }

    @Transactional
    public UserAddress update(Long userId, Long id, AddressRequest request) {
        UserAddress address = requireOwned(userId, id);
        Boolean requestedDefault = request.isDefault();
        if (Boolean.TRUE.equals(requestedDefault)) {
            clearDefault(userId);
        }
        copy(address, request);
        if (requestedDefault != null) {
            address.setIsDefault(requestedDefault);
        }
        addressMapper.updateById(address);
        return address;
    }

    @Transactional
    public UserAddress setDefault(Long userId, Long id) {
        UserAddress address = requireOwned(userId, id);
        clearDefault(userId);
        address.setIsDefault(true);
        addressMapper.updateById(address);
        return address;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserAddress address = requireOwned(userId, id);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        addressMapper.deleteById(id);
        if (wasDefault) {
            UserAddress replacement = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getUpdatedAt)
                    .last("LIMIT 1"));
            if (replacement != null) {
                replacement.setIsDefault(true);
                addressMapper.updateById(replacement);
            }
        }
    }

    public UserAddress requireOwned(Long userId, Long id) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new BusinessException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    private void clearDefault(Long userId) {
        UserAddress update = new UserAddress();
        update.setIsDefault(false);
        addressMapper.update(update, new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, true));
    }

    private void copy(UserAddress address, AddressRequest request) {
        address.setReceiverName(request.receiverName().trim());
        address.setPhone(request.phone().trim());
        address.setProvince(request.province().trim());
        address.setCity(request.city().trim());
        address.setDistrict(request.district().trim());
        address.setDetail(request.detail().trim());
    }
}
