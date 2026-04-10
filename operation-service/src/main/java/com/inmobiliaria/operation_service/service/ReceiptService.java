package com.inmobiliaria.operation_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.inmobiliaria.operation_service.dto.ReceiptResponse;
import com.inmobiliaria.operation_service.dto.ReceiptUploadRequest;
import com.inmobiliaria.operation_service.model.Receipt;
import com.inmobiliaria.operation_service.repository.ReceiptRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Business logic for receipt management.
 *
 * Covers all three operations required by the User Story:
 * - attachReceipt → validate, upload to MinIO, persist metadata in MongoDB
 * - listReceipts → fetch all receipts for an operation with download URLs
 * - deleteReceipt → remove file from MinIO and document from MongoDB
 */
@Slf4j
@Service
public class ReceiptService {

        private final ReceiptRepository receiptRepository;
        private final MinioStorageService storageService;

        public ReceiptService(ReceiptRepository receiptRepository,
                        MinioStorageService storageService) {
                this.receiptRepository = receiptRepository;
                this.storageService = storageService;
        }

        // ─────────────────────────────────────────────────────────────────────────
        // ATTACH RECEIPT
        // POST /api/operations/{operationId}/receipts
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Validates and attaches a payment receipt to an operation.
         *
         * Flow:
         * 1. Validate file type and size (delegated to MinioStorageService).
         * 2. Upload the file to MinIO — get back the object key.
         * 3. Persist the receipt metadata in MongoDB.
         * 4. Return the saved receipt with a pre-signed download URL.
         *
         * @param operationId The operation to attach the receipt to
         * @param agentId     ID of the authenticated agent (from JWT via gateway)
         * @param file        The uploaded file (PDF / image)
         * @param request     Form-data fields: amount, currency, paymentDate, concept
         * @return The persisted receipt with a download URL
         * @throws IllegalArgumentException if the file type or size is invalid (PA2)
         */
        public ReceiptResponse attachReceipt(String operationId,
                        String agentId,
                        MultipartFile file,
                        ReceiptUploadRequest request) {
                // 1. Validate (throws IllegalArgumentException on bad type/size)
                storageService.validateFile(file);

                // 2. Upload to MinIO
                String fileKey = storageService.uploadFile(file, operationId);
                log.info("[ReceiptService] File uploaded to MinIO: key='{}'", fileKey);

                // 3. Build and persist the Receipt document
                Receipt receipt = Receipt.builder()
                                .operationId(operationId)
                                .amount(request.getAmount())
                                .currency(request.getCurrency())
                                .paymentDate(request.getPaymentDate())
                                .concept(request.getConcept())
                                .fileKey(fileKey)
                                .originalFileName(file.getOriginalFilename())
                                .contentType(file.getContentType())
                                .fileSizeBytes(file.getSize())
                                .uploadedByAgentId(agentId)
                                .uploadedAt(LocalDateTime.now())
                                .build();

                receipt = receiptRepository.save(receipt);
                log.info("[ReceiptService] Receipt saved: id='{}', operation='{}'",
                                receipt.getId(), operationId);

                // 4. Generate pre-signed URL and return response
                String downloadUrl = storageService.generatePresignedUrl(fileKey);
                return ReceiptResponse.from(receipt, downloadUrl);
        }

        // ─────────────────────────────────────────────────────────────────────────
        // LIST RECEIPTS
        // GET /api/operations/{operationId}/receipts
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Returns all receipts attached to an operation, each with a fresh
         * pre-signed download URL valid for 1 hour.
         *
         * @param operationId The operation to query
         * @return List of ReceiptResponse DTOs ordered by upload date descending
         */
        public List<ReceiptResponse> listReceipts(String operationId) {
                return receiptRepository
                                .findByOperationIdOrderByUploadedAtDesc(operationId)
                                .stream()
                                .map(r -> ReceiptResponse.from(r, storageService.generatePresignedUrl(r.getFileKey())))
                                .collect(Collectors.toList());
        }

        // ─────────────────────────────────────────────────────────────────────────
        // DELETE RECEIPT
        // DELETE /api/operations/{operationId}/receipts/{receiptId}
        // ─────────────────────────────────────────────────────────────────────────

        /**
         * Deletes a receipt from MongoDB and removes its file from MinIO.
         *
         * The ownership check (receipt belongs to operationId) is performed here
         * to prevent an agent from deleting receipts of other operations by
         * manipulating the URL path.
         *
         * @param operationId The operation the receipt must belong to
         * @param receiptId   The receipt to delete
         * @throws RuntimeException if the receipt is not found or does not belong
         *                          to the specified operation
         */
        public void deleteReceipt(String operationId, String receiptId) {
                // 1. Fetch the receipt
                Receipt receipt = receiptRepository.findById(receiptId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Receipt not found: " + receiptId));

                // 2. Verify it belongs to the specified operation
                if (!receipt.getOperationId().equals(operationId)) {
                        throw new RuntimeException(
                                        "Receipt '" + receiptId + "' does not belong to operation '" + operationId
                                                        + "'.");
                }

                // 3. Remove file from MinIO (fail-silent if file is already gone)
                storageService.deleteFile(receipt.getFileKey());

                // 4. Remove document from MongoDB
                receiptRepository.deleteById(receiptId);
                log.info("[ReceiptService] Receipt deleted: id='{}', operation='{}'",
                                receiptId, operationId);
        }
}