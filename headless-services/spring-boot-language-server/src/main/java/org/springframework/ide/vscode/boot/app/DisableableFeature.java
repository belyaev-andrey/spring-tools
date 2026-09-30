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
package org.springframework.ide.vscode.boot.app;

/**
 * A Java editor feature that {@code boot-java.disabled-features} can turn off, for example because
 * another Java tooling in the same editor provides it too. The ids are part of the settings, so
 * they must stay stable, and they name what the user sees, not the class that implements it.
 *
 * @author Andrey Belyaev
 */
public enum DisableableFeature {

	/** Property key proposals in {@code @Value}. {@code classpath:} proposals stay. */
	VALUE_PROPERTY_COMPLETION("value-property-completion"),

	/** Go to definition and find references on property keys in {@code @Value} and {@code @ConditionalOnProperty}. */
	PROPERTY_KEY_NAVIGATION("property-key-navigation"),

	/** Go to definition and find references on bean names in {@code @DependsOn}, {@code @Qualifier}, {@code @Resource} and {@code @Named}. */
	BEAN_NAME_NAVIGATION("bean-name-navigation"),

	/** Bean name proposals in {@code @DependsOn}, {@code @Qualifier}, {@code @Resource}, {@code @Named} and {@code @ConditionalOn(Missing)Bean}. */
	BEAN_NAME_COMPLETION("bean-name-completion"),

	/** Profile proposals in {@code @Profile}. */
	PROFILE_COMPLETION("profile-completion"),

	/** Property key proposals in {@code @ConditionalOnProperty}. */
	CONDITIONAL_ON_PROPERTY_COMPLETION("conditional-on-property-completion"),

	/** Scope proposals in {@code @Scope}. */
	SCOPE_COMPLETION("scope-completion"),

	/** The quick fixes of a problem set to {@code IGNORE}, offered as refactorings. */
	IGNORED_PROBLEM_FIXES("ignored-problem-fixes"),

	/** "Convert query to text block" on query strings. */
	QUERY_TEXT_BLOCK_CONVERSION("query-text-block-conversion");

	private final String id;

	DisableableFeature(String id) {
		this.id = id;
	}

	/** The id to list in {@code boot-java.disabled-features}. */
	public String getId() {
		return id;
	}
}
