/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.corext.refactoring.typeconstraints2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Platform;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Type;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.CompilationUnitRange;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TypeEnvironment;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;


public class InferTypeArgumentsTCModel {
	
	private static class TypeConstraintComparer implements IElementComparer/*<ITypeConstraint2>*/ {
		public boolean equals(Object a, Object b) {
			return ((ITypeConstraint2) a).isSameAs((ITypeConstraint2) b);
		}
		public int hashCode(Object element) {
			return ((ITypeConstraint2) element).getHash();
		}
	}
	
	private static class ConstraintVariableComparer implements IElementComparer/*<ConstraintVariable2>*/ {
		public boolean equals(Object a, Object b) {
			return ((ConstraintVariable2) a).isSameAs((ConstraintVariable2) b);
		}
		public int hashCode(Object element) {
			return ((ConstraintVariable2) element).getHash();
		}
	}
	
	private static final Object NULL= new Object() {
		public String toString() {
			return ""; //$NON-NLS-1$
		}
	};
	
	protected static final boolean DEBUG= "true".equalsIgnoreCase(Platform.getDebugOption("org.eclipse.jdt.ui/debug/TypeConstraints")); //$NON-NLS-1$//$NON-NLS-2$

	private static final String COLLECTION_ELEMENT= "CollectionElement"; //$NON-NLS-1$
	private static final String INDEXED_COLLECTION_ELEMENTS= "IndexedCollectionElements"; //$NON-NLS-1$
	private static final String USED_IN= "UsedIn"; //$NON-NLS-1$
	private static final CollectionElementVariable2[] EMPTY_COLLECTION_ELEMENT_VARIABLES= new CollectionElementVariable2[0];
	private static final Map EMPTY_COLLECTION_ELEMENT_VARIABLES_MAP= Collections.EMPTY_MAP;
	
	protected static boolean fStoreToString= DEBUG;
	
	/**
	 * Map from {@link ConstraintVariable2} to
	 * <ul>
	 * <li>{@link ITypeConstraint2}, or</li>
	 * <li>{@link List}&lt;{@link ITypeConstraint2}&gt;</li>
	 * </ul>
	 */
	private CustomHashtable/*<ConstraintVariable2, Object>*/ fConstraintVariables;
	private CustomHashtable/*<ITypeConstraint2, NULL>*/ fTypeConstraints;
	private HashSet/*<EquivalenceRepresentative>*/ fEquivalenceRepresentatives;
	private Collection/*CastVariable2*/ fCastVariables;
	
	private HashSet fCuScopedConstraintVariables;
	
	private Collection fNewTypeConstraints;
	private Collection fNewConstraintVariables; //TODO: remove?
	
	private TypeEnvironment fTypeEnvironment;
	
	public InferTypeArgumentsTCModel() {
		fTypeConstraints= new CustomHashtable(new TypeConstraintComparer());
		fConstraintVariables= new CustomHashtable(new ConstraintVariableComparer());
		fEquivalenceRepresentatives= new HashSet();
		fCastVariables= new ArrayList();
		
		fCuScopedConstraintVariables= new HashSet();
		fNewTypeConstraints= new ArrayList();
		fNewConstraintVariables= new ArrayList();
		
		fTypeEnvironment= new TypeEnvironment();
	}
	
	/**
	 * @param typeBinding the type binding to check
	 * @return whether the constraint variable should <em>not</em> be created
	 */
	public boolean filterConstraintVariableType(ITypeBinding typeBinding) {
		//TODO: filter makeDeclaringTypeVariable, since that's not used?
		//-> would need to adapt create...Constraint methods to deal with null
		
		return typeBinding.isPrimitive();
//		return TypeRules.canAssign(fCollectionBinding, typeBinding);
	}
	
