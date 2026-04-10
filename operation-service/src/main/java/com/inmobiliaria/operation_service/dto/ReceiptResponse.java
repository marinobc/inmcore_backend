package com.inmobiliaria.operation_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.inmobiliaria.operation_service.model.Receipt;

import lombok.Data;

/**
 * Read-only DTO returned to the client after attaching or listing receipts.
 *
 * The {@code downloadUrl} is a MinIO pre-signed URL valid for 1 hour,
 * generated on-the-fly by
 * {@link com.inmobiliaria.operation_service.service.MinioStorageService}.
 */
@Data
public class ReceiptResponse {

    private String id;
    private String operationId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime paymentDate;
    private String concept;
    private String originalFileName;
    private String contentType;
    private Long fileSizeBytes;
    private String uploadedByAgentId;
    private LocalDateTime uploadedAt;

    /**
     * Pre-signed MinIO URL — frontend uses this to download or preview the file.
     * Never expose the raw object key or internal bucket paths to the client.
     */
    private String downloadUrl;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ReceiptResponse from(Receipt receipt, String downloadUrl) {
        ReceiptResponse dto = new ReceiptResponse();
        dto.id = receipt.getId();
        dto.operationId = receipt.getOperationId();
        dto.amount = receipt.getAmount();
        dto.currency = receipt.getCurrency();
        dto.paymentDate = receipt.getPaymentDate();
        dto.concept = receipt.getConcept();
        dto.originalFileName = receipt.getOriginalFileName();
        dto.contentType = receipt.getContentType();
        dto.fileSizeBytes = receipt.getFileSizeBytes();
        dto.uploadedByAgentId = receipt.getUploadedByAgentId();
        dto.uploadedAt = receipt.getUploadedAt();
        dto.downloadUrl = downloadUrl;
        return dto;
    }
}