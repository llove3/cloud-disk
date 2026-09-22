package com.example.clouddisk.ai;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOfficeExtractionTest {
    @TempDir Path dir;

    @Test
    void extractsAddedSpreadsheetPresentationAndCsvFormats() throws Exception {
        AiIndexConsumer consumer = new AiIndexConsumer(null, null, null, null);
        for (String ext : new String[]{"xls", "xlsx"}) {
            Path file = dir.resolve("sample." + ext);
            try (Workbook book = ext.equals("xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
                book.createSheet("Data").createRow(0).createCell(0).setCellValue("orchard harvest schedule");
                try (var out = Files.newOutputStream(file)) { book.write(out); }
            }
            assertTrue(consumer.extract(file).contains("orchard harvest schedule"), ext);
        }
        Path ppt = dir.resolve("sample.ppt");
        try (var show = new HSLFSlideShow()) {
            var shape = new org.apache.poi.hslf.usermodel.HSLFTextBox();
            shape.setText("orchard harvest schedule");
            show.createSlide().addShape(shape);
            try (var out = Files.newOutputStream(ppt)) { show.write(out); }
        }
        assertTrue(consumer.extract(ppt).contains("orchard harvest schedule"), "ppt");
        Path pptx = dir.resolve("sample.pptx");
        try (var show = new XMLSlideShow()) {
            var shape = show.createSlide().createTextBox();
            shape.setText("orchard harvest schedule");
            try (var out = Files.newOutputStream(pptx)) { show.write(out); }
        }
        assertTrue(consumer.extract(pptx).contains("orchard harvest schedule"), "pptx");
        Path csv = dir.resolve("sample.csv");
        Files.writeString(csv, "topic,detail\norchard,harvest schedule\n");
        assertTrue(consumer.extract(csv).contains("harvest schedule"), "csv");
    }
}