	/**
	 * Allows for avoiding the creation of SimpleTypeConstraints based on properties of
	 * their constituent ConstraintVariables and ConstraintOperators. Can be used to e.g. 
	 * avoid creation of constraints for assignments between built-in types.
	 * 
	 * @param cv1 
	 * @param cv2
	 * @param operator
	 * @return <code>true</code> iff the type constraint should really be created
	 */
	public boolean keep(ConstraintVariable2 cv1, ConstraintVariable2 cv2, ConstraintOperator2 operator) {
		if ((cv1 == null || cv2 == null))
			return false;
		
		if (cv1.isSameAs(cv2)) {
			if (cv1 == cv2)
				return false;
			else
				Assert.isTrue(false);
		}
		
		if (cv1 instanceof CollectionElementVariable2 || cv2 instanceof CollectionElementVariable2)
			return true;
		
		if (cv1 instanceof IndependentTypeVariable2 || cv2 instanceof IndependentTypeVariable2)
			return true;
		
		//TODO: who needs these?
		if (cv1 instanceof TypeConstraintVariable2)
			if (isAGenericType(((TypeConstraintVariable2) cv1).getType()))
				return true;
				
		if (cv2 instanceof TypeConstraintVariable2)
			if (isAGenericType(((TypeConstraintVariable2) cv2).getType()))
				return true;
		
		return false;
	}
	
	/**
	 * @param cv
	 * @return a List of ITypeConstraint2s where cv is used
	 */
	public List/*<ITypeConstraint2>*/ getUsedIn(ConstraintVariable2 cv) {
		Object usedIn= cv.getData(USED_IN);
		if (usedIn == null)
			return Collections.EMPTY_LIST;
		else if (usedIn instanceof ArrayList)
			return Collections.unmodifiableList((ArrayList) usedIn);
		else
			return Collections.singletonList(usedIn);
	}
	
	/**
	 * Resets the accumulators for {@link #getNewConstraintVariables()} and
	 * {@link #getNewTypeConstraints()}.
	 */
	public void newCu() {
		fNewTypeConstraints.clear();
		fNewConstraintVariables.clear();
		pruneUnusedCuScopedCvs();
		fCuScopedConstraintVariables.clear();
	}
	
	private void pruneUnusedCuScopedCvs() {
		for (Iterator iter= fCuScopedConstraintVariables.iterator(); iter.hasNext();) {
			ConstraintVariable2 cv= (ConstraintVariable2) iter.next();
			//TODO: also prune if all element variables are unused; also prune element variables then
			if (getUsedIn(cv).size() == 0 && getElementVariables(cv).size() == 0)
				fConstraintVariables.remove(cv);
		}
	}

	public ConstraintVariable2[] getAllConstraintVariables() {
		ConstraintVariable2[] result= new ConstraintVariable2[fConstraintVariables.size()];
		int i= 0;
		for (Enumeration e= fConstraintVariables.keys(); e.hasMoreElements(); i++) {
			result[i]= (ConstraintVariable2) e.nextElement();
		}
		return result;
	}
	
	public EquivalenceRepresentative[] getEquivalenceRepresentatives() {
		return (EquivalenceRepresentative[]) fEquivalenceRepresentatives.toArray(new EquivalenceRepresentative[fEquivalenceRepresentatives.size()]);
	}
	
	public CastVariable2[] getCastVariables() {
		return (CastVariable2[]) fCastVariables.toArray(new CastVariable2[fCastVariables.size()]);
	}
	
//	public ConstraintVariable2[] getNewConstraintVariables() {
//		return (ConstraintVariable2[]) fNewConstraintVariables.toArray(new ConstraintVariable2[fNewConstraintVariables.size()]);
//	}
//	
	public ITypeConstraint2[] getNewTypeConstraints() {
		return (ITypeConstraint2[]) fNewTypeConstraints.toArray(new ITypeConstraint2[fNewTypeConstraints.size()]);
	}
	
//	public Set getAllTypeConstraints() {
//		fTypeConstraints.keys();
//	}
	
	
	/**
	 * Controls calculation and storage of information for more readable toString() messages.
	 * <p><em>Warning: This method is for testing purposes only and should not be called except from unit tests.</em></p>
	 * 
	 * @param store <code>true</code> iff information for toString() should be stored 
	 */
	public static void setStoreToString(boolean store) {
		fStoreToString= store;
	}
	
