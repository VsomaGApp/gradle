/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentUsage;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

public class CacheLockingArtifactResolver implements ArtifactResolver {
    private final CacheLockingManager lockingManager;
    private final ArtifactResolver delegate;

    public CacheLockingArtifactResolver(CacheLockingManager lockingManager, ArtifactResolver delegate) {
        this.lockingManager = lockingManager;
        this.delegate = delegate;
    }

    public void resolveModuleArtifacts(final ComponentResolveMetaData component, final ArtifactType artifactType, final BuildableArtifactSetResolveResult result) {
        String description = "Resolve " + artifactType + " for " + component;
        lockingManager.useCache(description, new Runnable() {
            public void run() {
                delegate.resolveModuleArtifacts(component, artifactType, result);
            }
        });
    }

    public void resolveModuleArtifacts(final ComponentResolveMetaData component, final ComponentUsage usage, final BuildableArtifactSetResolveResult result) {
        String description = "Resolve " + usage + " for " + component;
        lockingManager.useCache(description, new Runnable() {
            public void run() {
                delegate.resolveModuleArtifacts(component, usage, result);
            }
        });
    }

    public void resolveArtifact(final ComponentArtifactMetaData artifact, final ModuleSource moduleSource, final BuildableArtifactResolveResult result) {
        String description = "Resolve ".concat(artifact.toString());
        lockingManager.useCache(description, new Runnable() {
            public void run() {
                delegate.resolveArtifact(artifact, moduleSource, result);
            }
        });
    }
}
