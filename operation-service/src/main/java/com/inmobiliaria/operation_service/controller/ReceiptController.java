package com.inmobiliaria.operation_service.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.inmobiliaria.operation_service.dto.ReceiptResponse;
import com.inmobiliaria.operation_service.dto.ReceiptUploadRequest;
import com.inmobiliaria.operation_service.service.ReceiptService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for payment receipt management.
 *
 * All endpoints are scoped to a specific operation via the path variable
 * {operationId}.
 *
 * Endpoints:
 * POST /api/operations/{operationId}/receipts
 * → Attach a receipt (multipart/form-data: file + metadata fields)
 *
 * GET /api/operations/{operationId}/receipts
 * → List all receipts for an operation
 *
 * DELETE /api/operations/{operationId}/receipts/{receiptId}
 * → Remove a receipt and its file from MinIO
 *
 * The authenticated agent's ID is read from the X-User-Id header,
 * injected by the API Gateway's AuthenticationFilter after JWT validation
 * (same pattern as the visit-calendar-service).
 */
@Slf4j
@RestController
@RequestMapping("/api/operations/{operationId}/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/operations/{operationId}/receipts
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attaches a payment receipt to an operation.
     *
     * Request: multipart/form-data
     * - file → PDF / JPEG / PNG / WebP (max 10 MB)
     * - amount → decimal (e.g. "1500.00")
     * - currency → ISO 4217 (e.g. "BOB", "USD")
     * - paymentDate → ISO-8601 datetime (e.g. "2025-06-15T10:30:00")
     * - concept → short description (e.g. "Reserva inicial")
     *
     * @param operationId The operation to attach the receipt to
     * @param agentId     Authenticated agent ID (injected by API Gateway)
     * @param file        The receipt file
     * @param amount      Payment amount (form field)
     * @param currency    ISO currency code (form field)
     * @param paymentDate ISO-8601 payment date (form field)
     * @param concept     Payment concept (form field)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> attachReceipt(
            @PathVariable String operationId,
            @RequestHeader("X-User-Id") String agentId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("amount") String amount,
            @RequestPart("currency") String currency,
            @RequestPart("paymentDate") String paymentDate,
            @RequestPart("concept") String concept) {

        try {
            // Build and validate the request DTO from individual form parts
            ReceiptUploadRequest request = new ReceiptUploadRequest();
            request.setAmount(new java.math.BigDecimal(amount));
            request.setCurrency(currency);
            request.setPaymentDate(java.time.LocalDateTime.parse(paymentDate));
            request.setConcept(concept);

            ReceiptResponse response = receiptService.attachReceipt(operationId, agentId, file, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            // Triggered by file type/size validation — PA2
            log.warn("[ReceiptController] Invalid file upload attempt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("[ReceiptController] Failed to attach receipt: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload receipt. Please try again."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/operations/{operationId}/receipts
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all receipts attached to the given operation.
     * Each receipt includes a pre-signed MinIO download URL (valid 1 hour).
     *
     * @param operationId The operation to query
     */
    @GetMapping
    public ResponseEntity<List<ReceiptResponse>> listReceipts(
            @PathVariable String operationId) {

        List<ReceiptResponse> receipts = receiptService.listReceipts(operationId);
        return ResponseEntity.ok(receipts);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/operations/{operationId}/receipts/{receiptId}
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a receipt from MongoDB and removes the file from MinIO.
     *
     * @param operationId The operation the receipt must belong to
     * @param receiptId   The receipt to delete
     */
    @DeleteMapping("/{receiptId}")
    public ResponseEntity<?> deleteReceipt(
            @PathVariable String operationId,
            @PathVariable String receiptId) {

        try {
            receiptService.deleteReceipt(operationId, receiptId);
            return ResponseEntity.ok(Map.of("message", "Receipt deleted successfully."));

        } catch (RuntimeException e) {
            log.warn("[ReceiptController] Delete failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}