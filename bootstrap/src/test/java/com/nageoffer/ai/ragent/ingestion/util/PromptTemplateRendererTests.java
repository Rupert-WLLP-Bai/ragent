/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.ingestion.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PromptTemplateRendererTests {

    @Test
    void shouldReturnTemplateWhenBlankOrNull() {
        assertNull(PromptTemplateRenderer.render(null, Map.of("name", "Claude")));
        assertEquals("   ", PromptTemplateRenderer.render("   ", Map.of("name", "Claude")));
    }

    @Test
    void shouldReplaceVariablesAndConvertNullsToEmptyStrings() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "Claude");
        variables.put("place", "Ragent");
        variables.put("missing", null);

        String rendered = PromptTemplateRenderer.render(
                "Hello {{name}}, welcome to {{place}}.{{missing}}",
                variables
        );

        assertEquals("Hello Claude, welcome to Ragent.", rendered);
    }

    @Test
    void shouldLeaveUnknownPlaceholdersUntouched() {
        String rendered = PromptTemplateRenderer.render("{{known}} {{unknown}}", Map.of("known", "value"));

        assertEquals("value {{unknown}}", rendered);
    }
}
