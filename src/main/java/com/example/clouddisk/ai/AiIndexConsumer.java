package com.example.clouddisk.ai;

import com.example.clouddisk.entity.FileInfo;
import com.example.clouddisk.mapper.FileMapper;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AiIndexConsumer {
    private static final Set<String> SUPPORTED = Set.of("pdf", "doc", "docx", "txt", "md");
    private final AiIndexTaskMapper tasks;
    private final FileMapper files;
    private final AiSearchService search;
    private final RabbitTemplate rabbit;

    public AiIndexConsumer(AiIndexTaskMapper tasks, FileMapper files, AiSearchService search,
                           RabbitTemplate rabbit) {
        this.tasks = tasks;
        this.files = files;
        this.search = search;
        this.rabbit = rabbit;
    }

    @RabbitListener(queues = AiRabbitConfig.QUEUE)
    public void consume(String value) {
        Long id = Long.valueOf(value);
        if (tasks.startAttempt(id) == 0) return;
        AiIndexTask task = tasks.findById(id);
        try {
            FileInfo file = files.findById(task.getFileId());
            if (file != null && !task.getGeneration().equals(file.getIndexGeneration())) {
                tasks.setStatus(id, "STALE", null);
                return;
            }
            search.deleteFile(task.getFileId());
            if (file == null || Boolean.TRUE.equals(file.getDeleted()) || "DELETE".equals(task.getOperation())) {
                tasks.setStatus(id, "DONE", null);
                return;
            }
            String name = file.getFileName().toLowerCase(Locale.ROOT);
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            if (!SUPPORTED.contains(ext)) {
                tasks.setStatus(id, "SKIPPED", null);
                return;
            }
            Path path = Path.of(file.getFilePath());
            if (!Files.isRegularFile(path)) throw new IllegalStateException("文件内容不存在");
            String content = extract(path);
            List<String> chunks = chunk(content);
            if (!chunks.isEmpty()) search.index(file, task.getGeneration(), chunks);
            tasks.setStatus(id, chunks.isEmpty() ? "SKIPPED" : "DONE", null);
        } catch (Exception error) {
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (reason.length() > 500) reason = reason.substring(0, 500);
            if (task.getAttempts() >= 3) {
                tasks.setStatus(id, "FAILED", reason);
                throw new AmqpRejectAndDontRequeueException("索引任务失败三次: " + id, error);
            }
            tasks.setStatus(id, "RETRY", reason);
            try { rabbit.convertAndSend(AiRabbitConfig.EXCHANGE, "index", value); }
            catch (AmqpException ignored) { /* scheduled republisher recovers it */ }
        }
    }

    static List<String> chunk(String content) {
        if (content == null || content.isBlank()) return List.of();
        String normalized = content.replaceAll("\\s+", " ").trim();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < normalized.length() && chunks.size() < 1000;) {
            int end = Math.min(start + 280, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) break;
            start = end - 40;
        }
        return chunks;
    }

    private String extract(Path path) throws Exception {
        ParseContext context = new ParseContext();
        TesseractOCRConfig ocr = new TesseractOCRConfig();
        ocr.setSkipOcr(true);
        context.set(TesseractOCRConfig.class, ocr);
        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        context.set(PDFParserConfig.class, pdf);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, path.getFileName().toString());
        BodyContentHandler handler = new BodyContentHandler(2_000_000);
        try (InputStream input = Files.newInputStream(path)) {
            new AutoDetectParser().parse(input, handler, metadata, context);
        }
        return handler.toString();
    }
}
