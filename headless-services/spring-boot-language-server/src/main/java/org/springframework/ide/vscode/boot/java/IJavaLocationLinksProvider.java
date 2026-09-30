/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public interface IJavaLocationLinksProvider {
	
	List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project, TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset);

	/**
	 * This provider, answering only while {@code enabled} returns true. The check runs on every
	 * request, so a settings change applies at once.
	 */
	default IJavaLocationLinksProvider onlyWhen(BooleanSupplier enabled) {
		return (cancelToken, project, docId, cu, n, offset) -> enabled.getAsBoolean()
				? getLocationLinks(cancelToken, project, docId, cu, n, offset)
				: List.of();
	}

}
