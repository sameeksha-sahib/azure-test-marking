package utils;

import hooks.AzureService;
import io.cucumber.java.Scenario;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.logging.Logger;

public class ExcelUtil {
    private static final Logger LOGGER = Logger.getLogger(ExcelUtil.class.getName());
    private static String fileName = "./target/AutomationTestRun" + getCurrentDateStamp() + ".xls";
    private static String sheetName = "TestData";

    public static String getCurrentDateStamp() {
        return new SimpleDateFormat("yyyy.MM.dd").format(new java.util.Date());
    }

    public void createTestExcelBeforeSuit() {
        String[] columns = {"Description of Scenario", "Status", "Test Case Ids", "Feature File", "Execution Time"};
        createExcelFileWithHeaders(fileName, sheetName, columns);
    }

    public void writeTestDataExcelAfterScenario(Scenario scenario, long scenarioExecutionTimeSec) {
        LOGGER.info("Writing AutomationRun excel with scenario data");
        String testCaseIDs = new AzureService().extractTestCaseIds(scenario);
        String[] featureLists = scenario.getId().split("\\.");
        String[] features = (featureLists[featureLists.length-2]).split("/");
        String featureName = features[features.length-1];
        LOGGER.info("Feature File Name:- " + featureName);
        String[] testData = {scenario.getName(), scenario.getStatus().toString(), testCaseIDs, featureName, String.valueOf(scenarioExecutionTimeSec)};
        writeExcelFileData(fileName, sheetName, testData);
    }

    public List<Row> getTestRunDataFromExcel() {
        return readExcel(fileName, sheetName);
    }

    public String[] getColumnDataFromExcel(List<Row> rows, int offsetFromLastColIndex) {
        String[] columnData = new String[rows.size() - 1];
        int colIndex = rows.get(0).getLastCellNum() - offsetFromLastColIndex;
        for (int i = 1; i < rows.size(); ++i) {
            Row row = rows.get(i);
            columnData[i - 1] = row.getCell(colIndex).toString();
        }
        return columnData;
    }

    public String[] getColumnDataFromExcel(String fileName, String sheetName, int offsetFromLastColIndex) {
        List<Row> rows = readExcel(fileName, sheetName);
        return getColumnDataFromExcel(rows, offsetFromLastColIndex);
    }

    public List<Row> readExcel(String filePath, String sheetName) {
        try {
            File file = new File(filePath);
            FileInputStream inputStream = new FileInputStream(file);

            Workbook workbook = new HSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheet(sheetName);

            int physicalRowCount = sheet.getPhysicalNumberOfRows();
            List<Row> rows = new ArrayList<>();
            for (int i = 1; i <= physicalRowCount; ++i) {
                Row row = sheet.getRow(i);
                rows.add(row);
            }

            return rows;
        } catch (IOException e) {
            String message = "Unable to read Excel: " + filePath;
            LOGGER.error(message, e);
            throw new RuntimeException(message);
        }
    }

    public void createExcelFileWithHeaders(String fileName, String sheetName, String[] columns) {
        try {
            HSSFWorkbook workbook = new HSSFWorkbook();
            HSSFSheet sheet = workbook.createSheet(sheetName);
            int rowNum = 1;

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);
            headerFont.setColor(IndexedColors.RED.getIndex());

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);

            HSSFRow headerRow = sheet.createRow(rowNum);

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerCellStyle);
            }
            OutputStream fileOut = new FileOutputStream(fileName);
            workbook.write(fileOut);
            fileOut.close();
            workbook.close();
            LOGGER.info("Excel file for test data is initiated");
        } catch (Exception e) {
            LOGGER.info("Exception in CreateExcelFile------" + e);
        }
    }

    public void writeExcelFileData(String fileName, String sheetName, String[] testData) {
        try {
            File xlsxFile = new File(fileName);
            FileInputStream fileInputStream = new FileInputStream(xlsxFile);
            HSSFWorkbook workbook = new HSSFWorkbook(fileInputStream);
            HSSFSheet worksheet = workbook.getSheet(sheetName);
            int rowNum = worksheet.getLastRowNum() + 1;
            HSSFRow row = worksheet.createRow(rowNum);
            for (int i = 0; i < testData.length; i++) {
                row.createCell(i).setCellValue(testData[i]);
            }
            // Resize all columns to fit the content size
            for (int i = 0; i < testData.length; i++) {
                worksheet.autoSizeColumn(i);
            }
            fileInputStream.close();
            FileOutputStream outputStream = new FileOutputStream(xlsxFile);
            workbook.write(outputStream);
            workbook.close();
            outputStream.close();
            LOGGER.info("Test Data is written in Excel File");

        } catch (IOException e) {
            LOGGER.info("Exception in CreateExcelFile------" + e);

        }
    }

}
