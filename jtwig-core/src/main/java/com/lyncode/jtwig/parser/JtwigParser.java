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

import com.lyncode.jtwig.exception.ParseBypassException;
import com.lyncode.jtwig.exception.ParseException;
import com.lyncode.jtwig.exception.ResourceException;
import com.lyncode.jtwig.resource.JtwigResource;
import com.lyncode.jtwig.tree.api.Content;
import com.lyncode.jtwig.tree.content.*;
import com.lyncode.jtwig.tree.documents.JtwigDocument;
import com.lyncode.jtwig.tree.documents.JtwigExtendsDocument;
import com.lyncode.jtwig.tree.documents.JtwigRootDocument;
import com.lyncode.jtwig.tree.expressions.Variable;
import com.lyncode.jtwig.tree.structural.Block;
import com.lyncode.jtwig.tree.structural.Extends;
import com.lyncode.jtwig.tree.structural.Include;
import com.lyncode.jtwig.tree.tags.Verbatim;
import org.parboiled.BaseParser;
import org.parboiled.Rule;
import org.parboiled.common.FileUtils;
import org.parboiled.errors.ParserRuntimeException;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

import java.nio.charset.Charset;

import static com.lyncode.jtwig.parser.JtwigKeyword.*;
import static com.lyncode.jtwig.parser.JtwigSymbol.*;
import static com.lyncode.jtwig.tree.content.IfExpression.ElseExpression;
import static com.lyncode.jtwig.tree.content.IfExpression.ElseIfExpression;
import static org.parboiled.Parboiled.createParser;

public class JtwigParser extends BaseParser<Content> {
    public static JtwigDocument parse(JtwigResource input) throws ParseException {
        try {
            ReportingParseRunner<Object> runner = new ReportingParseRunner<Object>(createParser(JtwigParser.class).start());
            ParsingResult<Object> result = runner.run(FileUtils.readAllText(input.retrieve(), Charset.defaultCharset()));
            return (JtwigDocument) result.resultValue;
        } catch (ParserRuntimeException e) {
            if (e.getCause() instanceof ParseBypassException) {
                ParseException innerException = ((ParseBypassException) e.getCause()).getInnerException();
                innerException.setExpression(e.getMessage());
                throw innerException;
            } else
                throw new ParseException(e);
        } catch (ResourceException e) {
            throw new ParseException(e);
        }
    }

    JtwigBasicParser basicParser = createParser(JtwigBasicParser.class);
    JtwigExpressionParser expressionParser = createParser(JtwigExpressionParser.class);
    JtwigTagPropertyParser tagPropertyParser = createParser(JtwigTagPropertyParser.class);


    public Rule start() {
        return FirstOf(
                extendTemplate(),
                normalTemplate()
        );
    }

    Rule extendTemplate() {
        return Sequence(
                basicParser.spacing(),
                Sequence(
                        basicParser.openCode(),
                        basicParser.spacing(),
                        keyword(EXTENDS),
                        mandatory(
                                Sequence(
                                        basicParser.stringLiteral(),
                                        basicParser.spacing(),
                                        basicParser.closeCode(),
                                        push(new JtwigExtendsDocument(new Extends(basicParser.pop()))),
                                        ZeroOrMore(
                                                basicParser.spacing(),
                                                block(),
                                                peek(1, JtwigExtendsDocument.class).add(pop(Block.class))
                                        ),
                                        basicParser.spacing(),
                                        EOI
                                ),
                                new ParseException("Wrong include syntax")
                        )
                )
        );
    }


    Rule normalTemplate() {
        return Sequence(
                content(),
                push(new JtwigRootDocument(pop())),
                EOI
        );
    }

    Rule content() {
        return Sequence(
                push(new JtwigContent()),
                ZeroOrMore(
                        FirstOf(
                                addToContent(output()),
                                addToContent(block()),
                                addToContent(include()),
                                addToContent(forEach()),
                                addToContent(ifCondition()),
                                addToContent(set()),
                                addToContent(verbatim()),
                                Sequence(
                                        openCode(),
                                        TestNot(
                                                FirstOf(
                                                        keyword(ENDBLOCK),
                                                        keyword(ENDFOR),
                                                        keyword(ENDIF),
                                                        keyword(IF),
                                                        keyword(BLOCK),
                                                        keyword(FOR),
                                                        keyword(SET),
                                                        keyword(ELSE),
                                                        keyword(ELSEIF),
                                                        keyword(VERBATIM)
                                                )
                                        ),
                                        throwException(new ParseException("Unknown tag"))
                                ),
                                addToContent(text())
                        )
                )
        );
    }

    Rule addToContent(Rule innerRule) {
        return Sequence(
                innerRule,
                doIt(peek(1, JtwigContent.class).add(pop()))
        );
    }

