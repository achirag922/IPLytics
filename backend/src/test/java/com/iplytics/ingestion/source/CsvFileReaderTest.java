package com.iplytics.ingestion.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iplytics.ingestion.InvalidFileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvFileReaderTest {

    @TempDir
    Path dir;

    @Test
    void parsesQuotedCommasAndEscapedQuotes() throws IOException {
        List<List<String>> records = readAll("venue,note\n\"Punjab Cricket Association Stadium, Mohali\",\"say \"\"hi\"\"\"\n");

        assertThat(records).containsExactly(
                List.of("venue", "note"),
                List.of("Punjab Cricket Association Stadium, Mohali", "say \"hi\""));
    }

    @Test
    void handlesCrlfLineEndingsAndNoTrailingNewline() throws IOException {
        assertThat(readAll("a,b\r\n1,2\r\n3,4")).containsExactly(List.of("a", "b"), List.of("1", "2"), List.of("3", "4"));
    }

    @Test
    void skipsAUtf8ByteOrderMark() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        bytes.write("id,season\n1,2008\n".getBytes(StandardCharsets.UTF_8));
        Path file = Files.write(dir.resolve("bom.csv"), bytes.toByteArray());

        assertThat(readAll(file)).containsExactly(List.of("id", "season"), List.of("1", "2008"));
    }

    @Test
    void keepsEmptyLinesAsRecordsSoValidationCanReportThem() throws IOException {
        assertThat(readAll("a,b\n1,2\n\n3,4\n"))
                .containsExactly(List.of("a", "b"), List.of("1", "2"), List.of(""), List.of("3", "4"));
    }

    @Test
    void returnsNullForAnEmptyFile() throws IOException {
        assertThat(readAll("")).isEmpty();
    }

    @Test
    void reportsMalformedCsvWithTheLineNumber() {
        Path file = write("a,b\n1,2\n\"unterminated,3\n");

        assertThatThrownBy(() -> readAll(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Malformed CSV")
                .hasMessageContaining("line");
    }

    @Test
    void rejectsFilesThatAreNotUtf8() throws IOException {
        Path file = Files.write(dir.resolve("latin1.csv"), "a,b\nMüller,1\n".getBytes(StandardCharsets.ISO_8859_1));

        assertThatThrownBy(() -> readAll(file))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("not valid UTF-8");
    }

    private List<List<String>> readAll(String content) throws IOException {
        return readAll(write(content));
    }

    private static List<List<String>> readAll(Path file) throws IOException {
        List<List<String>> records = new ArrayList<>();
        try (CsvFileReader reader = CsvFileReader.open(file)) {
            List<String> record;
            while ((record = reader.next()) != null) {
                records.add(record);
            }
        }
        return records;
    }

    private Path write(String content) {
        try {
            return Files.writeString(dir.resolve("file.csv"), content);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
