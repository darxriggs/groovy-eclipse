/*
 * Copyright 2008-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.transform;

import groovy.lang.Immutable;
import groovy.lang.MetaClass;
import groovy.lang.MissingPropertyException;
import groovy.lang.ReadOnlyPropertyException;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.util.HashCodeHelper;
import org.objectweb.asm.Opcodes;

import java.util.*;

/**
 * Handles generation of code for the @Immutable annotation.
 *
 * @author Paul King
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class ImmutableASTTransformation implements ASTTransformation, Opcodes {

    /*
      Currently leaving BigInteger and BigDecimal in list but see:
      http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6348370

      Also, Color is not final so while not normally used with child
      classes, it isn't strictly immutable. Use at your own risk.
     */
    private static List<String> immutableList = Arrays.asList(
            "java.lang.Boolean",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.String",
            "java.math.BigInteger",
            "java.math.BigDecimal",
            "java.awt.Color",
            "java.net.URI"
    );
    private static final Class MY_CLASS = Immutable.class;
    private static final ClassNode MY_TYPE = new ClassNode(MY_CLASS);
    private static final String MY_TYPE_NAME = "@" + MY_TYPE.getNameWithoutPackage();
    private static final ClassNode OBJECT_TYPE = new ClassNode(Object.class);
    private static final ClassNode HASHMAP_TYPE = new ClassNode(HashMap.class);
    private static final ClassNode MAP_TYPE = new ClassNode(Map.class);
    private static final ClassNode DATE_TYPE = new ClassNode(Date.class);
    private static final ClassNode CLONEABLE_TYPE = new ClassNode(Cloneable.class);
    private static final ClassNode COLLECTION_TYPE = new ClassNode(Collection.class);
    private static final ClassNode HASHUTIL_TYPE = new ClassNode(HashCodeHelper.class);
    private static final ClassNode STRINGBUFFER_TYPE = new ClassNode(StringBuffer.class);
    private static final ClassNode READONLYEXCEPTION_TYPE = new ClassNode(ReadOnlyPropertyException.class);
    private static final ClassNode DGM_TYPE = new ClassNode(DefaultGroovyMethods.class);
    private static final ClassNode INVOKER_TYPE = new ClassNode(InvokerHelper.class);
    private static final ClassNode SELF_TYPE = new ClassNode(ImmutableASTTransformation.class);
    private static final Token COMPARE_EQUAL = Token.newSymbol(Types.COMPARE_EQUAL, -1, -1);
    private static final Token COMPARE_NOT_EQUAL = Token.newSymbol(Types.COMPARE_NOT_EQUAL, -1, -1);
    private static final Token ASSIGN = Token.newSymbol(Types.ASSIGN, -1, -1);

    public void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes));
        }

        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode node = (AnnotationNode) nodes[0];
        if (!MY_TYPE.equals(node.getClassNode())) return;
        List<PropertyNode> newNodes = new ArrayList<PropertyNode>();

        if (parent instanceof ClassNode) {
            ClassNode cNode = (ClassNode) parent;
            String cName = cNode.getName();
            if (cNode.isInterface()) {
                throw new RuntimeException("Error processing interface '" + cName + "'. " + MY_TYPE_NAME + " not allowed for interfaces.");
            }
            if ((cNode.getModifiers() & ACC_FINAL) == 0) {
                cNode.setModifiers(cNode.getModifiers() | ACC_FINAL);
            }

            final List<PropertyNode> pList = getInstanceProperties(cNode);
            for (PropertyNode pNode : pList) {
                adjustPropertyForImmutability(pNode, newNodes);
            }
            for (PropertyNode pNode : newNodes) {
                cNode.getProperties().remove(pNode);
                addProperty(cNode, pNode);
            }
            final List<FieldNode> fList = cNode.getFields();
            for (FieldNode fNode : fList) {
                ensureNotPublic(cName, fNode);
            }
            createConstructor(cNode);
            createHashCode(cNode);
            createEquals(cNode);
            createToString(cNode);
        }
    }

    private boolean hasDeclaredMethod(ClassNode cNode, String name, int argsCount) {
        List<MethodNode> ms = cNode.getDeclaredMethods(name);
        for(MethodNode m : ms) {
           Parameter[] paras = m.getParameters();
           if(paras != null && paras.length == argsCount) {
                return true;
           }
        }
        return false;
    }

    private void ensureNotPublic(String cNode, FieldNode fNode) {
        String fName = fNode.getName();
        // TODO: do we need to lock down things like: $ownClass
        if (fNode.isPublic() && !fName.contains("$")) {
            throw new RuntimeException("Public field '" + fName + "' not allowed for " + MY_TYPE_NAME + " class '" + cNode + "'.");
        }
    }

    private void createHashCode(ClassNode cNode) {
        // make a public method if none exists otherwise try a private method with leading underscore
        boolean hasExistingHashCode = hasDeclaredMethod(cNode, "hashCode", 0);
        if (hasExistingHashCode && hasDeclaredMethod(cNode, "_hashCode", 0)) return;

        final FieldNode hashField = cNode.addField("$hash$code", ACC_PRIVATE | ACC_SYNTHETIC, ClassHelper.int_TYPE, null);
        final BlockStatement body = new BlockStatement();
        final Expression hash = new VariableExpression(hashField);
        final List<PropertyNode> list = getInstanceProperties(cNode);

        body.addStatement(new IfStatement(
                isZeroExpr(hash),
                calculateHashStatements(hash, list),
                new EmptyStatement()
        ));

        body.addStatement(new ReturnStatement(hash));

        cNode.addMethod(new MethodNode(hasExistingHashCode ? "_hashCode" : "hashCode", hasExistingHashCode ? ACC_PRIVATE : ACC_PUBLIC,
                ClassHelper.int_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, body));
    }

    private void createToString(ClassNode cNode) {
        // make a public method if none exists otherwise try a private method with leading underscore
        boolean hasExistingToString = hasDeclaredMethod(cNode, "toString", 0);
        if (hasExistingToString && hasDeclaredMethod(cNode, "_toString", 0)) return;
        
        final BlockStatement body = new BlockStatement();
        final List<PropertyNode> list = getInstanceProperties(cNode);
        // def _result = new StringBuffer()
        final Expression result = new VariableExpression("_result");
        final Expression init = new ConstructorCallExpression(STRINGBUFFER_TYPE, MethodCallExpression.NO_ARGUMENTS);
        body.addStatement(new ExpressionStatement(new DeclarationExpression(result, ASSIGN, init)));

        body.addStatement(append(result, new ConstantExpression(cNode.getName())));
        body.addStatement(append(result, new ConstantExpression("(")));
        boolean first = true;
        for (PropertyNode pNode : list) {
            if (first) {
                first = false;
            } else {
                body.addStatement(append(result, new ConstantExpression(", ")));
            }
            body.addStatement(new IfStatement(
                    new BooleanExpression(new VariableExpression(cNode.getField("$map$constructor"))),
                    toStringPropertyName(result, pNode.getName()),
                    new EmptyStatement()
            ));
            final Expression fieldExpr = new VariableExpression(pNode.getField());
            body.addStatement(append(result, new StaticMethodCallExpression(INVOKER_TYPE, "toString", fieldExpr)));
        }
        body.addStatement(append(result, new ConstantExpression(")")));
        body.addStatement(new ReturnStatement(new MethodCallExpression(result, "toString", MethodCallExpression.NO_ARGUMENTS)));
        cNode.addMethod(new MethodNode(hasExistingToString ? "_toString" : "toString", hasExistingToString ? ACC_PRIVATE : ACC_PUBLIC,
                ClassHelper.STRING_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, body));
    }

    private Statement toStringPropertyName(Expression result, String fName) {
        final BlockStatement body = new BlockStatement();
        body.addStatement(append(result, new ConstantExpression(fName)));
        body.addStatement(append(result, new ConstantExpression(":")));
        return body;
    }

    private ExpressionStatement append(Expression result, Expression expr) {
        return new ExpressionStatement(new MethodCallExpression(result, "append", expr));
    }

    private Statement calculateHashStatements(Expression hash, List<PropertyNode> list) {
        final BlockStatement body = new BlockStatement();
        // def _result = HashCodeHelper.initHash()
        final Expression result = new VariableExpression("_result");
        final Expression init = new StaticMethodCallExpression(HASHUTIL_TYPE, "initHash", MethodCallExpression.NO_ARGUMENTS);
        body.addStatement(new ExpressionStatement(new DeclarationExpression(result, ASSIGN, init)));

        // fields
        for (PropertyNode pNode : list) {
            // _result = HashCodeHelper.updateHash(_result, field)
            final Expression fieldExpr = new VariableExpression(pNode.getField());
            final Expression args = new TupleExpression(result, fieldExpr);
            final Expression current = new StaticMethodCallExpression(HASHUTIL_TYPE, "updateHash", args);
            body.addStatement(assignStatement(result, current));
        }
        // $hash$code = _result
        body.addStatement(assignStatement(hash, result));
        return body;
    }

    private void createEquals(ClassNode cNode) {
        // make a public method if none exists otherwise try a private method with leading underscore
        boolean hasExistingEquals = hasDeclaredMethod(cNode, "equals", 1);
        if (hasExistingEquals && hasDeclaredMethod(cNode, "_equals", 1)) return;

        final BlockStatement body = new BlockStatement();
        Expression other = new VariableExpression("other");

        // some short circuit cases for efficiency
        body.addStatement(returnFalseIfNull(other));
        body.addStatement(returnFalseIfWrongType(cNode, other));
        body.addStatement(returnTrueIfIdentical(VariableExpression.THIS_EXPRESSION, other));

        body.addStatement(new ExpressionStatement(new BinaryExpression(other, ASSIGN, new CastExpression(cNode, other))));

        final List<PropertyNode> list = getInstanceProperties(cNode);
        // fields
        for (PropertyNode pNode : list) {
            body.addStatement(returnFalseIfPropertyNotEqual(pNode, other));
        }

        // default
        body.addStatement(new ReturnStatement(ConstantExpression.TRUE));

        Parameter[] params = {new Parameter(OBJECT_TYPE, "other")};
        cNode.addMethod(new MethodNode(hasExistingEquals ? "_equals" : "equals", hasExistingEquals ? ACC_PRIVATE : ACC_PUBLIC,
                ClassHelper.boolean_TYPE, params, ClassNode.EMPTY_ARRAY, body));
    }

    private Statement returnFalseIfWrongType(ClassNode cNode, Expression other) {
        return new IfStatement(
                notEqualClasses(cNode, other),
                new ReturnStatement(ConstantExpression.FALSE),
                new EmptyStatement()
        );
    }

    private IfStatement returnFalseIfNull(Expression other) {
        return new IfStatement(
                equalsNullExpr(other),
                new ReturnStatement(ConstantExpression.FALSE),
                new EmptyStatement()
        );
    }

    private IfStatement returnTrueIfIdentical(Expression self, Expression other) {
        return new IfStatement(
                identicalExpr(self, other),
                new ReturnStatement(ConstantExpression.TRUE),
                new EmptyStatement()
        );
    }

    private Statement returnFalseIfPropertyNotEqual(PropertyNode pNode, Expression other) {
        return new IfStatement(
                notEqualsExpr(pNode, other),
                new ReturnStatement(ConstantExpression.FALSE),
                new EmptyStatement()
        );
    }

    private void addProperty(ClassNode cNode, PropertyNode pNode) {
        final FieldNode fn = pNode.getField();
        cNode.getFields().remove(fn);
        cNode.addProperty(pNode.getName(), pNode.getModifiers() | ACC_FINAL, pNode.getType(),
                pNode.getInitialExpression(), pNode.getGetterBlock(), pNode.getSetterBlock());
        final FieldNode newfn = cNode.getField(fn.getName());
        cNode.getFields().remove(newfn);
        cNode.addField(fn);
    }

    private void createConstructor(ClassNode cNode) {
        // pretty toString will remember how the user declared the params and print accordingly
        final FieldNode constructorField = cNode.addField("$map$constructor", ACC_PRIVATE | ACC_SYNTHETIC, ClassHelper.boolean_TYPE, null);
        final Expression constructorStyle = new VariableExpression(constructorField);
        if (cNode.getDeclaredConstructors().size() != 0) {
            // TODO: allow constructors which call provided constructor?
            throw new RuntimeException("Explicit constructors not allowed for " + MY_TYPE_NAME + " class: " + cNode.getNameWithoutPackage());
        }

        List<PropertyNode> list = getInstanceProperties(cNode);
        boolean specialHashMapCase = list.size() == 1 && list.get(0).getField().getType().equals(HASHMAP_TYPE);
        if (specialHashMapCase) {
            createConstructorMapSpecial(cNode, constructorStyle, list);
        } else {
            createConstructorMap(cNode, constructorStyle, list);
            createConstructorOrdered(cNode, constructorStyle, list);
        }
    }

    private List<PropertyNode> getInstanceProperties(ClassNode cNode) {
        final List<PropertyNode> result = new ArrayList<PropertyNode>();
        for (PropertyNode pNode : cNode.getProperties()) {
            if (!pNode.isStatic()) {
                result.add(pNode);
            }
        }
        return result;
    }

    private void createConstructorMapSpecial(ClassNode cNode, Expression constructorStyle, List<PropertyNode> list) {
        final BlockStatement body = new BlockStatement();
        body.addStatement(createConstructorStatementMapSpecial(list.get(0).getField()));
        createConstructorMapCommon(cNode, constructorStyle, body);
    }

    private void createConstructorMap(ClassNode cNode, Expression constructorStyle, List<PropertyNode> list) {
        final BlockStatement body = new BlockStatement();
        for (PropertyNode pNode : list) {
            body.addStatement(createConstructorStatement(cNode, pNode));
        }
        // check for missing properties
        Expression checkArgs = new ArgumentListExpression(new VariableExpression("this"), new VariableExpression("args"));
        body.addStatement(new ExpressionStatement(new StaticMethodCallExpression(SELF_TYPE, "checkPropNames", checkArgs)));
        createConstructorMapCommon(cNode, constructorStyle, body);
    }

    private void createConstructorMapCommon(ClassNode cNode, Expression constructorStyle, BlockStatement body) {
        final List<FieldNode> fList = cNode.getFields();
        for (FieldNode fNode : fList) {
            if (fNode.isPublic()) continue; // public fields will be rejected elsewhere
            if (cNode.getProperty(fNode.getName()) != null) continue; // a property
            if (fNode.isFinal() && fNode.isStatic()) continue;
            if (fNode.getName().contains("$")) continue; // internal field
            if (fNode.isFinal() && fNode.getInitialExpression() != null) body.addStatement(checkFinalArgNotOverridden(cNode, fNode));
            body.addStatement(createConstructorStatementDefault(fNode));
        }
        body.addStatement(assignStatement(constructorStyle, ConstantExpression.TRUE));
        final Parameter[] params = new Parameter[]{new Parameter(HASHMAP_TYPE, "args")};
        cNode.addConstructor(new ConstructorNode(ACC_PUBLIC, params, ClassNode.EMPTY_ARRAY, new IfStatement(
                equalsNullExpr(new VariableExpression("args")),
                new EmptyStatement(),
                body)));
    }

    private Statement checkFinalArgNotOverridden(ClassNode cNode, FieldNode fNode) {
        final String name = fNode.getName();
        Expression value = findArg(name);
        return new IfStatement(
                equalsNullExpr(value),
                new EmptyStatement(),
                new ThrowStatement(new ConstructorCallExpression(READONLYEXCEPTION_TYPE,
                        new ArgumentListExpression(new ConstantExpression(name),
                                new ConstantExpression(cNode.getName())))));
    }

    private void createConstructorOrdered(ClassNode cNode, Expression constructorStyle, List<PropertyNode> list) {
        final MapExpression argMap = new MapExpression();
        final Parameter[] orderedParams = new Parameter[list.size()];
        int index = 0;
        for (PropertyNode pNode : list) {
            orderedParams[index++] = new Parameter(pNode.getField().getType(), pNode.getField().getName());
            argMap.addMapEntryExpression(new ConstantExpression(pNode.getName()), new VariableExpression(pNode.getName()));
        }
        final BlockStatement orderedBody = new BlockStatement();
        orderedBody.addStatement(new ExpressionStatement(
                new ConstructorCallExpression(ClassNode.THIS, new ArgumentListExpression(new CastExpression(HASHMAP_TYPE, argMap)))
        ));
        orderedBody.addStatement(assignStatement(constructorStyle, ConstantExpression.FALSE));
        cNode.addConstructor(new ConstructorNode(ACC_PUBLIC, orderedParams, ClassNode.EMPTY_ARRAY, orderedBody));
    }

    private Statement createConstructorStatement(ClassNode cNode, PropertyNode pNode) {
        FieldNode fNode = pNode.getField();
        final ClassNode fieldType = fNode.getType();
        final Statement statement;
        if (fieldType.isArray() || fieldType.implementsInterface(CLONEABLE_TYPE)) {
            statement = createConstructorStatementArrayOrCloneable(fNode);
        } else if (fieldType.isDerivedFrom(DATE_TYPE)) {
            statement = createConstructorStatementDate(fNode);
        } else if (isOrImplements(fieldType, COLLECTION_TYPE) || isOrImplements(fieldType, MAP_TYPE)) {
            statement = createConstructorStatementCollection(fNode);
        } else if (isKnownImmutable(fieldType)) {
            statement = createConstructorStatementDefault(fNode);
        } else if (fieldType.isResolved()) {
            throw new RuntimeException(createErrorMessage(cNode.getName(), fNode.getName(), fieldType.getName(), "compiling"));
        } else {
            statement = createConstructorStatementGuarded(cNode, fNode);
        }
        return statement;
    }

    private boolean isOrImplements(ClassNode fieldType, ClassNode interfaceType) {
        return fieldType.equals(interfaceType) || fieldType.implementsInterface(interfaceType);
    }

    private Statement createConstructorStatementGuarded(ClassNode cNode, FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        Expression initExpr = fNode.getInitialValueExpression();
        if (initExpr == null) initExpr = ConstantExpression.NULL;
        Expression unknown = findArg(fNode.getName());
        return new IfStatement(
                equalsNullExpr(unknown),
                new IfStatement(
                        equalsNullExpr(initExpr),
                        new EmptyStatement(),
                        assignStatement(fieldExpr, checkUnresolved(cNode, fNode, initExpr))),
                assignStatement(fieldExpr, checkUnresolved(cNode, fNode, unknown)));
    }

    private Expression checkUnresolved(ClassNode cNode, FieldNode fNode, Expression value) {
        Expression args = new TupleExpression(new ConstantExpression(cNode.getName()), new ConstantExpression(fNode.getName()), value);
        return new StaticMethodCallExpression(SELF_TYPE, "checkImmutable", args);
    }

    private Statement createConstructorStatementCollection(FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        Expression initExpr = fNode.getInitialValueExpression();
        if (initExpr == null) initExpr = ConstantExpression.NULL;
        Expression collection = findArg(fNode.getName());
        return new IfStatement(
                equalsNullExpr(collection),
                new IfStatement(
                        equalsNullExpr(initExpr),
                        new EmptyStatement(),
                        assignStatement(fieldExpr, cloneCollectionExpr(initExpr))),
                assignStatement(fieldExpr, cloneCollectionExpr(collection)));
    }

    private Statement createConstructorStatementMapSpecial(FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        Expression initExpr = fNode.getInitialValueExpression();
        if (initExpr == null) initExpr = ConstantExpression.NULL;
        Expression namedArgs = findArg(fNode.getName());
        Expression baseArgs = new VariableExpression("args");
        return new IfStatement(
                equalsNullExpr(baseArgs),
                new IfStatement(
                        equalsNullExpr(initExpr),
                        new EmptyStatement(),
                        assignStatement(fieldExpr, cloneCollectionExpr(initExpr))),
                new IfStatement(
                        equalsNullExpr(namedArgs),
                        new IfStatement(
                                isTrueExpr(new MethodCallExpression(baseArgs, "containsKey", new ConstantExpression(fNode.getName()))),
                                assignStatement(fieldExpr, namedArgs),
                                assignStatement(fieldExpr, cloneCollectionExpr(baseArgs))),
                        new IfStatement(
                                isOneExpr(new MethodCallExpression(baseArgs, "size", MethodCallExpression.NO_ARGUMENTS)),
                                assignStatement(fieldExpr, cloneCollectionExpr(namedArgs)),
                                assignStatement(fieldExpr, cloneCollectionExpr(baseArgs)))
                )
        );
    }

    private boolean isKnownImmutable(ClassNode fieldType) {
        if (!fieldType.isResolved()) return false;
        // GRECLIPSE: start
        /*{
        return fieldType.isEnum() ||
                ClassHelper.isPrimitiveType(fieldType) ||
                inImmutableList(fieldType.getName());
        }*/
        // new
        String s= fieldType.getName();
        return fieldType.isPrimitive() || fieldType.isEnum() || inImmutableList(fieldType.getName());
        // end
    }

    private static boolean inImmutableList(String typeName) {
        return immutableList.contains(typeName);
    }

    private Statement createConstructorStatementDefault(FieldNode fNode) {
        final String name = fNode.getName();
        final Expression fieldExpr = new PropertyExpression(VariableExpression.THIS_EXPRESSION, name);
        Expression initExpr = fNode.getInitialValueExpression();
        if (initExpr == null) initExpr = ConstantExpression.NULL;
        final Expression value = findArg(fNode.getName());
        return new IfStatement(
                equalsNullExpr(value),
                new IfStatement(
                        equalsNullExpr(initExpr),
                        new EmptyStatement(),
                        assignStatement(fieldExpr, initExpr)),
                assignStatement(fieldExpr, value));
    }

    private Statement createConstructorStatementArrayOrCloneable(FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        Expression initExpr = fNode.getInitialValueExpression();
        if (initExpr == null) initExpr = ConstantExpression.NULL;
        final Expression array = findArg(fNode.getName());
        return new IfStatement(
                equalsNullExpr(array),
                new IfStatement(
                        equalsNullExpr(initExpr),
                        assignStatement(fieldExpr, ConstantExpression.NULL),
                        assignStatement(fieldExpr, cloneArrayOrCloneableExpr(initExpr))),
                assignStatement(fieldExpr, cloneArrayOrCloneableExpr(array)));
    }

    private Statement createConstructorStatementDate(FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        Expression initExpr = fNode.getInitialValueExpression();
        if (initExpr == null) initExpr = ConstantExpression.NULL;
        final Expression date = findArg(fNode.getName());
        return new IfStatement(
                equalsNullExpr(date),
                new IfStatement(
                        equalsNullExpr(initExpr),
                        assignStatement(fieldExpr, ConstantExpression.NULL),
                        assignStatement(fieldExpr, cloneDateExpr(initExpr))),
                assignStatement(fieldExpr, cloneDateExpr(date)));
    }

    private Expression cloneDateExpr(Expression origDate) {
        return new ConstructorCallExpression(DATE_TYPE,
                new MethodCallExpression(origDate, "getTime", MethodCallExpression.NO_ARGUMENTS));
    }

    private Statement assignStatement(Expression fieldExpr, Expression value) {
        return new ExpressionStatement(assignExpr(fieldExpr, value));
    }

    private Expression assignExpr(Expression fieldExpr, Expression value) {
        return new BinaryExpression(fieldExpr, ASSIGN, value);
    }

    private BooleanExpression equalsNullExpr(Expression argExpr) {
        return new BooleanExpression(new BinaryExpression(argExpr, COMPARE_EQUAL, ConstantExpression.NULL));
    }

    private BooleanExpression isTrueExpr(Expression argExpr) {
        return new BooleanExpression(new BinaryExpression(argExpr, COMPARE_EQUAL, ConstantExpression.TRUE));
    }

    private BooleanExpression isZeroExpr(Expression expr) {
        return new BooleanExpression(new BinaryExpression(expr, COMPARE_EQUAL, new ConstantExpression(0)));
    }

    private BooleanExpression isOneExpr(Expression expr) {
        return new BooleanExpression(new BinaryExpression(expr, COMPARE_EQUAL, new ConstantExpression(1)));
    }

    private BooleanExpression notEqualsExpr(PropertyNode pNode, Expression other) {
        final Expression fieldExpr = new VariableExpression(pNode.getField());
        final Expression otherExpr = new PropertyExpression(other, pNode.getField().getName());
        return new BooleanExpression(new BinaryExpression(fieldExpr, COMPARE_NOT_EQUAL, otherExpr));
    }

    private BooleanExpression identicalExpr(Expression self, Expression other) {
        return new BooleanExpression(new MethodCallExpression(self, "is", new ArgumentListExpression(other)));
    }

    private BooleanExpression notEqualClasses(ClassNode cNode, Expression other) {
        return new BooleanExpression(new BinaryExpression(new ClassExpression(cNode), COMPARE_NOT_EQUAL,
                new MethodCallExpression(other, "getClass", MethodCallExpression.NO_ARGUMENTS)));
    }

    private Expression findArg(String fName) {
        return new PropertyExpression(new VariableExpression("args"), fName);
    }

    private void adjustPropertyForImmutability(PropertyNode pNode, List<PropertyNode> newNodes) {
        final FieldNode fNode = pNode.getField();
        fNode.setModifiers((pNode.getModifiers() & (~ACC_PUBLIC)) | ACC_FINAL | ACC_PRIVATE);
        adjustPropertyNode(pNode, createGetterBody(fNode));
        newNodes.add(pNode);
    }

    private void adjustPropertyNode(PropertyNode pNode, Statement getterBody) {
        pNode.setSetterBlock(null);
        pNode.setGetterBlock(getterBody);
    }

    private Statement createGetterBody(FieldNode fNode) {
        BlockStatement body = new BlockStatement();
        final ClassNode fieldType = fNode.getType();
        final Statement statement;
        if (fieldType.isArray() || fieldType.implementsInterface(CLONEABLE_TYPE)) {
            statement = createGetterBodyArrayOrCloneable(fNode);
        } else if (fieldType.isDerivedFrom(DATE_TYPE)) {
            statement = createGetterBodyDate(fNode);
        } else {
            statement = createGetterBodyDefault(fNode);
        }
        body.addStatement(statement);
        return body;
    }

    private Statement createGetterBodyDefault(FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        return new ExpressionStatement(fieldExpr);
    }

    private static String createErrorMessage(String className, String fieldName, String typeName, String mode) {
        return MY_TYPE_NAME + " processor doesn't know how to handle field '" + fieldName + "' of type '" +
                prettyTypeName(typeName) + "' while " + mode + " class " + className + ".\n" +
                MY_TYPE_NAME + " classes currently only support properties with known immutable types " +
                "or types where special handling achieves immutable behavior, including:\n" +
                "- Strings, primitive types, wrapper types, BigInteger and BigDecimal, enums\n" +
                "- other " + MY_TYPE_NAME + " classes and known immutables (java.awt.Color, java.net.URI)\n" +
                "- Cloneable classes, collections, maps and arrays, and other classes with special handling (java.util.Date)\n" +
                "Other restrictions apply, please see the groovydoc for " + MY_TYPE_NAME + " for further details";
    }

    private static String prettyTypeName(String name) {
        return name.equals("java.lang.Object") ? name + " or def" : name;
    }

    private Statement createGetterBodyArrayOrCloneable(FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        final Expression expression = cloneArrayOrCloneableExpr(fieldExpr);
        return safeExpression(fieldExpr, expression);
    }

    private Expression cloneArrayOrCloneableExpr(Expression fieldExpr) {
        return new MethodCallExpression(fieldExpr, "clone", MethodCallExpression.NO_ARGUMENTS);
    }

    private Expression cloneCollectionExpr(Expression fieldExpr) {
        return new StaticMethodCallExpression(DGM_TYPE, "asImmutable", fieldExpr);
    }

    private Statement createGetterBodyDate(FieldNode fNode) {
        final Expression fieldExpr = new VariableExpression(fNode);
        final Expression expression = cloneDateExpr(fieldExpr);
        return safeExpression(fieldExpr, expression);
    }

    private Statement safeExpression(Expression fieldExpr, Expression expression) {
        return new IfStatement(
                equalsNullExpr(fieldExpr),
                new ExpressionStatement(fieldExpr),
                new ExpressionStatement(expression));
    }

    public static Object checkImmutable(String className, String fieldName, Object field) {
        if (field == null || field instanceof Enum || inImmutableList(field.getClass().getName())) return field;
        if (field instanceof Collection) return DefaultGroovyMethods.asImmutable((Collection) field);
        if (field.getClass().getAnnotation(MY_CLASS) != null) return field;
        final String typeName = field.getClass().getName();
        throw new RuntimeException(createErrorMessage(className, fieldName, typeName, "constructing"));
    }

    public static void checkPropNames(Object instance, Map<String, Object> args) {
        final MetaClass metaClass = InvokerHelper.getMetaClass(instance);
        for (String k : args.keySet()) {
            if (metaClass.hasProperty(instance, k) == null)
                throw new MissingPropertyException(k, instance.getClass());
        }
    }
}