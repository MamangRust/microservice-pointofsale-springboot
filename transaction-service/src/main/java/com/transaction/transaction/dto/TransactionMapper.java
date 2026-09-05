package com.transaction.transaction.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;

import com.transaction.transaction.entity.Transaction;

@Mapper(componentModel = ComponentModel.SPRING)
public interface TransactionMapper {
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "changeAmount", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Transaction toEntity(TransactionRequest request);

    TransactionResponse toResponse(Transaction transaction);
}