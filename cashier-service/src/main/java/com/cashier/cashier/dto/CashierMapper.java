package com.cashier.cashier.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;

import com.cashier.cashier.entity.Cashier;

@Mapper(componentModel = ComponentModel.SPRING)
public interface CashierMapper {
    @Mapping(target = "cashierId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Cashier toEntity(CashierRequest cashierRequest);

    CashierResponse toResponse(Cashier cashier);
}
