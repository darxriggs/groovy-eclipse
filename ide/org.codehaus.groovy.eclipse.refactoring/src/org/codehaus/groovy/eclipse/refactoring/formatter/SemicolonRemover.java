/*
 * Copyright 2011 the original author or authors.
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
package org.codehaus.groovy.eclipse.refactoring.formatter;

import java.util.List;

import org.codehaus.greclipse.GroovyTokenTypeBridge;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import antlr.Token;

/**
 * Removes trailing semicolons as they are optional in Groovy.
 */
public class SemicolonRemover extends GroovyFormatter {

    GroovyDocumentScanner scanner;

    public SemicolonRemover(ITextSelection sel, IDocument doc) {
        super(sel, doc);
        scanner = new GroovyDocumentScanner(doc);
    }

    @Override
    public TextEdit format() throws BadLocationException {
        TextEdit textEdit = new MultiTextEdit();

        for (int line = 0; line < document.getNumberOfLines(); line++) {
            Token lastToken = getLastTokenInLine(line);

            if (isUnnecessarySemicolon(lastToken)) {
                int semicolonOffset = scanner.getOffset(lastToken);
                TextEdit deleteSemicolon = new DeleteEdit(semicolonOffset, 1);
                textEdit.addChild(deleteSemicolon);
            }
        }

        return textEdit;
    }

    private Token getLastTokenInLine(int line) throws BadLocationException {
        List<Token> tokens = scanner.getLineTokens(line);

        if (tokens == null || tokens.isEmpty()) {
            return null;
        }

        // It's not required to handle trailing comments at the EOL
        // as they are already stripped by Antlr or the scanner.
        return tokens.get(tokens.size() - 1);
    }

    private boolean isUnnecessarySemicolon(Token token) {
        return token != null && token.getType() == GroovyTokenTypeBridge.SEMI;
    }
}
