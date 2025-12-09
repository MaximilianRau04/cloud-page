package cloudpage.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cloudpage.dto.FileResource;
import cloudpage.exceptions.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class FileServiceTest {
  private final FileService fileService = new FileService();

  @Test
  void loadAsResource_existing_returnsResource() throws Exception {

    Path tempFile = Files.createTempFile("test-", ".txt");
    Files.writeString(tempFile, "Hello");

    FileResource result = fileService.loadAsResource(tempFile);

    assertNotNull(result);
    assertNotNull(result.getResource(), "Resource should not be null");
    assertNotNull(result.getETag(), "ETag should not be null");
    assertNotNull(result.getLastModified() > 0, "Last modified should be > 0");
  }

  @Test
  void loadAsResource_missingFile_throwsException() {
    Path missing = Path.of("/tmp/some/non/existing/file.txt");

    assertThrows(FileNotFoundException.class, () -> fileService.loadAsResource(missing));
  }
}
