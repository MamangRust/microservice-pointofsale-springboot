package com.cashier.cashier.dto;

import com.cashier.cashier.entity.Cashier;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:18:48+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class CashierMapperImpl implements CashierMapper {

    @Override
    public Cashier toEntity(CashierRequest cashierRequest) {
        if ( cashierRequest == null ) {
            return null;
        }

        Cashier cashier = new Cashier();

        cashier.setMerchantId( cashierRequest.merchantId() );
        cashier.setName( cashierRequest.name() );
        cashier.setUserId( cashierRequest.userId() );

        return cashier;
    }

    @Override
    public CashierResponse toResponse(Cashier cashier) {
        if ( cashier == null ) {
            return null;
        }

        Long cashierId = null;
        Long merchantId = null;
        Long userId = null;
        String name = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        cashierId = cashier.getCashierId();
        merchantId = cashier.getMerchantId();
        userId = cashier.getUserId();
        name = cashier.getName();
        createdAt = cashier.getCreatedAt();
        updatedAt = cashier.getUpdatedAt();

        CashierResponse cashierResponse = new CashierResponse( cashierId, merchantId, userId, name, createdAt, updatedAt );

        return cashierResponse;
    }
}
