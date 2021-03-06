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

package com.lyncode.jtwig.test;

import com.lyncode.jtwig.JtwigContext;
import com.lyncode.jtwig.JtwigTemplate;
import com.lyncode.jtwig.builder.JtwigResourceBuilder;

import java.io.ByteArrayOutputStream;

public class AbstractJtwigTest {
    private JtwigContext context = new JtwigContext();

    protected String theResultOfRendering(JtwigTemplate template, JtwigContext context) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        template.output(outputStream, context);
        return outputStream.toString();
    }

    protected String theResultOfRendering(JtwigTemplate template) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        template.output(outputStream, context);
        return outputStream.toString();
    }

    protected JtwigTemplate theTemplate(String content) {
        return new JtwigTemplate(content);
    }

    protected JtwigResourceBuilder resource () {
        return new JtwigResourceBuilder();
    }

    protected JtwigContext theContext () {
        return context;
    }
}
