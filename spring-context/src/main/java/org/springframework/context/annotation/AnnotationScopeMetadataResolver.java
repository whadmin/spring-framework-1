/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.lang.annotation.Annotation;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.Assert;

/**
 * A {@link ScopeMetadataResolver}接口默认情况下检查Bean类上是否存在Spring的{@link Scope @Scope}注解。
 * 并解析{@link Scope @Scope}注解配置作用域元数据信息ScopeMetadata
 *
 * @see org.springframework.context.annotation.Scope
 */
public class AnnotationScopeMetadataResolver implements ScopeMetadataResolver {

	/**
	 * 当配置作用域代理选项为ScopedProxyMode.DEFAULT时，解析获取默认作用域代理选项
	 */
	private final ScopedProxyMode defaultProxyMode;

	/**
	 * scope注解类型
	 */
	protected Class<? extends Annotation> scopeAnnotationType = Scope.class;


	/**
	 * 构造一个新的{@code AnnotationScopeMetadataResolver}，默认用域代理选项为ScopedProxyMode.NO
	 */
	public AnnotationScopeMetadataResolver() {
		this.defaultProxyMode = ScopedProxyMode.NO;
	}

	/**
	 * 构造一个新的{@code AnnotationScopeMetadataResolver}，并设置默认用域代理选项
	 */
	public AnnotationScopeMetadataResolver(ScopedProxyMode defaultProxyMode) {
		Assert.notNull(defaultProxyMode, "'defaultProxyMode' must not be null");
		this.defaultProxyMode = defaultProxyMode;
	}

	/**
	 * 设置scope注释的类型
	 */
	public void setScopeAnnotationType(Class<? extends Annotation> scopeAnnotationType) {
		Assert.notNull(scopeAnnotationType, "'scopeAnnotationType' must not be null");
		this.scopeAnnotationType = scopeAnnotationType;
	}


	/**
	 * 解析BeanDefinition获取作用域元数据信息ScopeMetadata
	 */
	@Override
	public ScopeMetadata resolveScopeMetadata(BeanDefinition definition) {
		/** 实例化ScopeMetadata **/
		ScopeMetadata metadata = new ScopeMetadata();
		/** 判断当前BeanDefinition 类型是否为AnnotatedBeanDefinition **/
		if (definition instanceof AnnotatedBeanDefinition) {
			/** 类型转换 **/
			AnnotatedBeanDefinition annDef = (AnnotatedBeanDefinition) definition;

			/** 获取scopeAnnotationType注解的属性对象 **/
			AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(
					annDef.getMetadata(), this.scopeAnnotationType);

            /** 获取scopeAnnotationType注解作用域，作用域代理选项设置到ScopeMetadata **/
			if (attributes != null) {
				metadata.setScopeName(attributes.getString("value"));
				ScopedProxyMode proxyMode = attributes.getEnum("proxyMode");
				if (proxyMode == ScopedProxyMode.DEFAULT) {
					proxyMode = this.defaultProxyMode;
				}
				metadata.setScopedProxyMode(proxyMode);
			}
		}
		/** 返回ScopeMetadata **/
		return metadata;
	}

}
