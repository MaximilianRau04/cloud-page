package cloudpage.service;

import cloudpage.dto.FileDto;
import cloudpage.dto.FolderContentItemDto;
import cloudpage.dto.FolderDto;
import cloudpage.dto.PageResponseDto;
import cloudpage.exceptions.FileDeletionException;
import cloudpage.exceptions.InvalidPathException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FolderService {

  public FolderDto getFolderTree(String rootPath) throws IOException {
    Path root = Paths.get(rootPath);
    validateRoot(root);
    return readFolder(root);
  }

  public FolderDto getFolderTree(String rootPath, String relativePath) throws IOException {
    Path folder = Paths.get(rootPath, relativePath).normalize();
    validatePath(rootPath, folder);
    return readFolder(folder);
  }

  public Path createFolder(String rootPath, String relativeParentPath, String name)
      throws IOException {
    Path parent = Paths.get(rootPath, relativeParentPath).normalize();
    validatePath(rootPath, parent);
    Path newFolder = parent.resolve(name);
    return Files.createDirectory(newFolder);
  }

  public void deleteFolder(String rootPath, String relativeFolderPath) throws IOException {
    Path folder = Paths.get(rootPath, relativeFolderPath).normalize();
    validatePath(rootPath, folder);

    Files.walk(folder)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            p -> {
              try {
                Files.delete(p);
              } catch (IOException e) {
                throw new FileDeletionException(
                    "Failed to delete: " + p + "with exception : " + e.getMessage());
              }
            });
  }

  public void renameOrMoveFolder(String rootPath, String relativeFolderPath, String relativeNewPath)
      throws IOException {
    Path source = Paths.get(rootPath, relativeFolderPath).normalize();
    Path target = Paths.get(rootPath, relativeNewPath).normalize();
    validatePath(rootPath, source);
    validatePath(rootPath, target.getParent());
    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
  }

  public PageResponseDto<FolderContentItemDto> getFolderContentPage(
      String rootPath, String relativePath, int page, int size, String sort) throws IOException {
    if (page < 0) {
      throw new IllegalArgumentException("page must be greater than or equal to 0");
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be greater than 0");
    }

    Path folder =
        (relativePath == null || relativePath.isBlank())
            ? Paths.get(rootPath)
            : Paths.get(rootPath, relativePath).normalize();

    validatePath(rootPath, folder);
    if (!Files.exists(folder) || !Files.isDirectory(folder)) {
      throw new InvalidPathException("Folder does not exist or is not a directory: " + folder);
    }

    List<FolderContentItemDto> items = new ArrayList<>();

    try (var stream = Files.list(folder)) {
      items =
          stream
              .map(
                  path -> {
                    boolean isDirectory = Files.isDirectory(path);
                    long sizeValue = 0L;
                    String mimeType = null;
                    if (!isDirectory) {
                      try {
                        BasicFileAttributes attrs =
                            Files.readAttributes(path, BasicFileAttributes.class);
                        sizeValue = attrs.size();
                        mimeType = Files.probeContentType(path);
                      } catch (IOException e) {
                        throw new FileDeletionException(
                            "Failed to read: " + path + "with exception : " + e.getMessage());
                      }
                    }
                    return new FolderContentItemDto(
                        path.getFileName().toString(),
                        path.toAbsolutePath().toString(),
                        isDirectory,
                        sizeValue,
                        mimeType);
                  })
              .collect(Collectors.toList());
    }

    applySorting(items, sort);

    long totalElements = items.size();
    int totalPages = (int) Math.ceil(totalElements / (double) size);

    int fromIndex = page * size;
    int toIndex = Math.min(fromIndex + size, items.size());

    List<FolderContentItemDto> pageContent =
        fromIndex >= items.size() ? List.of() : items.subList(fromIndex, toIndex);

    return new PageResponseDto<>(pageContent, totalElements, totalPages, page);
  }

  private void applySorting(List<FolderContentItemDto> items, String sort) {
    String sortField = "name";
    boolean ascending = true;

    if (sort != null && !sort.isBlank()) {
      String[] parts = sort.split(",");
      if (parts.length > 0 && !parts[0].isBlank()) {
        sortField = parts[0];
      }
      if (parts.length > 1 && !parts[1].isBlank()) {
        ascending = !"desc".equalsIgnoreCase(parts[1]);
      }
    }

    Comparator<FolderContentItemDto> comparator;
    switch (sortField) {
      case "name":
      default:
        comparator =
            Comparator.comparing(
                FolderContentItemDto::getName, String.CASE_INSENSITIVE_ORDER);
        break;
    }

    if (!ascending) {
      comparator = comparator.reversed();
    }

    items.sort(comparator);
  }

  private FolderDto readFolder(Path path) throws IOException {
    List<FolderDto> subfolders =
        Files.list(path)
            .filter(Files::isDirectory)
            .map(
                subPath -> {
                  try {
                    return readFolder(subPath);
                  } catch (IOException e) {
                    throw new FileDeletionException(
                        "Failed to read: " + path + "with exception : " + e.getMessage());
                  }
                })
            .collect(Collectors.toList());

    List<FileDto> files =
        Files.list(path)
            .filter(Files::isRegularFile)
            .map(
                filePath -> {
                  try {
                    BasicFileAttributes attrs =
                        Files.readAttributes(filePath, BasicFileAttributes.class);
                    return new FileDto(
                        filePath.getFileName().toString(),
                        filePath.toAbsolutePath().toString(),
                        attrs.size(),
                        Files.probeContentType(filePath));
                  } catch (IOException e) {
                    throw new FileDeletionException(
                        "Failed to read: " + filePath + "with exception : " + e.getMessage());
                  }
                })
            .collect(Collectors.toList());

    return new FolderDto(
        path.getFileName().toString(), path.toAbsolutePath().toString(), subfolders, files);
  }

  public void validatePath(String rootPath, Path path) {
    if (!path.toAbsolutePath().startsWith(Paths.get(rootPath).toAbsolutePath())) {
      throw new InvalidPathException("Access outside the user's root folder is forbidden: " + path);
    }
  }

  private void validateRoot(Path root) {
    if (!Files.exists(root) || !Files.isDirectory(root)) {
      throw new InvalidPathException("Root folder does not exist or is not a directory: " + root);
    }
  }
}
