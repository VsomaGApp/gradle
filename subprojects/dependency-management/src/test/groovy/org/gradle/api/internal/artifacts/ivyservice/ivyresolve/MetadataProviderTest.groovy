/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.NamespaceId
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState
import org.gradle.internal.component.external.model.DefaultIvyModuleResolveMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import spock.lang.Specification

class MetadataProviderTest extends Specification {
    def dep = Stub(DependencyMetaData)
    def id = Stub(ModuleComponentIdentifier) {
        getVersion() >> "1.2"
    }
    def metaData = Stub(MutableModuleComponentResolveMetadata)
    def resolveState = Mock(ModuleComponentResolveState)
    def metadataProvider = new MetadataProvider(resolveState)

    def "caches metadata result"() {
        when:
        metadataProvider.getMetaData()
        metadataProvider.getMetaData()

        then:
        1 * resolveState.resolve() >> {
            def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
            result.resolved(metaData)
            return result
        }
        0 * resolveState.resolve()
    }

    def "verifies that metadata was provided"() {
        given:
        resolveState.resolve() >> {
            def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
            result.resolved(metaData)
            return result
        }

        expect:
        metadataProvider.resolve()
        metadataProvider.usable
        metadataProvider.metaData
    }

    def "verifies that metadata was not provided"() {
        given:
        resolveState.resolve() >> {
            def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
            result.missing()
            return result
        }

        expect:
        !metadataProvider.resolve()
        !metadataProvider.usable
    }

    def "can provide component metadata" () {
        given:
        resolveState.resolve() >> {
            def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
            result.resolved(metaData)
            return result
        }

        when:
        def componentMetadata = metadataProvider.getComponentMetadata()

        then:
        componentMetadata.metadata == metaData
    }

    def "can provide Ivy descriptor" () {
        given:
        def mds = new MutableModuleDescriptorState(DefaultModuleComponentIdentifier.newId("group", "name", "version"), "test", false)
        mds.setBranch("branchValue")
        mds.getExtraInfo().put(new NamespaceId("baz", "foo"), "extraInfoValue")
        def metaData = new DefaultIvyModuleResolveMetadata(id, mds)
        resolveState.resolve() >> {
            def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
            result.resolved(metaData)
            return result
        }

        when:
        def returned = metadataProvider.getIvyModuleDescriptor()

        then:
        returned.ivyStatus == "test"
        returned.branch == "branchValue"
        returned.extraInfo.get("foo") == "extraInfoValue"
    }

    def "returns null when not Ivy descriptor" () {
        given:
        resolveState.resolve() >> {
            def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
            result.resolved(metaData)
            return result
        }

        expect:
        metadataProvider.getIvyModuleDescriptor() == null
    }
}
