/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.utils.test.MockProjectObserver;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

/**
 * Regression tests for AOT metadata refresh using the real file observer.
 */
public class DataRepositoryAotMetadataFileObserverTest {

	private static final String REPOSITORY = "dev.example.CoffeeRepository";
	private static final String OTHER_REPOSITORY = "dev.example.TeaRepository";

	@TempDir
	Path workspace;

	private final TestFileObserver observer = new TestFileObserver();
	private final MockProjectObserver projects = new MockProjectObserver();
	private final DataRepositoryAotMetadataService service = new DataRepositoryAotMetadataService(observer, null, null, projects);
	private final List<List<URI>> refreshes = new ArrayList<>();
	private Path buildDir;

	@BeforeEach
	void setup() {
		buildDir = workspace.resolve("build");
		service.addListener(refreshes::add);
	}

	@ParameterizedTest
	@CsvSource({ "maven,project[demo]", "maven,project[", "gradle,project[demo]", "gradle,project[" })
	void testMetadataRefreshesUnderPathsContainingBrackets(String buildType, String folderName) throws Exception {
		Path dir = workspace.resolve(folderName);
		Path buildDir = dir.resolve("out/custom-build");
		IJavaProject project = project(dir, buildDir, buildType);
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());

		Path file = writeMetadata(buildDir, buildType, REPOSITORY, "CoffeeRepositoryImpl");
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent(), "absence is cached until the build is reported");
		observer.notifyFileCreated(file.toUri().toString());
		var before = service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();
		assertEquals("CoffeeRepositoryImpl", before.type());
		assertSingleRefresh(file.toUri());
		refreshes.clear();

		writeMetadata(buildDir, buildType, REPOSITORY, "UpdatedCoffeeRepositoryImpl");
		assertSame(before, service.getRepositoryMetadata(project, REPOSITORY).orElseThrow());
		observer.notifyFileChanged(file.toUri().toString());
		var after = service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();
		assertNotSame(before, after);
		assertEquals("UpdatedCoffeeRepositoryImpl", after.type());
		assertSingleRefresh(file.toUri());
		refreshes.clear();

		// A clean may report just the deleted build directory, without events for its contents.
		FileUtils.deleteDirectory(buildDir.toFile());
		observer.notifyFileDeleted(buildDir.toUri().toString());
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());
		assertSingleRefresh(file.toUri());
	}

	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testChangingOneRepositoryPreservesOtherCachedMetadata(String buildType) throws Exception {
		Path dir = workspace.resolve("project");
		Path buildDir = dir.resolve("build");
		IJavaProject project = project(dir, buildDir, buildType);
		Path otherDir = workspace.resolve("other-project");
		Path otherBuildDir = otherDir.resolve("build");
		IJavaProject otherProject = project(otherDir, otherBuildDir, buildType);

		Path file = writeMetadata(buildDir, buildType, REPOSITORY, "CoffeeRepositoryImpl");
		writeMetadata(buildDir, buildType, OTHER_REPOSITORY, "TeaRepositoryImpl");
		writeMetadata(otherBuildDir, buildType, REPOSITORY, "OtherCoffeeRepositoryImpl");
		var before = service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();
		var otherRepository = service.getRepositoryMetadata(project, OTHER_REPOSITORY).orElseThrow();
		var otherProjectMetadata = service.getRepositoryMetadata(otherProject, REPOSITORY).orElseThrow();

		writeMetadata(buildDir, buildType, REPOSITORY, "UpdatedCoffeeRepositoryImpl");
		observer.notifyFileChanged(file.toUri().toString());

		var after = service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();
		assertNotSame(before, after);
		assertEquals("UpdatedCoffeeRepositoryImpl", after.type());
		assertSame(otherRepository, service.getRepositoryMetadata(project, OTHER_REPOSITORY).orElseThrow());
		assertSame(otherProjectMetadata, service.getRepositoryMetadata(otherProject, REPOSITORY).orElseThrow());
		assertSingleRefresh(file.toUri());
	}

	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testDeletingBuildDirectoryPreservesNeighboringDirectory(String buildType) throws Exception {
		IJavaProject project = project(workspace.resolve("project"), buildDir, buildType);
		Path neighboringBuildDir = workspace.resolve("build-other");
		IJavaProject neighboringProject = project(workspace.resolve("other-project"), neighboringBuildDir, buildType);

		Path file = writeMetadata(buildDir, buildType, REPOSITORY, "CoffeeRepositoryImpl");
		Path otherFile = writeMetadata(buildDir, buildType, OTHER_REPOSITORY, "TeaRepositoryImpl");
		writeMetadata(neighboringBuildDir, buildType, REPOSITORY, "OtherCoffeeRepositoryImpl");
		service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();
		service.getRepositoryMetadata(project, OTHER_REPOSITORY).orElseThrow();
		var neighboringMetadata = service.getRepositoryMetadata(neighboringProject, REPOSITORY).orElseThrow();

		FileUtils.deleteDirectory(buildDir.toFile());
		observer.notifyFileDeleted(buildDir.toUri().toString());

		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());
		assertFalse(service.getRepositoryMetadata(project, OTHER_REPOSITORY).isPresent());
		assertSame(neighboringMetadata, service.getRepositoryMetadata(neighboringProject, REPOSITORY).orElseThrow());
		assertSingleRefresh(file.toUri(), otherFile.toUri());
	}

	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testDeletedMetadataIsReloadedAfterRecreation(String buildType) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		Path file = writeMetadata(buildDir, buildType, REPOSITORY, "CoffeeRepositoryImpl");
		var before = service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();

		Files.delete(file);
		assertSame(before, service.getRepositoryMetadata(project, REPOSITORY).orElseThrow());
		observer.notifyFileDeleted(file.toUri().toString());
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());
		assertSingleRefresh(file.toUri());
		refreshes.clear();

		writeMetadata(buildDir, buildType, REPOSITORY, "RegeneratedCoffeeRepositoryImpl");
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent(), "the deletion is cached until creation is reported");
		observer.notifyFileCreated(file.toUri().toString());
		assertEquals("RegeneratedCoffeeRepositoryImpl", service.getRepositoryMetadata(project, REPOSITORY).orElseThrow().type());
		assertSingleRefresh(file.toUri());
	}

	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testBatchedCreatesAndChangesRefreshEachRepositoryOnce(String buildType) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());
		assertFalse(service.getRepositoryMetadata(project, OTHER_REPOSITORY).isPresent());
		Path file = writeMetadata(buildDir, buildType, REPOSITORY, "CoffeeRepositoryImpl");
		Path otherFile = writeMetadata(buildDir, buildType, OTHER_REPOSITORY, "TeaRepositoryImpl");
		String[] events = { file.toUri().toString(), otherFile.toUri().toString(), file.toUri().toString() };

		observer.notifyFilesCreated(events);
		// Repeated events without an intervening lookup have no cached entries left to invalidate.
		observer.notifyFilesCreated(events);
		assertSingleRefresh(file.toUri(), otherFile.toUri());
		assertEquals("CoffeeRepositoryImpl", service.getRepositoryMetadata(project, REPOSITORY).orElseThrow().type());
		assertEquals("TeaRepositoryImpl", service.getRepositoryMetadata(project, OTHER_REPOSITORY).orElseThrow().type());
		refreshes.clear();

		writeMetadata(buildDir, buildType, REPOSITORY, "UpdatedCoffeeRepositoryImpl");
		writeMetadata(buildDir, buildType, OTHER_REPOSITORY, "UpdatedTeaRepositoryImpl");
		observer.notifyFilesChanged(events);
		observer.notifyFilesChanged(events);
		assertSingleRefresh(file.toUri(), otherFile.toUri());
		assertEquals("UpdatedCoffeeRepositoryImpl", service.getRepositoryMetadata(project, REPOSITORY).orElseThrow().type());
		assertEquals("UpdatedTeaRepositoryImpl", service.getRepositoryMetadata(project, OTHER_REPOSITORY).orElseThrow().type());
	}

	@ParameterizedTest
	@CsvSource({ "maven,true", "maven,false", "gradle,true", "gradle,false" })
	void testOverlappingDeletionEventsRefreshEachRepositoryOnce(String buildType, boolean parentFirst) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		Path file = writeMetadata(buildDir, buildType, REPOSITORY, "CoffeeRepositoryImpl");
		Path otherFile = writeMetadata(buildDir, buildType, OTHER_REPOSITORY, "TeaRepositoryImpl");
		service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();
		service.getRepositoryMetadata(project, OTHER_REPOSITORY).orElseThrow();

		FileUtils.deleteDirectory(buildDir.toFile());
		String parentUri = buildDir.toUri().toString();
		String fileUri = file.toUri().toString();
		String otherFileUri = otherFile.toUri().toString();
		String[] events = parentFirst ? new String[] { parentUri, fileUri, otherFileUri, parentUri }
				: new String[] { fileUri, parentUri, otherFileUri, fileUri };
		observer.notifyFilesDeleted(events);
		observer.notifyFilesDeleted(events);

		assertSingleRefresh(file.toUri(), otherFile.toUri());
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());
		assertFalse(service.getRepositoryMetadata(project, OTHER_REPOSITORY).isPresent());
	}

	@ParameterizedTest
	@CsvSource({ "maven,not-json", "maven,{", "gradle,not-json", "gradle,{" })
	void testMalformedMetadataRecoversAfterChange(String buildType, String invalidJson) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		Path file = metadataFile(buildDir, buildType, REPOSITORY);
		Files.createDirectories(file.getParent());
		Files.writeString(file, invalidJson);
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());

		writeMetadata(buildDir, buildType, REPOSITORY, "RecoveredCoffeeRepositoryImpl");
		assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent(), "the parse failure is cached until a change is reported");
		observer.notifyFileChanged(file.toUri().toString());

		assertEquals("RecoveredCoffeeRepositoryImpl", service.getRepositoryMetadata(project, REPOSITORY).orElseThrow().type());
		assertSingleRefresh(file.toUri());
	}

	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testUnrelatedFileEventsDoNotRefreshCachedMetadata(String buildType) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		writeMetadata(buildDir, buildType, REPOSITORY, "CoffeeRepositoryImpl");
		var before = service.getRepositoryMetadata(project, REPOSITORY).orElseThrow();
		Path uncachedFile = writeMetadata(buildDir, buildType, OTHER_REPOSITORY, "TeaRepositoryImpl");
		Path unrelatedFile = Files.writeString(workspace.resolve("notes.txt"), "unrelated");
		String[] events = { uncachedFile.toUri().toString(), unrelatedFile.toUri().toString() };

		observer.notifyFilesCreated(events);
		observer.notifyFilesChanged(events);
		Files.delete(uncachedFile);
		Files.delete(unrelatedFile);
		observer.notifyFilesDeleted(events);

		assertSame(before, service.getRepositoryMetadata(project, REPOSITORY).orElseThrow());
		assertTrue(refreshes.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testProjectChangesDoNotAccumulateSubscriptions(String buildType) throws Exception {
		int subscriptions = observer.subscriptionCount();
		for (int i = 0; i < 3; i++) {
			Path dir = workspace.resolve("project" + i);
			for (String output : List.of("build", "other-build")) {
				IJavaProject project = project(dir, dir.resolve(output), buildType);
				service.getRepositoryMetadata(project, REPOSITORY);
				projects.doWithListeners(listener -> listener.changed(project, false));
				assertEquals(subscriptions, observer.subscriptionCount());
				projects.doWithListeners(listener -> listener.deleted(project));
				assertEquals(subscriptions, observer.subscriptionCount());
			}
		}
	}

	private static Path writeMetadata(Path buildDir, String buildType, String repository, String implementation) throws Exception {
		Path file = metadataFile(buildDir, buildType, repository);
		Files.createDirectories(file.getParent());
		Files.writeString(file, """
				{"name": "%s", "module": "jdbc", "type": "%s", "methods": []}
				""".formatted(repository, implementation));
		return file;
	}

	private static Path metadataFile(Path buildDir, String buildType, String repository) {
		return metadataFolder(buildDir, buildType).resolve(repository.replace('.', '/') + ".json");
	}

	private void assertSingleRefresh(URI... files) {
		assertEquals(1, refreshes.size(), "one listener notification per batch");
		assertEquals(files.length, refreshes.get(0).size(), "each affected URI is reported once");
		assertEquals(Set.of(files), Set.copyOf(refreshes.get(0)));
	}

	private static IJavaProject project(Path dir, Path buildDir, String buildType) throws Exception {
		boolean maven = ProjectBuild.MAVEN_PROJECT_TYPE.equals(buildType);
		CPE source = CPE.source((maven ? dir.resolve("src/main/java") : metadataFolder(buildDir, buildType)).toFile(),
				buildDir.resolve("classes").toFile());
		source.setOwn(true);
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(List.of(source));
		IJavaProject project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getLocationUri()).thenReturn(dir.toUri());
		when(project.getProjectBuild()).thenReturn(IProjectBuild.create(buildType, dir.resolve(maven ? "pom.xml" : "build.gradle").toUri()));
		return project;
	}

	private static Path metadataFolder(Path buildDir, String buildType) {
		return buildDir.resolve(ProjectBuild.MAVEN_PROJECT_TYPE.equals(buildType) ? "spring-aot/main/resources" : "generated/aotResources");
	}

	private static class TestFileObserver extends BasicFileObserver {
		int subscriptionCount() {
			return createRegistry.size() + changeRegistry.size() + deleteRegistry.size();
		}
	}

}