	public void createSubtypeConstraint(ConstraintVariable2 v1, ConstraintVariable2 v2){
		createSimpleTypeConstraint(v1, v2, ConstraintOperator2.createSubTypeOperator());
	}
	
//	public ITypeConstraint2[] createStrictSubtypeConstraint(ConstraintVariable2 v1, ConstraintVariable2 v2){
//		return createSimpleTypeConstraint(v1, v2, ConstraintOperator2.createStrictSubtypeOperator());
//	}
//	
	
	protected void createSimpleTypeConstraint(ConstraintVariable2 cv1, ConstraintVariable2 cv2, ConstraintOperator2 operator) {
		if (! keep(cv1, cv2, operator))
			return;
		
		ConstraintVariable2 storedCv1= storedCv(cv1);
		ConstraintVariable2 storedCv2= storedCv(cv2);
		SimpleTypeConstraint2 typeConstraint= new SimpleTypeConstraint2(storedCv1, storedCv2, operator);
		
		Object storedTc= fTypeConstraints.getKey(typeConstraint);
		if (storedTc == null) {
			fTypeConstraints.put(typeConstraint, NULL);
			fNewTypeConstraints.add(typeConstraint);
		} else {
			typeConstraint= (SimpleTypeConstraint2) storedTc;
		}
		
		registerCvWithTc(storedCv1, typeConstraint);
		registerCvWithTc(storedCv2, typeConstraint);
	}

	private ConstraintVariable2 storedCv(ConstraintVariable2 cv) {
		//TODO: should optimize 'stored()' in better CustomHashSet
		Object stored= fConstraintVariables.getKey(cv);
		if (stored == null) {
			fConstraintVariables.put(cv, NULL);
			fNewConstraintVariables.add(cv);
			return cv;
		} else {
			return (ConstraintVariable2) stored;
		}
	}
	
	private void registerCvWithTc(ConstraintVariable2 storedCv, ITypeConstraint2 typeConstraint) {
		//TODO: special handling for CollectionElementVariable2?
		Object usedIn= storedCv.getData(USED_IN);
		if (usedIn == null) {
			storedCv.setData(USED_IN, typeConstraint);
		} else if (usedIn instanceof ArrayList) {
			ArrayList usedInList= (ArrayList) usedIn;
			usedInList.add(typeConstraint);
		} else {
			ArrayList usedInList= new ArrayList(2);
			usedInList.add(usedIn);
			usedInList.add(typeConstraint);
			storedCv.setData(USED_IN, usedInList);
		}
	}
	
	public void createEqualsConstraint(TypeConstraintVariable2 leftElement, TypeConstraintVariable2 rightElement) {
		if (leftElement == null || rightElement == null)
			return;
		
		EquivalenceRepresentative leftRep= leftElement.getRepresentative();
		EquivalenceRepresentative rightRep= rightElement.getRepresentative();
		if (leftRep == null) {
			if (rightRep == null) {
				EquivalenceRepresentative rep= new EquivalenceRepresentative(leftElement, rightElement);
				fEquivalenceRepresentatives.add(rep);
				leftElement.setRepresentative(rep);
				rightElement.setRepresentative(rep);
			} else {
				rightRep.add(leftElement);
				leftElement.setRepresentative(rightRep);
			}
		} else {
			if (rightRep == null) {
				leftRep.add(rightElement);
				rightElement.setRepresentative(leftRep);
			} else if (leftRep == rightRep) {
				return;
			} else {
				TypeConstraintVariable2[] rightElements= rightRep.getElements();
				leftRep.addAll(rightElements);
				for (int i= 0; i < rightElements.length; i++)
					rightElements[i].setRepresentative(leftRep);
				fEquivalenceRepresentatives.remove(rightRep);
			}
		}
	}
	
