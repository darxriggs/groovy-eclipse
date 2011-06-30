 /*
 * Copyright 2003-2009 the original author or authors.
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

package org.codehaus.groovy.eclipse.codeassist.processors;

import org.eclipse.jdt.internal.codeassist.CompletionEngine;
import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal;
import org.eclipse.jdt.internal.core.NameLookup;

public class GroovyCompletionProposal extends InternalCompletionProposal {

    public GroovyCompletionProposal(int kind, int completionLocation) {
        super(kind, completionLocation);
    }

    public void setNameLookup(NameLookup lookup) {
        super.nameLookup = lookup;
    }
    @Override
    public void setDeclarationTypeName(char[] declarationTypeName) {
        super.setDeclarationTypeName(declarationTypeName);
    }
    @Override
    public void setTypeName(char[] typeName) {
        super.setTypeName(typeName);
    }
    @Override
    public void setParameterTypeNames(char[][] parameterTypeNames) {
        super.setParameterTypeNames(parameterTypeNames);
    }
    @Override
    public void setAccessibility(int kind) {
        super.setAccessibility(kind);
    }

    @Override
    protected void setDeclarationPackageName(char[] declarationPackageName) {
        super.setDeclarationPackageName(declarationPackageName);
    }

    @Override
    protected void setParameterPackageNames(char[][] parameterPackageNames) {
        super.setParameterPackageNames(parameterPackageNames);
    }

    @Override
    public void setPackageName(char[] packageName) {
        super.setPackageName(packageName);
    }

    @Override
    protected void setIsContructor(boolean isConstructor) {
        super.setIsContructor(isConstructor);
    }

    protected void setCompletionEngine(CompletionEngine completionEngine) {
        this.completionEngine = completionEngine;
    }
}