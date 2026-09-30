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
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The quick fixes of a problem set to {@code IGNORE} are offered as refactorings, unless
 * {@code boot-java.disabled-features} lists {@code ignored-problem-fixes}.
 *
 * @author Andrey Belyaev
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class ReconcileProblemCodeActionProviderTest {

	private static final String FIX_LABEL = "Convert @Autowired field into Constructor Parameter";

	private static final String SOURCE = """
			package org.test;

			import org.springframework.beans.factory.annotation.Autowired;
			import org.springframework.stereotype.Component;

			@Component
			public class FieldInjected {

				@Autowired
				private MainClass mainClass;

			}
			""";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());
		projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@AfterEach
	public void tearDown() {
		harness.changeConfiguration(Map.of());
	}

	@Test
	void fixOfIgnoredProblemIsOfferedAsRefactoring() throws Exception {
		harness.changeConfiguration(settings(true));

		assertEquals(1, fieldInjectionRefactorings().size());
	}

	@Test
	void fixOfIgnoredProblemIsNotOfferedWhenSwitchedOff() throws Exception {
		harness.changeConfiguration(settings(false));

		assertEquals(0, fieldInjectionRefactorings().size());
	}

	private List<CodeAction> fieldInjectionRefactorings() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/org/test/FieldInjected.java").toUri().toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, SOURCE, docUri);
		return editor.getCodeActions("mainClass;", 1).stream()
				.filter(ca -> FIX_LABEL.equals(ca.getLabel()))
				.toList();
	}

	private static Map<String, Object> settings(boolean fixesForIgnoredProblems) {
		List<String> disabled = fixesForIgnoredProblems ? List.of() : List.of("ignored-problem-fixes");
		return Map.of(
				"spring-boot", Map.of("ls", Map.of("problem", Map.of("boot2", Map.of("JAVA_CONSTRUCTOR_PARAMETER_INJECTION", "IGNORE")))),
				"boot-java", Map.of("disabled-features", disabled));
	}
}
