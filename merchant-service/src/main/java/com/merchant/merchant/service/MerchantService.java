package com.merchant.merchant.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.merchant.merchant.dto.MerchantMapper;
import com.merchant.merchant.dto.MerchantRequest;
import com.merchant.merchant.entity.Merchant;
import com.merchant.merchant.entity.MerchantDocument;
import com.merchant.merchant.repository.MerchantDocumentRepository;
import com.merchant.merchant.repository.MerchantRepository;

import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {
    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);
    private final MerchantRepository merchantRepository;
    private final MerchantDocumentRepository documentRepository;
    private final MerchantMapper merchantMapper;
    private final Tracer tracer;
    private final Meter meter;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestsDuration;
    private final LongCounter failureTotal;

    public MerchantService(MerchantRepository merchantRepository, MerchantDocumentRepository documentRepository,
                           MerchantMapper merchantMapper, OpenTelemetry openTelemetry) {
        this.merchantRepository = merchantRepository;
        this.documentRepository = documentRepository;
        this.merchantMapper = merchantMapper;
        this.tracer = openTelemetry.getTracer("merchant-service", "1.0.0");
        this.meter = openTelemetry.getMeter("merchant-service");
        this.requestsTotal = meter.counterBuilder("requests_total").setDescription("Total requests").setUnit("1").build();
        this.requestsDuration = meter.histogramBuilder("requests_duration_seconds").setDescription("Duration").setUnit("s").build();
        this.failureTotal = meter.counterBuilder("failure_total").setDescription("Failures").setUnit("1").build();
    }

    public List<Merchant> getAll() { return merchantRepository.findAll(); }
    public Merchant getById(Long id) { return merchantRepository.findById(id).orElseThrow(() -> new RuntimeException("Merchant not found")); }
    public Merchant create(MerchantRequest req) { return merchantRepository.save(merchantMapper.toEntity(req)); }
    public Merchant update(Long id, MerchantRequest req) {
        Merchant m = getById(id);
        m.setName(req.name()); m.setDescription(req.description()); m.setAddress(req.address());
        m.setContactEmail(req.contactEmail()); m.setContactPhone(req.contactPhone());
        return merchantRepository.save(m);
    }
    public void delete(Long id) { merchantRepository.deleteById(id); }

    // Documents
    public List<MerchantDocument> getDocuments(Long merchantId) { return documentRepository.findByMerchantId(merchantId); }
    public MerchantDocument addDocument(Long merchantId, String docType, String docUrl) {
        MerchantDocument d = new MerchantDocument();
        d.setMerchantId(merchantId); d.setDocumentType(docType); d.setDocumentUrl(docUrl);
        return documentRepository.save(d);
    }
}