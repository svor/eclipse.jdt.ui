/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.sef;

import org.eclipse.jdt.core.dom.PostfixExpression;

import org.eclipse.jdt.internal.corext.textmanipulation.SimpleTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;

final class EncapsulatePostfixAccess extends SimpleTextEdit {
	
	public EncapsulatePostfixAccess(String getter, String setter, PostfixExpression  prefix) {
		super(prefix.getStartPosition(), prefix.getLength(), setter + "(" + getter + "() " + prefix.getOperator().toString().substring(0, 1) + " 1)");
	}	
	
	private EncapsulatePostfixAccess(TextRange range, String text) {
		super(range, text);
	}
	
	/* non Java-doc
	 * @see TextEdit#getCopy
	 */
	public TextEdit copy() {
		return new EncapsulatePostfixAccess(getTextRange().copy(), getText());
	}	
}