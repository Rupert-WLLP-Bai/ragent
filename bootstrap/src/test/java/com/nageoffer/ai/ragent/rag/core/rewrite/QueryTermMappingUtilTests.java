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

package com.nageoffer.ai.ragent.rag.core.rewrite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryTermMappingUtilTests {

    @Test
    void shouldReturnOriginalWhenTextOrSourceTermIsBlank() {
        assertNull(QueryTermMappingUtil.applyMapping(null, "源词", "目标词"));
        assertEquals("", QueryTermMappingUtil.applyMapping("", "源词", "目标词"));
        assertEquals("原文", QueryTermMappingUtil.applyMapping("原文", null, "目标词"));
        assertEquals("原文", QueryTermMappingUtil.applyMapping("原文", "", "目标词"));
    }

    @Test
    void shouldReplaceAllMatchedSourceTerms() {
        String result = QueryTermMappingUtil.applyMapping("平安保险和中国平安保险", "平安保险", "平安保司");

        assertEquals("平安保司和中国平安保司", result);
    }

    @Test
    void shouldNotDuplicateWhenTargetPrefixAlreadyExistsAtHitPosition() {
        String result = QueryTermMappingUtil.applyMapping("平安保司已接入", "平安保", "平安保司");

        assertEquals("平安保司已接入", result);
    }
}
