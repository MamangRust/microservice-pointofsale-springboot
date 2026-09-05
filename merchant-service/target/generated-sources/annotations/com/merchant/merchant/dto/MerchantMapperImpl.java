package com.merchant.merchant.dto;

import com.merchant.merchant.entity.Merchant;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:18:56+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class MerchantMapperImpl implements MerchantMapper {

    @Override
    public Merchant toEntity(MerchantRequest request) {
        if ( request == null ) {
            return null;
        }

        Merchant merchant = new Merchant();

        merchant.setAddress( request.address() );
        merchant.setContactEmail( request.contactEmail() );
        merchant.setContactPhone( request.contactPhone() );
        merchant.setDescription( request.description() );
        merchant.setName( request.name() );

        merchant.setStatus( "PENDING" );

        return merchant;
    }

    @Override
    public MerchantResponse toResponse(Merchant merchant) {
        if ( merchant == null ) {
            return null;
        }

        Long merchantId = null;
        Long userId = null;
        String merchantNo = null;
        String apiKey = null;
        String name = null;
        String description = null;
        String address = null;
        String contactEmail = null;
        String contactPhone = null;
        String status = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        merchantId = merchant.getMerchantId();
        userId = merchant.getUserId();
        merchantNo = merchant.getMerchantNo();
        apiKey = merchant.getApiKey();
        name = merchant.getName();
        description = merchant.getDescription();
        address = merchant.getAddress();
        contactEmail = merchant.getContactEmail();
        contactPhone = merchant.getContactPhone();
        status = merchant.getStatus();
        createdAt = merchant.getCreatedAt();
        updatedAt = merchant.getUpdatedAt();

        MerchantResponse merchantResponse = new MerchantResponse( merchantId, userId, merchantNo, apiKey, name, description, address, contactEmail, contactPhone, status, createdAt, updatedAt );

        return merchantResponse;
    }
}
