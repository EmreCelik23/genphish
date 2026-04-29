package com.genphish.campaign.util;

import com.genphish.campaign.entity.Employee;
import com.genphish.campaign.exception.InvalidOperationException;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class FileImportUtil {

    /**
     * Parses CSV or XLSX file and returns a list of Employee entities.
     * Expected columns: firstName, lastName, email, department
     */
    public List<Employee> parseFile(MultipartFile file, UUID companyId) {
        String filename = file.getOriginalFilename();

        if (filename == null) {
            throw new IllegalArgumentException("File name is required");
        }

        if (filename.endsWith(".csv")) {
            return parseCsv(file, companyId);
        } else if (filename.endsWith(".xlsx")) {
            return parseXlsx(file, companyId);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Please upload CSV or XLSX files.");
        }
    }

    // ── CSV parsing with OpenCSV ──
    private List<Employee> parseCsv(MultipartFile file, UUID companyId) {
        List<Employee> employees = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();

            // Skip header row, only process rows with enough columns
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length >= 4) {
                    employees.add(Employee.builder()
                            .companyId(companyId)
                            .firstName(row[0].trim())
                            .lastName(row[1].trim())
                            .email(row[2].trim())
                            .department(row[3].trim())
                            .build());
                } else {
                    log.warn("Skipping CSV row {} — insufficient columns", i);
                }
            }
        } catch (IOException | CsvException e) {
            log.error("Failed to parse CSV file: {}", e.getMessage());
            throw new InvalidOperationException("Failed to parse CSV file: " + e.getMessage());
        }

        log.info("Parsed {} employees from CSV", employees.size());
        return employees;
    }

    // ── XLSX parsing with Apache POI ──
    private List<Employee> parseXlsx(MultipartFile file, UUID companyId) {
        List<Employee> employees = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Skip header row (index 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    String firstName = getCellValue(row.getCell(0));
                    String lastName = getCellValue(row.getCell(1));
                    String email = getCellValue(row.getCell(2));
                    String department = getCellValue(row.getCell(3));

                    if (!firstName.isEmpty() && !email.isEmpty()) {
                        employees.add(Employee.builder()
                                .companyId(companyId)
                                .firstName(firstName)
                                .lastName(lastName)
                                .email(email)
                                .department(department)
                                .build());
                    } else {
                        log.warn("Skipping XLSX row {} — missing required fields", i);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to parse XLSX file: {}", e.getMessage());
            throw new InvalidOperationException("Failed to parse XLSX file: " + e.getMessage());
        }

        log.info("Parsed {} employees from XLSX", employees.size());
        return employees;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }
}
