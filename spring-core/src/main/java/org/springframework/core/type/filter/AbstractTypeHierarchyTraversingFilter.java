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

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;

/**
 * 类型过滤器，它知道遍历层次结构。
 *
 * 抽象类型层次遍历过滤器
 */
public abstract class AbstractTypeHierarchyTraversingFilter implements TypeFilter {

	/**
	 * 日志
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 是否考虑父类匹配
	 */
	private final boolean considerInherited;

	/**
	 * 是否考虑接口匹配
	 */
	private final boolean considerInterfaces;


	/**
	 * 实例化，指定是否考虑父类匹配，是否考虑接口匹配
	 */
	protected AbstractTypeHierarchyTraversingFilter(boolean considerInherited, boolean considerInterfaces) {
		this.considerInherited = considerInherited;
		this.considerInterfaces = considerInterfaces;
	}


	/**
	 * 匹配
	 */
	@Override
	public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
			throws IOException {

		/** 判断自身特征是否匹配 **/
		if (matchSelf(metadataReader)) {
			return true;
		}

		/** 判断ClassName是否匹配 **/
		ClassMetadata metadata = metadataReader.getClassMetadata();
		if (matchClassName(metadata.getClassName())) {
			return true;
		}

		/** 是否考虑父类匹配 **/
		if (this.considerInherited) {
			/** 获取父类名称 **/
			String superClassName = metadata.getSuperClassName();
			/** 如果存在父类 **/
			if (superClassName != null) {
				/** 判断父类是否匹配,matchSuperClass 只针对全路径"java开头"进行匹配，没找到返回null **/
				Boolean superClassMatch = matchSuperClass(superClassName);
				if (superClassMatch != null) {
					if (superClassMatch.booleanValue()) {
						return true;
					}
				}
				/** 递归匹配父类 **/
				else {
					try {
						if (match(metadata.getSuperClassName(), metadataReaderFactory)) {
							return true;
						}
					}
					catch (IOException ex) {
						if (logger.isDebugEnabled()) {
							logger.debug("Could not read super class [" + metadata.getSuperClassName() +
									"] of type-filtered class [" + metadata.getClassName() + "]");
						}
					}
				}
			}
		}

		/** 是否考虑接口匹配 **/
		if (this.considerInterfaces) {
			/** 遍历所有接口 **/
			for (String ifc : metadata.getInterfaceNames()) {
				/** 判断接口是否匹配,matchInterface 只针对全路径"java开头"进行匹配，没找到返回null **/
				Boolean interfaceMatch = matchInterface(ifc);
				if (interfaceMatch != null) {
					if (interfaceMatch.booleanValue()) {
						return true;
					}
				}
				/** 递归匹配接口 **/
				else {
					try {
						if (match(ifc, metadataReaderFactory)) {
							return true;
						}
					}
					catch (IOException ex) {
						if (logger.isDebugEnabled()) {
							logger.debug("Could not read interface [" + ifc + "] for type-filtered class [" +
									metadata.getClassName() + "]");
						}
					}
				}
			}
		}

		return false;
	}

	/**
	 * TypeFilter和指定ClassName是否匹配
	 */
	private boolean match(String className, MetadataReaderFactory metadataReaderFactory) throws IOException {
		return match(metadataReaderFactory.getMetadataReader(className), metadataReaderFactory);
	}

	/**
	 * 匹配自身特征，模板方法，子类扩展实现
	 */
	protected boolean matchSelf(MetadataReader metadataReader) {
		return false;
	}

	/**
	 * 判断类名称是否匹配，模板方法，子类扩展实现
	 */
	protected boolean matchClassName(String className) {
		return false;
	}

	/**
	 * 判断父类是否匹配，模板方法，子类扩展实现
	 */
	@Nullable
	protected Boolean matchSuperClass(String superClassName) {
		return null;
	}

	/**
	 * 判断接口是否匹配，模板方法，子类扩展实现
	 */
	@Nullable
	protected Boolean matchInterface(String interfaceName) {
		return null;
	}

}
