package cloudpage.service;

import static org.junit.jupiter.api.Assertions.*;

import cloudpage.dto.FolderContentItemDto;
import cloudpage.dto.PageResponseDto;
import cloudpage.exceptions.InvalidPathException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class FolderServiceTest {

  private final FolderService folderService = new FolderService();

  @Test
  void getFolderContentPage_firstPage_basicPagination() throws IOException {
    Path tempDir = Files.createTempDirectory("folder-service-test-");
    try {
      // create 15 files
      for (int i = 0; i < 15; i++) {
        Files.writeString(tempDir.resolve("file-" + i + ".txt"), "data" + i);
      }

      PageResponseDto<FolderContentItemDto> page =
          folderService.getFolderContentPage(tempDir.toString(), "", 0, 10, null);

      assertEquals(0, page.getPageNumber());
      assertEquals(15, page.getTotalElements());
      assertEquals(2, page.getTotalPages());
      assertEquals(10, page.getContent().size());
    } finally {
      Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  @Test
  void getFolderContentPage_lastPagePartial() throws IOException {
    Path tempDir = Files.createTempDirectory("folder-service-test-");
    try {
      // create 23 files
      for (int i = 0; i < 23; i++) {
        Files.writeString(tempDir.resolve("file-" + i + ".txt"), "data" + i);
      }

      PageResponseDto<FolderContentItemDto> page =
          folderService.getFolderContentPage(tempDir.toString(), "", 2, 10, null);

      assertEquals(2, page.getPageNumber());
      assertEquals(23, page.getTotalElements());
      assertEquals(3, page.getTotalPages());
      assertEquals(3, page.getContent().size());
    } finally {
      Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  @Test
  void getFolderContentPage_pageBeyondLast_returnsEmptyContent() throws IOException {
    Path tempDir = Files.createTempDirectory("folder-service-test-");
    try {
      // create 5 files
      for (int i = 0; i < 5; i++) {
        Files.writeString(tempDir.resolve("file-" + i + ".txt"), "data" + i);
      }

      PageResponseDto<FolderContentItemDto> page =
          folderService.getFolderContentPage(tempDir.toString(), "", 2, 10, null);

      assertEquals(2, page.getPageNumber());
      assertEquals(5, page.getTotalElements());
      assertEquals(1, page.getTotalPages());
      assertTrue(page.getContent().isEmpty());
    } finally {
      Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  @Test
  void getFolderContentPage_sortByNameAscending() throws IOException {
    Path tempDir = Files.createTempDirectory("folder-service-test-");
    try {
      Files.writeString(tempDir.resolve("b.txt"), "b");
      Files.writeString(tempDir.resolve("a.txt"), "a");
      Files.writeString(tempDir.resolve("c.txt"), "c");

      PageResponseDto<FolderContentItemDto> page =
          folderService.getFolderContentPage(tempDir.toString(), "", 0, 10, "name,asc");

      assertEquals(3, page.getTotalElements());
      assertEquals(1, page.getTotalPages());
      assertEquals(3, page.getContent().size());
      assertEquals("a.txt", page.getContent().get(0).getName());
      assertEquals("b.txt", page.getContent().get(1).getName());
      assertEquals("c.txt", page.getContent().get(2).getName());
    } finally {
      Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }

  @Test
  void getFolderContentPage_invalidPath_throwsException() throws IOException {
    Path tempDir = Files.createTempDirectory("folder-service-test-");
    try {
      assertThrows(
          InvalidPathException.class,
          () ->
              folderService.getFolderContentPage(
                  tempDir.toString(), "../outside", 0, 10, null));
    } finally {
      Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }
}

