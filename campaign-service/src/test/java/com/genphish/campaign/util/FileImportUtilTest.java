package com.genphish.campaign.util;

import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.exception.InvalidOperationException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileImportUtilTest {

    private final FileImportUtil fileImportUtil = new FileImportUtil();

    @Test
    void parseFile_WhenCsvProvided_ShouldParseRowsAndSkipInvalidOnes() {
        UUID companyId = UUID.randomUUID();
        String csv = "firstName,lastName,email,department\n"
                + "John,Doe,john@example.com,IT\n"
                + "Broken,Row,onlythree\n"
                + "Jane,Smith,jane@example.com,HR\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "employees.csv", "text/csv", csv.getBytes()
        );

        List<Employee> employees = fileImportUtil.parseFile(file, companyId);

        assertThat(employees).hasSize(2);
        assertThat(employees.get(0).getCompanyId()).isEqualTo(companyId);
        assertThat(employees.get(0).getEmail()).isEqualTo("john@example.com");
        assertThat(employees.get(1).getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void parseFile_WhenXlsxProvided_ShouldParseRowsAndSkipMissingRequiredFields() throws Exception {
        UUID companyId = UUID.randomUUID();

        byte[] xlsxBytes = createXlsxBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "employees.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes
        );

        List<Employee> employees = fileImportUtil.parseFile(file, companyId);

        assertThat(employees).hasSize(2);
        assertThat(employees.get(0).getFirstName()).isEqualTo("Ali");
        assertThat(employees.get(1).getFirstName()).isEqualTo("42");
        assertThat(employees.get(0).getCompanyId()).isEqualTo(companyId);
    }

    @Test
    void parseFile_WhenUnsupportedExtension_ShouldThrowIllegalArgumentException() {
        UUID companyId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "employees.txt", "text/plain", "a".getBytes());

        assertThatThrownBy(() -> fileImportUtil.parseFile(file, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file format");
    }

    @Test
    void parseFile_WhenFilenameMissing_ShouldThrowIllegalArgumentException() {
        UUID companyId = UUID.randomUUID();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(null);

        assertThatThrownBy(() -> fileImportUtil.parseFile(file, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File name is required");
    }

    @Test
    void parseFile_WhenCsvReadFails_ShouldThrowInvalidOperationException() throws Exception {
        UUID companyId = UUID.randomUUID();
        MultipartFile file = mock(MultipartFile.class);

        when(file.getOriginalFilename()).thenReturn("employees.csv");
        when(file.getInputStream()).thenThrow(new IOException("Disk error"));

        assertThatThrownBy(() -> fileImportUtil.parseFile(file, companyId))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Failed to parse CSV file");
    }

    private byte[] createXlsxBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Employees");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("firstName");
            header.createCell(1).setCellValue("lastName");
            header.createCell(2).setCellValue("email");
            header.createCell(3).setCellValue("department");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Ali");
            row1.createCell(1).setCellValue("Kaya");
            row1.createCell(2).setCellValue("ali@example.com");
            row1.createCell(3).setCellValue("IT");

            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("");
            row2.createCell(1).setCellValue("Missing");
            row2.createCell(2).setCellValue("missing@example.com");
            row2.createCell(3).setCellValue("HR");

            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue(42);
            row3.createCell(1).setCellValue("Numeric");
            row3.createCell(2).setCellValue("numeric@example.com");
            row3.createCell(3).setCellValue("Ops");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
