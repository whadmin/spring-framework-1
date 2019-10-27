/*
 * Copyright 2002-2018 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;

import org.springframework.lang.Nullable;
import org.springframework.util.FileCopyUtils;

/**
 * OverridingClassLoader 是 Spring 自定义的类加载器，
 * 默认会先自己加载(excludedPackages 或 excludedClasses 例外)，只有加载不到才会委托给双亲加载，这就破坏了 JDK 的双亲委派模式。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 2.0.1
 */
public class OverridingClassLoader extends DecoratingClassLoader {

	/** 默认情况下排除的软件包。 */
	public static final String[] DEFAULT_EXCLUDED_PACKAGES = new String[]
			{"java.", "javax.", "sun.", "oracle.", "javassist.", "org.aspectj.", "net.sf.cglib."};

	private static final String CLASS_FILE_SUFFIX = ".class";

	static {
		ClassLoader.registerAsParallelCapable();
	}


	@Nullable
	private final ClassLoader overrideDelegate;


	/**
	 * 创建一个新的OverridingClassLoader，指定父ClassLoader
	 */
	public OverridingClassLoader(@Nullable ClassLoader parent) {
		this(parent, null);
	}

	/**
	 * 创建一个新的OverridingClassLoader，指定父ClassLoader和overrideDelegate
	 */
	public OverridingClassLoader(@Nullable ClassLoader parent, @Nullable ClassLoader overrideDelegate) {
		super(parent);
		this.overrideDelegate = overrideDelegate;
		for (String packageName : DEFAULT_EXCLUDED_PACKAGES) {
			excludePackage(packageName);
		}
	}


	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		/** 确定指定类不在自定义类加载器排除加载的范围内，且设置overrideDelegate **/
		if (this.overrideDelegate != null && isEligibleForOverriding(name)) {
			/** 使用overrideDelegate加载 **/
			return this.overrideDelegate.loadClass(name);
		}
		/** 使用默认加载规则 **/
		return super.loadClass(name);
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		/** 确定指定类不在自定义类加载器排除加载的范围内 **/
		if (isEligibleForOverriding(name)) {
			/**  OverridingClassLoader 重写覆盖loadClass，默认会先自己加载 破坏双亲委派原则优先子加载器加载 **/
			Class<?> result = loadClassForOverriding(name);
			if (result != null) {
				if (resolve) {
					resolveClass(result);
				}
				return result;
			}
		}
		/** 使用默认加载规则 **/
		return super.loadClass(name, resolve);
	}

	/**
	 * 确定指定类不在自定义类加载器排除加载的范围内
	 */
	protected boolean isEligibleForOverriding(String className) {
		return !isExcluded(className);
	}

	/**
	 * OverridingClassLoader 重写覆盖loadClass，默认会先自己加载 破坏双亲委派原则优先子加载器加载
	 */
	@Nullable
	protected Class<?> loadClassForOverriding(String name) throws ClassNotFoundException {
		/** 首先判断该类是否已经被加载  **/
		Class<?> result = findLoadedClass(name);
		/** 加载给定类的定义字节，通过{@link #defineClass}调用变成Class对象。 **/
		if (result == null) {
			byte[] bytes = loadBytesForClass(name);
			if (bytes != null) {
				result = defineClass(name, bytes, 0, bytes.length);
			}
		}
		/** 返回Class 对象 **/
		return result;
	}

	/**
	 * 加载给定类的定义字节，通过{@link #defineClass}调用变成Class对象。
	 * <p>默认实现委托给{@link #openStreamForClass}和{@link #transformIfNecessary}。
	 */
	@Nullable
	protected byte[] loadBytesForClass(String name) throws ClassNotFoundException {
		/** 打开指定类的inputstream。<p>默认实现通过父类加载器的{@code getresourceasstream}方法加载标准类文件。 **/
		InputStream is = openStreamForClass(name);
		if (is == null) {
			return null;
		}
		try {
			/**  加载原始字节。**/
			byte[] bytes = FileCopyUtils.copyToByteArray(is);
			/** 模板方法，子类实现扩展，默认直接返回bytes  **/
			return transformIfNecessary(name, bytes);
		}
		catch (IOException ex) {
			throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
		}
	}

	/**
	 * 打开指定类的inputstream。<p>默认实现通过父类加载器的{@code getresourceasstream}方法加载标准类文件。
	 */
	@Nullable
	protected InputStream openStreamForClass(String name) {
		String internalName = name.replace('.', '/') + CLASS_FILE_SUFFIX;
		return getParent().getResourceAsStream(internalName);
	}


	/**
	 * 模板方法，子类实现扩展，默认直接返回bytes
	 */
	protected byte[] transformIfNecessary(String name, byte[] bytes) {
		return bytes;
	}

}
