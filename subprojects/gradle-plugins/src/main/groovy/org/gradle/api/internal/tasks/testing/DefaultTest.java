/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing;

import java.io.Serializable;

public class DefaultTest implements TestInternal, Serializable {
    private final String name;
    private final Object id;
    private final String className;

    public DefaultTest(Object id, String className, String name) {
        this.id = id;
        this.className = className;
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("test %s(%s)", name, className);
    }

    public boolean isComposite() {
        return false;
    }

    public Object getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }
}
