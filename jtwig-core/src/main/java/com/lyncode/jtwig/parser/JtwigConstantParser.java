/**
 * Copyright 2012 Lyncode
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

package com.lyncode.jtwig.parser;

import com.lyncode.jtwig.tree.expressions.Constant;
import org.parboiled.BaseParser;
import org.parboiled.Rule;

import static com.lyncode.jtwig.parser.JtwigKeyword.*;
import static com.lyncode.jtwig.parser.JtwigSymbol.DOT;
import static com.lyncode.jtwig.parser.JtwigSymbol.MINUS;
import static org.parboiled.Parboiled.createParser;

public class JtwigConstantParser extends BaseParser<Constant> {
    JtwigBasicParser basic = createParser(JtwigBasicParser.class);

    public Rule anyConstant () {
        return FirstOf(
                nullValue(),
                booleanValue(),
                doubleValue(),
                integerValue(),
                charValue(),
                string()
        );
    }

    public Rule booleanValue() {
        return FirstOf(
                Sequence(
                        basic.keyword(TRUE),
                        push(new Constant<>(true))
                ),
                Sequence(
                        basic.keyword(FALSE),
                        push(new Constant<>(false))
                )
        );
    }

    public Rule nullValue() {
        return Sequence(
                basic.keyword(NULL),
                push(new Constant<>(null))
        );
    }

    public Rule charValue () {
        return Sequence(
                basic.onlyOneChar(),
                push(new Constant<>(basic.pop().charAt(0)))
        );
    }

    public Rule integerValue() {
        return Sequence(
                Sequence(
                        Optional(basic.symbol(MINUS)),
                        OneOrMore(basic.digit())
                ),
                push(new Constant<>(Integer.parseInt(match())))
        );
    }

    public Rule doubleValue() {
        return Sequence(
                Sequence(
                        Optional(basic.symbol(MINUS)),
                        OneOrMore(basic.digit()),
                        basic.symbol(DOT),
                        OneOrMore(basic.digit())
                ),
                push(new Constant<>(Double.valueOf(match())))
        );
    }

    public Rule string() {
        return Sequence(
                basic.stringLiteral(),
                push(new Constant<>(basic.pop()))
        );
    }
}
