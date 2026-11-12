package com.iplytics.ingestion.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHasherTest {

    private final FileHasher hasher = new FileHasher();

    @TempDir
    Path dir;

    @Test
    void computesLowercaseHexSha256() throws IOException {
        Path file = Files.writeString(dir.resolve("a.csv"), "abc");

        // FIPS 180-2 test vector for "abc"
        assertThat(hasher.sha256(file))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void identicalContentHasTheSameHashRegardlessOfFileName() throws IOException {
        Path first = Files.writeString(dir.resolve("matches.csv"), "id,season\n1,2008\n");
        Path renamed = Files.writeString(dir.resolve("copy-of-matches.csv"), "id,season\n1,2008\n");
        Path changed = Files.writeString(dir.resolve("changed.csv"), "id,season\n1,2009\n");

        assertThat(hasher.sha256(first)).isEqualTo(hasher.sha256(renamed)).isNotEqualTo(hasher.sha256(changed));
    }

    @Test
    void hashesFilesLargerThanTheReadBuffer() throws IOException {
        Path file = Files.write(dir.resolve("big.csv"), new byte[200_000]);

        assertThat(hasher.sha256(file)).hasSize(64).matches("[0-9a-f]+");
    }
}
