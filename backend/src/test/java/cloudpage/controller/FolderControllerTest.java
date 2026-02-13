package cloudpage.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cloudpage.dto.FolderDto;
import cloudpage.exceptions.InvalidPathException;
import cloudpage.model.User;
import cloudpage.security.JwtAuthFilter;
import cloudpage.security.JwtUtil;
import cloudpage.service.FolderService;
import cloudpage.service.UserService;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FolderController.class)
@AutoConfigureMockMvc(addFilters = false)
class FolderControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FolderService folderService;
  @MockitoBean private UserService userService;
  @MockitoBean private JwtAuthFilter jwtAuthFilter;
  @MockitoBean private JwtUtil jwtUtil;

  @TempDir Path tempDir;

  private User testUser;
  private FolderDto rootFolder;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId("user-1");
    testUser.setUsername("testuser");
    testUser.setRootFolderPath(tempDir.toString());
    when(userService.getCurrentUser()).thenReturn(testUser);

    rootFolder =
        new FolderDto("root", tempDir.toString(), Collections.emptyList(), Collections.emptyList());
  }

  // ── GET /api/folders ─────────────────────────────────────────────────────

  @Test
  void getUserRootFolder_returnsRootFolderDto() throws Exception {
    when(folderService.getFolderTree(tempDir.toString())).thenReturn(rootFolder);

    mockMvc
        .perform(get("/api/folders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("root"))
        .andExpect(jsonPath("$.folders").isArray())
        .andExpect(jsonPath("$.files").isArray());
  }

  // ── GET /api/folders/path ────────────────────────────────────────────────

  @Test
  void getFolderByPath_validPath_returnsSubfolderDto() throws Exception {
    FolderDto subFolder =
        new FolderDto(
            "docs",
            tempDir.resolve("docs").toString(),
            Collections.emptyList(),
            Collections.emptyList());
    when(folderService.getFolderTree(tempDir.toString(), "docs")).thenReturn(subFolder);

    mockMvc
        .perform(get("/api/folders/path").param("path", "docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("docs"));
  }

  @Test
  void getFolderByPath_invalidPath_returns400() throws Exception {
    when(folderService.getFolderTree(tempDir.toString(), "../../evil"))
        .thenThrow(new InvalidPathException("Access outside the user's root folder is forbidden"));

    mockMvc
        .perform(get("/api/folders/path").param("path", "../../evil"))
        .andExpect(status().isBadRequest());
  }

  // ── POST /api/folders ────────────────────────────────────────────────────

  @Test
  void createFolder_validRequest_returnsUpdatedTree() throws Exception {
    when(folderService.createFolder(tempDir.toString(), "docs", "newDir"))
        .thenReturn(tempDir.resolve("docs/newDir"));
    when(folderService.getFolderTree(tempDir.toString())).thenReturn(rootFolder);

    mockMvc
        .perform(post("/api/folders").param("parentPath", "docs").param("name", "newDir"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("root"));

    verify(folderService).createFolder(tempDir.toString(), "docs", "newDir");
  }

  @Test
  void createFolder_missingParams_returns400() throws Exception {
    mockMvc.perform(post("/api/folders")).andExpect(status().isBadRequest());
  }

  // ── DELETE /api/folders ──────────────────────────────────────────────────

  @Test
  void deleteFolder_validRequest_returnsUpdatedTree() throws Exception {
    when(folderService.getFolderTree(tempDir.toString())).thenReturn(rootFolder);

    mockMvc
        .perform(delete("/api/folders").param("folderPath", "oldDir"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("root"));

    verify(folderService).deleteFolder(tempDir.toString(), "oldDir");
  }

  // ── PATCH /api/folders ───────────────────────────────────────────────────

  @Test
  void renameOrMoveFolder_validRequest_returnsUpdatedTree() throws Exception {
    when(folderService.getFolderTree(tempDir.toString())).thenReturn(rootFolder);

    mockMvc
        .perform(patch("/api/folders").param("folderPath", "oldName").param("newPath", "newName"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("root"));

    verify(folderService).renameOrMoveFolder(tempDir.toString(), "oldName", "newName");
  }

  @Test
  void renameOrMoveFolder_pathTraversal_returns400() throws Exception {
    doThrow(new InvalidPathException("Forbidden"))
        .when(folderService)
        .renameOrMoveFolder(eq(tempDir.toString()), eq("safe"), eq("../../evil"));

    mockMvc
        .perform(patch("/api/folders").param("folderPath", "safe").param("newPath", "../../evil"))
        .andExpect(status().isBadRequest());
  }
}