    Rule block() {
        return Sequence(
                openCode(),
                keyword(BLOCK),
                mandatory(
                        Sequence(
                                expressionParser.variable(),
                                push(new Block((expressionParser.pop(Variable.class)).getIdentifier())),
                                doIt(peek(Block.class).begin().addToLeft(tagPropertyParser.getCurrentProperty())),
                                closeCode(),
                                doIt(peek(Block.class).begin().addToRight(tagPropertyParser.getCurrentProperty())),
                                content(),
                                peek(1, Block.class).setContent(pop(JtwigContent.class)),
                                openCode(),
                                doIt(peek(Block.class).end().addToLeft(tagPropertyParser.getCurrentProperty())),
                                keyword(ENDBLOCK),
                                Optional(
                                        expressionParser.variable(),
                                        assertEqual(
                                                peek(Block.class).getName(),
                                                expressionParser.pop(Variable.class).getIdentifier()
                                        )
                                ),
                                closeCode(),
                                doIt(peek(Block.class).end().addToRight(tagPropertyParser.getCurrentProperty()))
                        ),
                        new ParseException("Wrong block syntax")
                )
        );
    }

    boolean assertEqual(String value1, String value2) {
        if (!value1.equals(value2))
            return throwException(new ParseException("Start statement and ending block names do not match"));
        else
            return true;
    }

    Rule include() {
        return Sequence(
                openCode(),
                keyword(INCLUDE),
                mandatory(
                        Sequence(
                                basicParser.stringLiteral(),
                                basicParser.spacing(),
                                push(new Include(basicParser.pop())),
                                doIt(peek(Include.class).begin().addToLeft(tagPropertyParser.getCurrentProperty())),
                                closeCode(),
                                doIt(peek(Include.class).end().addToRight(tagPropertyParser.getCurrentProperty()))
                        ),
                        new ParseException("Wrong include syntax")
                )
        );
    }

    Rule text() {
        return Sequence(
                push(new Text()),
                OneOrMore(
                        FirstOf(
                                Sequence("{#", ZeroOrMore(TestNot("#}"), ANY), "#}"),
                                Sequence(
                                        basicParser.escape(),
                                        peek(Text.class).append(match())
                                ),
                                Sequence(
                                        TestNot(
                                                FirstOf(
                                                        basicParser.symbol(OPEN_OUTPUT),
                                                        basicParser.symbol(OPEN_CODE)
                                                )
                                        ),
                                        ANY,
                                        peek(Text.class).append(match())
                                )
                        )
                ).suppressSubnodes()
        );
    }

    Rule verbatim() {
        return Sequence(
                openCode(),
                keyword(VERBATIM),
                mandatory(
                        Sequence(
                                push(new Verbatim()),
                                doIt(peek(Verbatim.class).begin().addToLeft(tagPropertyParser.getCurrentProperty())),
                                closeCode(),
                                text(Sequence(
                                        basicParser.openCode(),
                                        basicParser.spacing()
                                        , keyword(ENDVERBATIM)
                                )),
                                peek(1, Verbatim.class).setText(pop(Text.class)),
                                openCode(),
                                keyword(JtwigKeyword.ENDVERBATIM),
                                closeCode(),
                                doIt(peek(Verbatim.class).end().addToRight(tagPropertyParser.getCurrentProperty()))
                        ),
                        new ParseException("Wrong verbatim syntax")
                )
        );
    }

    Rule text(Rule until) {
        return Sequence(
                push(new Text()),
                OneOrMore(
                        FirstOf(
                                Sequence(
                                        basicParser.escape(),
                                        peek(Text.class).append(match())
                                ),
                                Sequence(
                                        TestNot(
                                                until
                                        ),
                                        ANY,
                                        peek(Text.class).append(match())
                                )
                        )
                ).suppressSubnodes()
        );
    }

    Rule ifCondition() {
        return Sequence(
                openCode(),
                keyword(IF),
                mandatory(
                        Sequence(
                                expressionParser.expression(),
                                push(new IfExpression(expressionParser.pop())),
                                doIt(peek(IfExpression.class).begin().addToLeft(tagPropertyParser.getCurrentProperty())),
                                closeCode(),
                                doIt(peek(IfExpression.class).begin().addToRight(tagPropertyParser.getCurrentProperty())),
                                content(),
                                peek(1, IfExpression.class).setContent(pop(JtwigContent.class)),
                                ZeroOrMore(
                                        Sequence(
                                                openCode(),
                                                keyword(ELSEIF),
                                                expressionParser.expression(),
                                                push(new ElseIfExpression(expressionParser.pop())),
                                                doIt(peek(ElseIfExpression.class).tag().addToLeft(tagPropertyParser.getCurrentProperty())),
                                                closeCode(),
                                                doIt(peek(ElseIfExpression.class).tag().addToRight(tagPropertyParser.getCurrentProperty())),
                                                content(),
                                                peek(1, ElseIfExpression.class).setContent(pop(JtwigContent.class)),
                                                peek(1, IfExpression.class).addElseIf(pop(ElseIfExpression.class))
                                        )
                                ),
                                Optional(
                                        Sequence(
                                                openCode(),
                                                keyword(ELSE),
                                                push(new ElseExpression()),
                                                doIt(peek(ElseExpression.class).tag().addToLeft(tagPropertyParser.getCurrentProperty())),
                                                closeCode(),
                                                doIt(peek(ElseExpression.class).tag().addToRight(tagPropertyParser.getCurrentProperty())),
                                                content(),
                                                peek(1, ElseExpression.class).setContent(pop(JtwigContent.class)),
                                                peek(1, IfExpression.class).setElseExpression(pop(ElseExpression.class))
                                        )
                                ),
                                openCode(),
                                doIt(peek(IfExpression.class).end().addToLeft(tagPropertyParser.getCurrentProperty())),
                                keyword(ENDIF),
                                closeCode(),
                                doIt(peek(IfExpression.class).end().addToRight(tagPropertyParser.getCurrentProperty()))
                        ),
                        new ParseException("Wrong if syntax")
                )
        );
    }

