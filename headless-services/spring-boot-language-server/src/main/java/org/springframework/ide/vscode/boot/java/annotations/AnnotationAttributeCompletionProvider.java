/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.annotations;

import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.jdt.core.dom.ASTNode;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public interface AnnotationAttributeCompletionProvider {
	
	List<AnnotationAttributeProposal> getCompletionCandidates(IJavaProject project, ASTNode node);

	/**
	 * This provider, proposing only while {@code enabled} returns true. The check runs on every
	 * request, so a settings change applies at once.
	 */
	default AnnotationAttributeCompletionProvider onlyWhen(BooleanSupplier enabled) {
		return (project, node) -> enabled.getAsBoolean() ? getCompletionCandidates(project, node) : List.of();
	}

}
