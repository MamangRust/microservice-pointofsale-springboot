package com.merchant.merchant.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;

import com.merchant.merchant.entity.Merchant;

@Mapper(componentModel = ComponentModel.SPRING)
public interface MerchantMapper {
    @Mapping(target = "merchantId", ignore = true)
    @Mapping(target = "merchantNo", ignore = true)
    @Mapping(target = "apiKey", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Merchant toEntity(MerchantRequest request);

    MerchantResponse toResponse(Merchant merchant);
}