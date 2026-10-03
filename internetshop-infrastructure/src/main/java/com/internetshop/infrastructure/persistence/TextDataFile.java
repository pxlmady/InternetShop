package com.internetshop.infrastructure.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;

/**
 * Технічна деталь збереження: текстовий файл, один запис на рядок, поля через «|».
 * Текстові значення кодуються Base64, тому роздільники в назвах нічого не ламають.
 * Запис атомарний: спочатку тимчасовий файл, потім заміна — файл не залишиться напівзаписаним.
 */
final class TextDataFile {

    static final String SEPARATOR = "|";

    private final Path file;
    private final String header;

    TextDataFile(Path file, String header) {
        this.file = file;
        this.header = header;
    }

    /** Повертає рядки даних (без заголовка) або порожній список, якщо файлу ще немає. */
    List<String> readRecords() {
        try {
            if (!Files.exists(file)) return List.of();
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).equals(header))
                throw new IllegalStateException("Невідомий формат файлу даних: " + file.getFileName());
            return lines.subList(1, lines.size()).stream().filter(l -> !l.isBlank()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалося прочитати файл даних " + file.getFileName(), e);
        }
    }

    void writeRecords(List<String> records) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
            try {
                StringBuilder content = new StringBuilder(header).append('\n');
                for (String r : records) content.append(r).append('\n');
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалося записати файл даних " + file.getFileName(), e);
        }
    }

    static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    static String[] split(String record, int expectedFields) {
        String[] parts = record.split("\\" + SEPARATOR, -1);
        if (parts.length != expectedFields)
            throw new IllegalStateException("Пошкоджений запис у файлі даних.");
        return parts;
    }
}
