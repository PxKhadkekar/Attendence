package com.college.placement.attendance.service.excel;

import com.college.placement.attendance.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

@Slf4j
@Component
public class ExcelSecurityValidator {

    private static final byte[] OOXML_MAGIC_BYTES = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final String ALLOWED_EXTENSION = ".xlsx";
    private static final String ALLOWED_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final long maxFileSizeBytes;

    public ExcelSecurityValidator(@Value("${app.excel.max-file-size:5242880}") long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        // Global Zip-Bomb defense in Apache POI
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(50_000_000L);
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        // 1. File Size Verification
        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("File size exceeds maximum limit of 5 MB (" + file.getSize() + " bytes)");
        }

        // 2. Extension Verification
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(ALLOWED_EXTENSION)) {
            throw new BadRequestException("Only valid .xlsx files are accepted. .xls, .xlsm, .csv and other formats are strictly rejected.");
        }

        // Reject dangerous extensions like .xlsm disguised in name
        if (originalFilename.toLowerCase().endsWith(".xlsm") || originalFilename.toLowerCase().endsWith(".xls")) {
            throw new BadRequestException("Macro-enabled (.xlsm) and legacy (.xls) files are not allowed.");
        }

        // 3. MIME Type Verification (allowing standard or generic octet-stream only if magic bytes match)
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase(ALLOWED_MIME)
                && !contentType.equalsIgnoreCase("application/octet-stream")
                && !contentType.equalsIgnoreCase("application/x-zip-compressed")) {
            throw new BadRequestException("Invalid MIME type: " + contentType + ". Expected " + ALLOWED_MIME);
        }

        // 4. Magic Bytes Verification (PK\x03\x04)
        try (InputStream is = file.getInputStream()) {
            byte[] headerBytes = new byte[4];
            int read = is.read(headerBytes);
            if (read < 4 || !Arrays.equals(headerBytes, OOXML_MAGIC_BYTES)) {
                throw new BadRequestException("Invalid file format. File does not contain valid OOXML ZIP magic bytes.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to read file header: " + e.getMessage());
        }

        // 5. Inspect OOXML package for macros and dangerous content
        try (InputStream is = file.getInputStream()) {
            byte[] fileBytes = is.readAllBytes();
            try (OPCPackage opcPackage = OPCPackage.open(new ByteArrayInputStream(fileBytes))) {
                for (PackagePart part : opcPackage.getParts()) {
                    String partName = part.getPartName().getName().toLowerCase();
                    String partType = part.getContentType().toLowerCase();

                    if (partName.contains("vbaproject.bin") || partType.contains("macroenabled") || partType.contains("vba")) {
                        throw new BadRequestException("Malicious file rejected: Excel file contains macros or VBA projects.");
                    }
                }
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Corrupt or invalid OOXML workbook structure: " + e.getMessage());
        }
    }

    /**
     * Sanitizes string cell values against CSV/Formula Injection (DDE attacks).
     * If value starts with '=', '+', '-', or '@', prefixes with a single quote (').
     */
    public static String escapeForExport(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char firstChar = value.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
            return "'" + value;
        }
        return value;
    }
}
