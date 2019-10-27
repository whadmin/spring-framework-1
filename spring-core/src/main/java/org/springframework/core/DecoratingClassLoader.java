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

package org.springframework.core;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * 作为Spring自定义类加载器的基类，Spring自定义类加载器包括
 * {@link overridingclassloader}
 * {@link shadowingclassloader}，
 *
 * 提供排除包和类的通用处理。
 * @since 2.5.2
 */
public abstract class DecoratingClassLoader extends ClassLoader {

	static {
		ClassLoader.registerAsParallelCapable();
	}


	private final Set<String> excludedPackages = Collections.newSetFromMap(new ConcurrentHashMap<>(8));

	private final Set<String> excludedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(8));


	/**
	 * 创建一个新的DecoratingClassLoader
	 */
	public DecoratingClassLoader() {
	}

	/**
	 * 创建一个新的DecoratingClassLoader,同时指定父ClassLoader
	 */
	public DecoratingClassLoader(@Nullable ClassLoader parent) {
		super(parent);
	}


	/**
	 * 添加自定义类加载排除加载的类包路径
	 * <p>此处注册的任何类名称都将由父ClassLoader处理
	 */
	public void excludePackage(String packageName) {
		Assert.notNull(packageName, "Package name must not be null");
		this.excludedPackages.add(packageName);
	}

	/**
	 * 添加自定义类加载排除加载的类名称
	 * <p>此处注册的任何类名称都将由父ClassLoader处理
	 */
	public void excludeClass(String className) {
		Assert.notNull(className, "Class name must not be null");
		this.excludedClasses.add(className);
	}

	/**
	 * 确定指定类被在自定义类加载器排除加载的范围内
	 */
	protected boolean isExcluded(String className) {
		if (this.excludedClasses.contains(className)) {
			return true;
		}
		for (String packageName : this.excludedPackages) {
			if (className.startsWith(packageName)) {
				return true;
			}
		}
		return false;
	}

}
