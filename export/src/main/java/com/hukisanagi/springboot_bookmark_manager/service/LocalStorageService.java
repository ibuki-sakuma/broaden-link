package com.hukisanagi.springboot_bookmark_manager.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.io.File;

@Service
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "false", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path rootLocation;

    public LocalStorageService(@Value("${storage.local.directory:data/favicons}") String directory) {
        this.rootLocation = Paths.get(directory);
    }

    @Override
    public String saveFile(byte[] fileContent, String fileName) {
        try {
            Path destinationFile = this.rootLocation.resolve(Paths.get(fileName)).normalize().toAbsolutePath();
            Files.createDirectories(destinationFile.getParent()); // フォルダがなければ作成
            Files.write(destinationFile, fileContent);
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file.", e);
        }
    }

    @Override
    public void deleteFile(String fileName) {
        try {
            Path filePath = this.rootLocation.resolve(Paths.get(fileName)).normalize().toAbsolutePath();
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + fileName, e);
        }
    }

    @Override
    public String getFileUrl(String fileName) {
        return "/favicons/" + fileName;
    }

    @Override
    public String copyFile(String sourceFileName, String destinationFileName) {
        try {
            Path sourcePath = this.rootLocation.resolve(Paths.get(sourceFileName)).normalize().toAbsolutePath();
            Path destinationPath = this.rootLocation.resolve(Paths.get(destinationFileName)).normalize().toAbsolutePath();
            Files.createDirectories(destinationPath.getParent());
            Files.copy(sourcePath, destinationPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return destinationFileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy file.", e);
        }
    }

    @Override
    public void clearRankingFavicons() {
        Path rankingLocation = this.rootLocation.resolve("ranking").normalize().toAbsolutePath();
        if (Files.exists(rankingLocation) && Files.isDirectory(rankingLocation)) {
            try (var stream = Files.walk(rankingLocation)) {
                stream.sorted(Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(File::delete);
            } catch (IOException e) {
                throw new RuntimeException("Failed to clear ranking favicons.", e);
            }
        }
    }

    @Override
    public void deleteFolder(String folderName) {
        try {
            Path folderPath = this.rootLocation.resolve(folderName).normalize().toAbsolutePath();
            if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
                try (var stream = Files.walk(folderPath)) {
                    stream.sorted(Comparator.reverseOrder())
                          .map(Path::toFile)
                          .forEach(File::delete);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete folder: " + folderName, e);
        }
    }
}