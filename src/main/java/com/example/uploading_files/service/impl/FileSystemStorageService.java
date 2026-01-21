package com.example.uploading_files.service.impl;

import com.example.uploading_files.exception.StorageException;
import com.example.uploading_files.exception.StorageFileNotFoundException;
import com.example.uploading_files.propertities.StorageProperties;
import com.example.uploading_files.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

@Service
public class FileSystemStorageService implements StorageService {

    private final Path rootLocation;

    public FileSystemStorageService(StorageProperties storageProperties) {
        if (storageProperties.getLocation().trim().length() == 0) {
            throw new StorageException("File upload location can not be empty!");
        }
        this.rootLocation = Paths.get(storageProperties.getLocation());
    }

    @Override
    public void init() {
        if (rootLocation != null) {
            try {
                Files.createDirectories(rootLocation);
            } catch (IOException e) {
                throw new StorageException("Could not initialize storage: " + e);
            }
        }

    }

    @Override
    public void store(MultipartFile multipartFile) {
        try {
            if (multipartFile.isEmpty()) {
                throw new StorageException("Failed to store empty file");
            }
            Path destinationFile = this.rootLocation.resolve(
                    Paths.get(multipartFile.getOriginalFilename())
            ).normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
                throw new StorageException(
                        "Cannot store file outside current directory.");
            }
            try (InputStream inputStream = multipartFile.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);

            }
        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + e);
        }
    }

    @Override
    public Stream<Path> loadAll() {
        try {
            return Files.walk(this.rootLocation, 1)
                    .filter(path -> !path.equals(this.rootLocation))
                    .map(this.rootLocation::relativize);
        } catch (IOException e) {
            throw new StorageException("Failed to read storage file " + e);
        }
    }

    @Override
    public Path load(String fileName) {
        return rootLocation.resolve(fileName);
    }

    @Override
    public Resource loadAsResource(String fileName) {
        try {
            Path file = load(fileName);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new StorageFileNotFoundException("Could not read the file: " + fileName);
            }
        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read the file: " + fileName);
        }
    }

    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }
}
