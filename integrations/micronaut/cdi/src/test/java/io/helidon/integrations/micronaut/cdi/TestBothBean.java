/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.micronaut.cdi;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@MicronautBeanDef
public class TestBothBean implements TestBean {

    @Override
    public String name() {
        return "BothBean";
    }

    @CdiIntercepted
    @Override
    public String cdiAnnotated() {
        return TestBean.super.cdiAnnotated();
    }

    @MicroIntercepted
    @Override
    public String µAnnotated() {
        return TestBean.super.µAnnotated();
    }

    @CdiIntercepted
    @MicroIntercepted
    @Override
    public String bothAnnotated() {
        return TestBean.super.bothAnnotated();
    }
}
