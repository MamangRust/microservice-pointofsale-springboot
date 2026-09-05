package com.merchant.merchant.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.merchant.merchant.entity.Merchant;

import java.util.List;
import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    Optional<Merchant> findByMerchantNo(String merchantNo);
    Optional<Merchant> findByApiKey(String apiKey);
    List<Merchant> findByUserId(Long userId);
}
