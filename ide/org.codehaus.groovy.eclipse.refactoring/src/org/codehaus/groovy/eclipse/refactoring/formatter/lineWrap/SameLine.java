/*
 * Copyright (C) 2007, 2009 Martin Kempf, Reto Kleeb, Michael Klenk
 *
 * IFS Institute for Software, HSR Rapperswil, Switzerland
 * http://ifs.hsr.ch/
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
package org.codehaus.groovy.eclipse.refactoring.formatter.lineWrap;

import org.codehaus.greclipse.GroovyTokenTypeBridge;
import org.codehaus.groovy.eclipse.refactoring.formatter.DefaultGroovyFormatter;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.text.edits.ReplaceEdit;

import antlr.Token;

/**
 * @author Mike Klenk mklenk@hsr.ch
 */
public class SameLine extends CorrectLineWrap {

    public SameLine(DefaultGroovyFormatter formatter) {
        super(formatter);
    }

    @Override
    public ReplaceEdit correctLineWrap(int pos, Token token) throws BadLocationException {
        ReplaceEdit correctEdit = null;
        if (formatter.getPreviousTokenIncludingNLS(pos).getType() == GroovyTokenTypeBridge.NLS) {
            Token lastNotNLSToken = formatter.getPreviousToken(pos);
            int replaceStart = formatter.getOffsetOfTokenEnd(lastNotNLSToken);
            int replaceEnd = formatter.getOffsetOfToken(token);
            correctEdit = new ReplaceEdit(replaceStart, replaceEnd - replaceStart, " ");
        }
        return correctEdit;
    }
}
