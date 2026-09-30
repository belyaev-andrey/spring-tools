/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public interface ReferenceProvider {

	List<? extends Location> provideReferences(CancelChecker cancelToken, IJavaProject project, ASTNode node, Annotation annotation, ITypeBinding type, int offset);
	List<? extends Location> provideReferences(CancelChecker cancelToken, IJavaProject project, TextDocument doc, ASTNode node, int offset);

	/**
	 * This provider, answering only while {@code enabled} returns true. The check runs on every
	 * request, so a settings change applies at once.
	 */
	default ReferenceProvider onlyWhen(BooleanSupplier enabled) {
		ReferenceProvider provider = this;
		return new ReferenceProvider() {
			@Override
			public List<? extends Location> provideReferences(CancelChecker cancelToken, IJavaProject project, ASTNode node, Annotation annotation, ITypeBinding type, int offset) {
				return enabled.getAsBoolean() ? provider.provideReferences(cancelToken, project, node, annotation, type, offset) : List.of();
			}

			@Override
			public List<? extends Location> provideReferences(CancelChecker cancelToken, IJavaProject project, TextDocument doc, ASTNode node, int offset) {
				return enabled.getAsBoolean() ? provider.provideReferences(cancelToken, project, doc, node, offset) : List.of();
			}
		};
	}

}