	public VariableVariable2 makeVariableVariable(IVariableBinding variableBinding) {
		ITypeBinding typeBinding= variableBinding.getType();
		if (filterConstraintVariableType(typeBinding))
			return null;
		VariableVariable2 cv= new VariableVariable2(fTypeEnvironment.create(typeBinding), variableBinding);
		VariableVariable2 storedCv= (VariableVariable2) storedCv(cv);
		if (storedCv == cv) {
			makeElementVariables(storedCv, typeBinding);
			if (fStoreToString)
				cv.setData(ConstraintVariable2.TO_STRING, '[' + variableBinding.getName() + ']');
		}
		return storedCv;
	}

	public VariableVariable2 makeDeclaredVariableVariable(IVariableBinding variableBinding, ICompilationUnit cu) {
		VariableVariable2 cv= makeVariableVariable(variableBinding);
		if (cv == null)
			return null;
		VariableVariable2 storedCv= (VariableVariable2) registerDeclaredVariable(cv, cu);
		if (! variableBinding.isField())
			fCuScopedConstraintVariables.add(storedCv);
		return storedCv;
	}
	
	public TypeVariable2 makeTypeVariable(Type type) {
		ITypeBinding typeBinding= type.resolveBinding();
		if (filterConstraintVariableType(typeBinding))
			return null;
		ICompilationUnit cu= RefactoringASTParser.getCompilationUnit(type);
		CompilationUnitRange range= new CompilationUnitRange(cu, type);
		TypeVariable2 typeVariable= new TypeVariable2(fTypeEnvironment.create(typeBinding), range);
		TypeVariable2 storedCv= (TypeVariable2) storedCv(typeVariable);
		if (storedCv == typeVariable) {
			fCuScopedConstraintVariables.add(storedCv);
			if (isAGenericType(typeBinding))
				makeElementVariables(storedCv, typeBinding);
			if (fStoreToString)
				storedCv.setData(ConstraintVariable2.TO_STRING, type.toString());
		}
		return storedCv;
	}

