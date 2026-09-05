package com.merchant.merchant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.merchant.merchant.entity.MerchantDocument;

import java.util.List;

public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument, Long> {
    List<MerchantDocument> findByMerchantId(Long merchantId);
}