    Rule forEach() {
        return Sequence(
                openCode(),
                keyword(FOR),
                mandatory(
                        Sequence(
                                expressionParser.variable(),
                                FirstOf(
                                        Sequence(
                                                symbol(COMMA),
                                                expressionParser.variable(),
                                                keyword(IN),
                                                expressionParser.expression(),
                                                push(new ForPairLoop(expressionParser.pop(2, Variable.class),
                                                        expressionParser.pop(1, Variable.class),
                                                        expressionParser.pop()))
                                        ),
                                        Sequence(
                                                keyword(IN),
                                                expressionParser.expression(),
                                                push(new ForLoop(expressionParser.pop(1, Variable.class),
                                                        expressionParser.pop()))
                                        )
                                ),
                                doIt(peek(ForLoop.class).begin().addToLeft(tagPropertyParser.getCurrentProperty())),
                                closeCode(),
                                doIt(peek(ForLoop.class).begin().addToRight(tagPropertyParser.getCurrentProperty())),
                                content(),
                                peek(1, ForLoop.class).setContent(pop(JtwigContent.class)),
                                openCode(),
                                doIt(peek(ForLoop.class).end().addToLeft(tagPropertyParser.getCurrentProperty())),
                                keyword(ENDFOR),
                                closeCode(),
                                doIt(peek(ForLoop.class).end().addToRight(tagPropertyParser.getCurrentProperty()))
                        ),
                        new ParseException("Wrong for each syntax")
                )
        );
    }

    Rule set() {
        return Sequence(
                openCode(),
                keyword(SET),
                mandatory(
                        Sequence(
                                expressionParser.variable(),
                                push(new SetVariable(expressionParser.pop(Variable.class))),
                                doIt(peek(SetVariable.class).begin().addToLeft(tagPropertyParser.getCurrentProperty())),
                                symbol(ATTR),
                                expressionParser.expression(),
                                peek(1, SetVariable.class).setAssignment(expressionParser.pop()),
                                closeCode(),
                                doIt(peek(SetVariable.class).end().addToRight(tagPropertyParser.getCurrentProperty()))
                        ),
                        new ParseException("Wrong set syntax")
                )
        );
    }

    Rule output() {
        return Sequence(
                basicParser.symbol(OPEN_OUTPUT),
                tagPropertyParser.property(),
                basicParser.spacing(),
                mandatory(
                        Sequence(
                                expressionParser.expression(),
                                push(new Output(expressionParser.pop())),
                                doIt(peek(Output.class).begin().addToLeft(tagPropertyParser.getCurrentProperty())),
                                tagPropertyParser.property(),
                                doIt(peek(Output.class).end().addToRight(tagPropertyParser.getCurrentProperty())),
                                basicParser.symbol(JtwigSymbol.CLOSE_OUTPUT)
                        ),
                        new ParseException("Wrong output syntax")
                )
        );
    }

    Rule symbol(JtwigSymbol symbol) {
        return Sequence(
                basicParser.symbol(symbol),
                basicParser.spacing()
        );
    }


    Rule mandatory(Rule rule, ParseException exception) {
        return FirstOf(
                rule,
                throwException(exception)
        );
    }


    Rule openCode() {
        return Sequence(
                basicParser.openCode(),
                tagPropertyParser.property(),
                basicParser.spacing()
        );
    }

    Rule closeCode() {
        return Sequence(
                tagPropertyParser.property(),
                basicParser.closeCode()
        );
    }

    Rule keyword(JtwigKeyword keyword) {
        return Sequence(
                basicParser.keyword(keyword),
                basicParser.spacing()
        );
    }

    boolean throwException(ParseException exception) throws ParseBypassException {
        throw new ParseBypassException(exception);
    }

    <T> T peek(int position, Class<T> typeClass) {
        return typeClass.cast(peek(position));
    }

    <T> T peek(Class<T> typeClass) {
        return peek(0, typeClass);
    }

    <T> T pop(int position, Class<T> typeClass) {
        return typeClass.cast(pop(position));
    }

    <T> T pop(Class<T> typeClass) {
        return pop(0, typeClass);
    }

    boolean doIt(Object object) {
        return true;
    }
}
