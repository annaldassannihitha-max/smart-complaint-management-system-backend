
package com.smartcomplaint.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartcomplaint.model.Complaint;
import com.smartcomplaint.model.ComplaintStatus;
import com.smartcomplaint.repository.ComplaintRepository;

@RestController
@RequestMapping("/api/complaints")
@CrossOrigin(origins = "*")
public class ComplaintController {

    private static final Logger logger =
            LoggerFactory.getLogger(ComplaintController.class);

    private static final long MAX_PROOF_SIZE =
            10L * 1024L * 1024L;

    private final ComplaintRepository complaintRepository;

    public ComplaintController(
            ComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    // =========================================================
    // CREATE COMPLAINT
    // POST /api/complaints
    // Content-Type: multipart/form-data
    // =========================================================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createComplaint(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String contact,
            @RequestParam(required = false) String latitude,
            @RequestParam(required = false) String longitude,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) MultipartFile proof) {

        // Validate required fields.
        if (userId == null || userId.isBlank()) {
            return badRequest("User ID is required.");
        }

        if (type == null || type.isBlank()) {
            return badRequest("Complaint type is required.");
        }

        if (description == null || description.isBlank()) {
            return badRequest("Complaint description is required.");
        }

        try {
            Complaint complaint = new Complaint();

            // Generate a complaint ID, for example CMP-A1B2C3D4.
            String complaintId = generateComplaintId();
            complaint.setComplaintId(complaintId);

            complaint.setUserId(userId.trim());
            complaint.setType(type.trim());
            complaint.setDescription(description.trim());
            complaint.setContact(
                    contact == null || contact.isBlank()
                            ? null
                            : contact.trim());

            // Validate and save the location.
            if ((latitude == null || latitude.isBlank())
                    != (longitude == null || longitude.isBlank())) {
                return badRequest(
                        "Both latitude and longitude must be provided together.");
            }

            if (latitude != null && !latitude.isBlank()) {
                double lat;
                double lon;

                try {
                    lat = Double.parseDouble(latitude.trim());
                    lon = Double.parseDouble(longitude.trim());
                } catch (NumberFormatException ex) {
                    return badRequest(
                            "Latitude and longitude must be valid numbers.");
                }

                if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
                    return badRequest(
                            "Latitude must be between -90 and 90.");
                }

                if (!Double.isFinite(lon) || lon < -180 || lon > 180) {
                    return badRequest(
                            "Longitude must be between -180 and 180.");
                }

                complaint.setLatitude(lat);
                complaint.setLongitude(lon);
            }

            complaint.setAddress(
                    address == null || address.isBlank()
                            ? null
                            : address.trim());

            // New complaints start as PENDING.
            complaint.setStatus(ComplaintStatus.PENDING);

            // Validate and save the proof image, if supplied.
            if (proof != null && !proof.isEmpty()) {
                if (proof.getSize() > MAX_PROOF_SIZE) {
                    return badRequest(
                            "Proof image must be smaller than 10 MB.");
                }

                String contentType = proof.getContentType();

                if (contentType == null
                        || !contentType.toLowerCase()
                                .startsWith("image/")) {
                    return badRequest(
                            "Only image files are allowed as proof.");
                }

                // Store only the original filename, not a supplied path.
                String originalFilename = proof.getOriginalFilename();
                String safeFilename = originalFilename == null
                        ? "complaint-proof"
                        : originalFilename.replace('\\', '/');

                safeFilename = safeFilename.substring(
                        safeFilename.lastIndexOf('/') + 1);

                complaint.setProofFileName(safeFilename);
                complaint.setProofContentType(contentType);
                complaint.setProofImage(proof.getBytes());
            }

            // Persist the complaint before returning its ID.
            Complaint savedComplaint =
                    complaintRepository.saveAndFlush(complaint);

            logger.info(
                    "Created complaint {} for user {}",
                    savedComplaint.getComplaintId(),
                    savedComplaint.getUserId());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(savedComplaint);

        } catch (Exception ex) {
            logger.error("Unable to create complaint", ex);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to create complaint. Check the backend log for details."));
        }
    }

    private String generateComplaintId() {
        return "CMP-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private ResponseEntity<Map<String, String>> badRequest(
            String message) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("message", message));
    }

    // =========================================================
    // GET ALL COMPLAINTS
    // GET /api/complaints
    // =========================================================

    @GetMapping
    public ResponseEntity<List<Complaint>> getAllComplaints() {
        return ResponseEntity.ok(complaintRepository.findAll());
    }

    // =========================================================
    // GET COMPLAINTS FOR A USER
    // GET /api/complaints/user/{userId}
    // =========================================================

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Complaint>> getUserComplaints(
            @PathVariable String userId) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<Complaint> complaints =
                complaintRepository.findByUserIdOrderByCreatedAtDesc(
                        userId.trim());

        return ResponseEntity.ok(complaints);
    }

    // =========================================================
    // GET ONE COMPLAINT BY ITS PUBLIC ID
    // GET /api/complaints/{complaintId}
    // =========================================================

    @GetMapping("/{complaintId}")
    public ResponseEntity<?> getComplaint(
            @PathVariable String complaintId) {

        return complaintRepository
                .findByComplaintId(complaintId)
                .<ResponseEntity<?>>map(
                        complaint -> ResponseEntity.ok(complaint))
                .orElseGet(
                        () -> ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(Map.of(
                                        "message",
                                        "Complaint not found.")));
    }

    // =========================================================
    // UPDATE COMPLAINT
    // PATCH /api/complaints/{complaintId}
    // =========================================================

    @PatchMapping("/{complaintId}")
    public ResponseEntity<?> updateComplaint(
            @PathVariable String complaintId,
            @RequestBody Map<String, Object> body) {

        try {
            Complaint complaint = complaintRepository
                    .findByComplaintId(complaintId)
                    .orElse(null);

            if (complaint == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Complaint not found."));
            }

            if (body == null) {
                return badRequest("Request body is required.");
            }

            // Update status if supplied.
            if (body.containsKey("status")) {
                Object statusValue = body.get("status");

                if (statusValue != null) {
                    String statusText =
                            statusValue.toString().trim().toUpperCase();

                    try {
                        ComplaintStatus newStatus =
                                ComplaintStatus.valueOf(statusText);

                        complaint.setStatus(newStatus);

                    } catch (IllegalArgumentException ex) {
                        return ResponseEntity
                                .badRequest()
                                .body(Map.of(
                                        "message",
                                        "Invalid complaint status.",
                                        "allowed",
                                        List.of(
                                                "PENDING",
                                                "IN_PROGRESS",
                                                "RESOLVED",
                                                "REJECTED")));
                    }
                }
            }

            // Assign staff if supplied.
            if (body.containsKey("assignedStaff")) {
                Object staffValue = body.get("assignedStaff");

                complaint.setAssignedStaff(
                        staffValue == null
                                ? null
                                : staffValue.toString().trim());
            }

            Complaint updatedComplaint =
                    complaintRepository.saveAndFlush(complaint);

            return ResponseEntity.ok(updatedComplaint);

        } catch (Exception ex) {
            logger.error(
                    "Unable to update complaint {}",
                    complaintId,
                    ex);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "message",
                            "Unable to update complaint. Check the backend log for details."));
        }
    }

    // =========================================================
    // GET PROOF IMAGE
    // GET /api/complaints/{complaintId}/proof
    // =========================================================

    @GetMapping("/{complaintId}/proof")
    public ResponseEntity<byte[]> getProof(
            @PathVariable String complaintId) {

        Complaint complaint = complaintRepository
                .findByComplaintId(complaintId)
                .orElse(null);

        if (complaint == null
                || complaint.getProofImage() == null
                || complaint.getProofImage().length == 0) {
            return ResponseEntity.<byte[]>notFound().build();
        }

        MediaType mediaType = MediaType.IMAGE_JPEG;
        String contentType = complaint.getProofContentType();

        if (contentType != null && !contentType.isBlank()) {
            try {
                MediaType parsedType =
                        MediaType.parseMediaType(contentType);

                if ("image".equalsIgnoreCase(parsedType.getType())) {
                    mediaType = parsedType;
                }
            } catch (IllegalArgumentException ex) {
                logger.warn(
                        "Invalid proof image content type for complaint {}",
                        complaintId);
            }
        }

        String fileName = complaint.getProofFileName();

        if (fileName == null || fileName.isBlank()) {
            fileName = "complaint-proof.jpg";
        }

        // Prevent quotes and path separators in the response filename.
        fileName = fileName
                .replace("\"", "")
                .replace("\\", "/");

        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName + "\"")
                .contentType(mediaType)
                .body(complaint.getProofImage());
    }
}