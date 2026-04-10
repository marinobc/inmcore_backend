package com.inmobiliaria.operation_service.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a payment receipt (comprobante de pago) attached to an operation.
 *
 * The actual file is stored in MinIO; this document holds metadata only.
 * The {@code fileUrl} field contains the MinIO presigned URL or the
 * internal object key, depending on your download strategy.
 *
 * Collection: receipts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "receipts")
public class Receipt {

    @Id
    private String id;

    /** ID of the operation this receipt belongs to */
    @Indexed
    private String operationId;

    /** Payment amount registered on the receipt */
    private BigDecimal amount;

    /**
     * ISO 4217 currency code.
     * E.g.: "BOB" (Boliviano), "USD", "EUR"
     */
    private String currency;

    /** Date the payment was made (as declared by the agent) */
    private LocalDateTime paymentDate;

    /**
     * MinIO object key for the stored file.
     * Format: receipts/{operationId}/{uuid}.{ext}
     */
    private String fileKey;

    /**
     * Original file name as uploaded by the agent.
     * Stored for display purposes in the UI.
     */
    private String originalFileName;

    /**
     * MIME type of the uploaded file.
     * Allowed: application/pdf, image/jpeg, image/png, image/webp
     */
    private String contentType;

    /** File size in bytes */
    private Long fileSizeBytes;

    /**
     * Human-readable concept / description of the receipt.
     * E.g.: "Reserva inicial", "Pago cuota 1"
     */
    private String concept;

    /** ID of the agent who uploaded this receipt */
    private String uploadedByAgentId;

    /** Timestamp when the receipt was attached to the operation */
    private LocalDateTime uploadedAt;
}