package com.hukisanagi.springboot_bookmark_manager.service;

public interface StorageService {
    String saveFile(byte[] fileContent, String fileName);
    void deleteFile(String fileName);
    String getFileUrl(String fileName);
    String copyFile(String sourceFileName, String destinationFileName);
    void clearRankingFavicons();
    void deleteFolder(String folderName);
}