	public IndependentTypeVariable2 makeIndependentTypeVariable(ITypeBinding typeBinding) {
		//TODO: prune if unused!
		IndependentTypeVariable2 cv= new IndependentTypeVariable2(fTypeEnvironment.create(typeBinding));
		IndependentTypeVariable2 storedCv= (IndependentTypeVariable2) storedCv(cv);
		if (cv == storedCv) {
			//TODO: infinite recursion
//			if (isAGenericType(typeBinding))
//				makeElementVariables(storedCv, typeBinding);
			if (fStoreToString)
				storedCv.setData(ConstraintVariable2.TO_STRING, "IndependentType(" + Bindings.asString(typeBinding) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return storedCv;
	}
		
	public ParameterizedTypeVariable2 makeParameterizedTypeVariable(ITypeBinding typeBinding) {
		//TODO: prune if unused!
		ParameterizedTypeVariable2 cv= new ParameterizedTypeVariable2(fTypeEnvironment.create(typeBinding));
		ParameterizedTypeVariable2 storedCv= (ParameterizedTypeVariable2) storedCv(cv);
		if (cv == storedCv) {
			makeElementVariables(storedCv, typeBinding);
			if (fStoreToString)
				storedCv.setData(ConstraintVariable2.TO_STRING, "ParameterizedType(" + Bindings.asString(typeBinding) + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return storedCv;
	}
		
	public ParameterTypeVariable2 makeParameterTypeVariable(IMethodBinding methodBinding, int parameterIndex) {
		ITypeBinding typeBinding= methodBinding.getParameterTypes() [parameterIndex];
		if (filterConstraintVariableType(typeBinding))
			return null;
		ParameterTypeVariable2 cv= new ParameterTypeVariable2(
			fTypeEnvironment.create(typeBinding), parameterIndex, methodBinding);
		ParameterTypeVariable2 storedCv= (ParameterTypeVariable2) storedCv(cv);
		if (storedCv == cv) {
			makeElementVariables(storedCv, typeBinding);
			if (fStoreToString)
				storedCv.setData(ConstraintVariable2.TO_STRING, "[Parameter(" + parameterIndex + "," + Bindings.asString(methodBinding) + ")]"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		return storedCv;
	}
	
	/**
	 * Make a ParameterTypeVariable2 from a method declaration.
	 * The constraint variable is always stored if it passes the type filter.
	 * @param methodBinding
	 * @param parameterIndex
	 * @param cu
	 * @return the ParameterTypeVariable2, or <code>null</code> 
	 */
	public ParameterTypeVariable2 makeDeclaredParameterTypeVariable(IMethodBinding methodBinding, int parameterIndex, ICompilationUnit cu) {
		ParameterTypeVariable2 cv= makeParameterTypeVariable(methodBinding, parameterIndex);
		if (cv == null)
			return null;
		ParameterTypeVariable2 storedCv= (ParameterTypeVariable2) registerDeclaredVariable(cv, cu);
		//TODO: spread such checks:
		if (methodBinding.getDeclaringClass().isLocal() || Modifier.isPrivate(methodBinding.getModifiers()))
			fCuScopedConstraintVariables.add(storedCv);
		return storedCv;
	}

	private IDeclaredConstraintVariable registerDeclaredVariable(IDeclaredConstraintVariable cv, ICompilationUnit unit) {
		if (cv == null)
			return null;
		
		IDeclaredConstraintVariable storedCv= (IDeclaredConstraintVariable) fConstraintVariables.getKey(cv);
		if (storedCv == null) {
			//TODO: should always be the case now
			storedCv= cv;
			fNewConstraintVariables.add(cv);
		}
		storedCv.setCompilationUnit(unit);
		return storedCv;
	}

	public ReturnTypeVariable2 makeReturnTypeVariable(IMethodBinding methodBinding) {
		ITypeBinding returnTypeBinding= methodBinding.getReturnType();
		if (filterConstraintVariableType(returnTypeBinding))
			return null;
		ReturnTypeVariable2 cv= new ReturnTypeVariable2(fTypeEnvironment.create(returnTypeBinding), methodBinding);
		ReturnTypeVariable2 storedCv= (ReturnTypeVariable2) storedCv(cv);
		if (cv == storedCv) {
			makeElementVariables(storedCv, returnTypeBinding);
			if (fStoreToString)
				storedCv.setData(ConstraintVariable2.TO_STRING, "[ReturnType(" + Bindings.asString(methodBinding) + ")]"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return storedCv;
	}

	public ReturnTypeVariable2 makeDeclaredReturnTypeVariable(IMethodBinding methodBinding, ICompilationUnit unit) {
		ReturnTypeVariable2 cv= makeReturnTypeVariable(methodBinding);
		ReturnTypeVariable2 storedCv= (ReturnTypeVariable2) registerDeclaredVariable(cv, unit);
		if (cv == null)
			return null;
		if (methodBinding.getDeclaringClass().isLocal())
			fCuScopedConstraintVariables.add(storedCv);
		return storedCv;
	}
	
	public PlainTypeVariable2 makePlainTypeVariable(ITypeBinding typeBinding) {
		if (filterConstraintVariableType(typeBinding))
			return null;
		PlainTypeVariable2 cv= new PlainTypeVariable2(fTypeEnvironment.create(typeBinding));
		cv= (PlainTypeVariable2) storedCv(cv);
		return cv;
	}
	
	private void makeElementVariables(TypeConstraintVariable2 expressionCv, ITypeBinding typeBinding) {
		makeElementVariables(expressionCv, typeBinding, true);
	}
	
	/**
	 * Make element variables for type variables declared in typeBinding.
	 * 
	 * @param expressionCv the type constraint variable
	 * @param typeBinding the type binding to fetch type variables from
	 * @param isDeclaration <code>true</code> iff typeBinding is the base type of expressionCv
	 */
	private void makeElementVariables(TypeConstraintVariable2 expressionCv, ITypeBinding typeBinding, boolean isDeclaration) {
		//TODO: element variables for type variables of enclosing types and methods
		if (isAGenericType(typeBinding)) {
			typeBinding= typeBinding.getTypeDeclaration();
			ITypeBinding[] typeParameters= typeBinding.getTypeParameters();
			for (int i= 0; i < typeParameters.length; i++) {
				makeElementVariable(expressionCv, typeParameters[i], isDeclaration ? i : CollectionElementVariable2.NOT_DECLARED_TYPE_VARIABLE_INDEX);
				if (typeParameters[i].getTypeBounds().length != 0) {
					int debugTarget= 0;
					//TODO: create subtype constraints for bounds
				}
			}
		}
		
		ITypeBinding superclass= typeBinding.getSuperclass();
		if (superclass != null) {
			//TODO: don't create new CollectionElementVariables. Instead, reuse existing (add to map with new key)
			makeElementVariables(expressionCv, superclass, false);
			createTypeVariablesEqualityConstraints(expressionCv, superclass);
		}
		
		ITypeBinding[] interfaces= typeBinding.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			makeElementVariables(expressionCv, interfaces[i], false);
			createTypeVariablesEqualityConstraints(expressionCv, interfaces[i]);
		}
	}

	private void createTypeVariablesEqualityConstraints(TypeConstraintVariable2 expressionCv, ITypeBinding reference) {
		createTypeVariablesEqualityConstraints(expressionCv, expressionCv, reference);
	}

	/**
	 * Create equality constraints between generic type variables of expressionCv and referenceCv.
	 * For example, the generic interface <code>java.lang.Iterable&lt;E&gt;</code> defines a method
	 * <code>Iterator&lt;E&gt; iterator()</code>. Given
	 * <ul>
	 *   <li>an expressionCv of a subtype of <code>Iterable</code>,</li>
	 *   <li>a referenceCv of a subtype of <code>Iterator</code>, and</li>
	 *   <li>a reference binding of the Iterable#iterator()'s return type (the parameterized type <code>Iterator&lt;E&gt;</code>),</li>
	 * </ul>
	 * this method creates an equality constraint between the type variable E in expressionCV and
	 * the type variable E in referenceCV.
	 * 
	 * @param expressionCv the type constraint variable of an expression
	 * @param referenceCv the type constraint variable of a type reference
	 * @param reference the declared type reference
	 */
	public void createTypeVariablesEqualityConstraints(TypeConstraintVariable2 expressionCv, TypeConstraintVariable2 referenceCv, ITypeBinding reference) {
		if (reference.isParameterizedType() || reference.isRawType()) {
			ITypeBinding[] referenceTypeArguments= reference.getTypeArguments();
			ITypeBinding[] referenceTypeParameters= reference.getTypeDeclaration().getTypeParameters();
			for (int i= 0; i < referenceTypeParameters.length; i++) {
				ITypeBinding referenceTypeArgument= referenceTypeArguments[i];
				ITypeBinding referenceTypeParameter= referenceTypeParameters[i];
				TypeConstraintVariable2 referenceTypeArgumentCv;
				if (referenceTypeArgument.isTypeVariable()) {
					referenceTypeArgumentCv= getElementVariable(expressionCv, referenceTypeArgument);
				} else if (referenceTypeArgument.isWildcardType()) {
					referenceTypeArgumentCv= null; //TODO: make new WildcardTypeVariable, which is compatible to nothing 
				} else {
					referenceTypeArgumentCv= makeIndependentTypeVariable(referenceTypeArgument);
				}
				CollectionElementVariable2 referenceTypeParametersCv= getElementVariable(referenceCv, referenceTypeParameter);
				createEqualsConstraint(referenceTypeArgumentCv, referenceTypeParametersCv);
			}
		}
	}

//	public void createEqualsConstraint(TypeConstraintVariable2 cv1, TypeConstraintVariable2 cv2) {
//		if (cv1 instanceof CollectionElementVariable2 && cv2 instanceof CollectionElementVariable2)
//			createEqualsConstraint((CollectionElementVariable2) cv1, (CollectionElementVariable2) cv2);
//		else {
//			// TODO: cannot have equality constraints on TypeConstraintVariable2. Must be relaxed!
//			// hackaround: 2 subtype constraints:
//			createSubtypeConstraint(cv1, cv2);
//			createSubtypeConstraint(cv2, cv1);
//		}
//	}

	private CollectionElementVariable2 makeElementVariable(TypeConstraintVariable2 expressionCv, ITypeBinding typeVariable, int declarationTypeVariableIndex) {
		//TODO: unhack!!!
		if (expressionCv == null)
			return null;
		
		CollectionElementVariable2 storedElementVariable= getElementVariable(expressionCv, typeVariable);
		if (storedElementVariable != null)
			return storedElementVariable;
		
		if (isAGenericType(expressionCv.getType())) {
			CollectionElementVariable2 cv= new CollectionElementVariable2(expressionCv, typeVariable, declarationTypeVariableIndex);
			cv= (CollectionElementVariable2) storedCv(cv); //TODO: Should not use storedCv(..) here!
			setElementVariable(expressionCv, cv, typeVariable);
//			if (fStoreToString)
//				cv.setData(ConstraintVariable2.TO_STRING, "Elem[" + expressionCv.toString() + "]"); //$NON-NLS-1$ //$NON-NLS-2$
			return cv;
		} else {
			return null;
		}
	}
	
	private void setElementVariable(TypeConstraintVariable2 typeConstraintVariable, CollectionElementVariable2 elementVariable, ITypeBinding typeVariable) {
		HashMap keyToElementVar= (HashMap) typeConstraintVariable.getData(INDEXED_COLLECTION_ELEMENTS);
		String key= typeVariable.getKey();
		if (keyToElementVar == null) {
			keyToElementVar= new HashMap();
			typeConstraintVariable.setData(INDEXED_COLLECTION_ELEMENTS, keyToElementVar);
		} else {
			Assert.isTrue(! keyToElementVar.containsKey(key));
		}
		keyToElementVar.put(key, elementVariable);
	}
	
	public CollectionElementVariable2 getElementVariable(ConstraintVariable2 constraintVariable, ITypeBinding typeVariable) {
		Assert.isTrue(typeVariable.isTypeVariable()); // includes null check
		HashMap typeVarToElementVars= (HashMap) constraintVariable.getData(INDEXED_COLLECTION_ELEMENTS);
		if (typeVarToElementVars == null)
			return null;
		return (CollectionElementVariable2) typeVarToElementVars.get(typeVariable.getKey());
	}

	public Map/*<String typeVariableKey, CollectionElementVariable2>*/ getElementVariables(ConstraintVariable2 constraintVariable) {
		//TODO: null check should be done on client side!
//		if (constraintVariable == null)
//			return null;
		Map elementVariables= (Map) constraintVariable.getData(INDEXED_COLLECTION_ELEMENTS);
		if (elementVariables == null)
			return EMPTY_COLLECTION_ELEMENT_VARIABLES_MAP;
		else
			return elementVariables;
	}
	
	public boolean isAGenericType(TType type) {
		return type.isGenericType()
				|| type.isParameterizedType()
				|| type.isRawType();
	}

	public boolean isAGenericType(ITypeBinding type) {
		return type.isGenericType()
				|| type.isParameterizedType()
				|| type.isRawType();
	}

	public void makeCastVariable(CastExpression castExpression, TypeConstraintVariable2 expressionCv) {
		ITypeBinding typeBinding= castExpression.resolveTypeBinding();
		ICompilationUnit cu= RefactoringASTParser.getCompilationUnit(castExpression);
		CompilationUnitRange range= new CompilationUnitRange(cu, castExpression);
		CastVariable2 castCv= new CastVariable2(fTypeEnvironment.create(typeBinding), range, expressionCv);
		fCastVariables.add(castCv);
	}
	
}
