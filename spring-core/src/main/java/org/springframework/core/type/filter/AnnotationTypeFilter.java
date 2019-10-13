/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.core.type.filter;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * 一个简单的{@link TypeFilter}，它将类与给定的注释进行匹配，并检查继承的注释。
 */
public class AnnotationTypeFilter extends AbstractTypeHierarchyTraversingFilter {

	/**
	 * 注解类型
	 */
	private final Class<? extends Annotation> annotationType;

	/**
	 * 启动元注解匹配
	 */
	private final boolean considerMetaAnnotations;


	/**
	 * 实例化注解类型过滤器，指定注解类型
	 * 默认情况下匹配元注释，不考虑接口匹配
	 */
	public AnnotationTypeFilter(Class<? extends Annotation> annotationType) {
		this(annotationType, true, false);
	}

	/**
	 * 实例化注解类型过滤器，指定注解类型，是否匹配元注释
	 * 默认情况下不考虑接口匹配
	 */
	public AnnotationTypeFilter(Class<? extends Annotation> annotationType, boolean considerMetaAnnotations) {
		this(annotationType, considerMetaAnnotations, false);
	}

	/**
	 * 实例化注解类型过滤器，指定注解类型，是否匹配元注释，是否考虑接口匹配
	 * 当前注解类型类型为Inherited.class靠谱匹配父类
	 */
	public AnnotationTypeFilter(
			Class<? extends Annotation> annotationType, boolean considerMetaAnnotations, boolean considerInterfaces) {

		super(annotationType.isAnnotationPresent(Inherited.class), considerInterfaces);
		this.annotationType = annotationType;
		this.considerMetaAnnotations = considerMetaAnnotations;
	}

	/**
	 * 返回注解类型
	 */
	public final Class<? extends Annotation> getAnnotationType() {
		return this.annotationType;
	}

	/**
	 * 注解类型是否标注到指定类。
	 * 如果开启元注解匹配，如果启动元注解匹配，则从声明元注解中匹配
	 */
	@Override
	protected boolean matchSelf(MetadataReader metadataReader) {
		AnnotationMetadata metadata = metadataReader.getAnnotationMetadata();
		return metadata.hasAnnotation(this.annotationType.getName()) ||
				(this.considerMetaAnnotations && metadata.hasMetaAnnotation(this.annotationType.getName()));
	}

	/**
	 * 父类匹配注解类型
	 */
	@Override
	@Nullable
	protected Boolean matchSuperClass(String superClassName) {
		return hasAnnotation(superClassName);
	}

	/**
	 * 接口匹配注解类型
	 */
	@Override
	@Nullable
	protected Boolean matchInterface(String interfaceName) {
		return hasAnnotation(interfaceName);
	}

	/**
	 * 实现父类，接口是否匹配注解类型
	 */
	@Nullable
	protected Boolean hasAnnotation(String typeName) {
		/** 如果类名称/接口名称是Object，直接返回不匹配 **/
		if (Object.class.getName().equals(typeName)) {
			return false;
		}
		/** 如果类名称/接口名称已java开头**/
		else if (typeName.startsWith("java")) {
			/** 如果注解的全路径不已java开头，直接返回不匹配  **/
			if (!this.annotationType.getName().startsWith("java")) {
				return false;
			}
			try {
				Class<?> clazz = ClassUtils.forName(typeName, getClass().getClassLoader());
				/** 判断注解类型是否修饰父类/接口，如果启动元注解匹配，则从父类/接口声明元注解中匹配 **/
				return ((this.considerMetaAnnotations ? AnnotationUtils.getAnnotation(clazz, this.annotationType) :
						clazz.getAnnotation(this.annotationType)) != null);
			}
			catch (Throwable ex) {
				// Class not regularly loadable - can't determine a match that way.
			}
		}
		/** 如果类名称/接口名称不已java开头，返回null**/
		return null;
	}

}
