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

package org.springframework.instrument.classloading;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.core.DecoratingClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;

/**
 * ClassLoader decorator that shadows an enclosing ClassLoader,
 * applying registered transformers to all affected classes.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Costin Leau
 * @since 2.0
 * @see #addTransformer
 * @see org.springframework.core.OverridingClassLoader
 */
public class ShadowingClassLoader extends DecoratingClassLoader {

	/** 默认情况下排除的软件包。 */
	public static final String[] DEFAULT_EXCLUDED_PACKAGES =
			new String[] {"java.", "javax.", "jdk.", "sun.", "oracle.", "com.sun.", "com.ibm.", "COM.ibm.",
					"org.w3c.", "org.xml.", "org.dom4j.", "org.eclipse", "org.aspectj.", "net.sf.cglib",
					"org.springframework.cglib", "org.apache.xerces.", "org.apache.commons.logging."};


	/**
	 * 代理ClassLoader
	 */
	private final ClassLoader enclosingClassLoader;

	/**
	 * 字节码转换工具
	 */
	private final List<ClassFileTransformer> classFileTransformers = new LinkedList<>();

	/**
	 * 字节码转换工具
	 */
	private final Map<String, Class<?>> classCache = new HashMap<>();


	/**
	 * 创建一个新的ShadowingClassLoader，使用{@link #DEFAULT_EXCLUDED_PACKAGES}装饰给定的ClassLoader。
	 */
	public ShadowingClassLoader(ClassLoader enclosingClassLoader) {
		this(enclosingClassLoader, true);
	}

	/**
	 * 创建一个新的ShadowingClassLoader，使用{@link #DEFAULT_EXCLUDED_PACKAGES}装饰给定的ClassLoader。
	 */
	public ShadowingClassLoader(ClassLoader enclosingClassLoader, boolean defaultExcludes) {
		Assert.notNull(enclosingClassLoader, "Enclosing ClassLoader must not be null");
		this.enclosingClassLoader = enclosingClassLoader;
		if (defaultExcludes) {
			for (String excludedPackage : DEFAULT_EXCLUDED_PACKAGES) {
				excludePackage(excludedPackage);
			}
		}
	}


	/**
	 * 将给定的ClassFileTransformer添加到此ClassLoader将应用的转换器列表中。
	 */
	public void addTransformer(ClassFileTransformer transformer) {
		Assert.notNull(transformer, "Transformer must not be null");
		this.classFileTransformers.add(transformer);
	}

	/**
	 * 将所有ClassFileTransformers从给定的ClassLoader复制到该ClassLoader将应用的转换器列表。
	 */
	public void copyTransformers(ShadowingClassLoader other) {
		Assert.notNull(other, "Other ClassLoader must not be null");
		this.classFileTransformers.addAll(other.classFileTransformers);
	}


	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		if (shouldShadow(name)) {
			Class<?> cls = this.classCache.get(name);
			if (cls != null) {
				return cls;
			}
			return doLoadClass(name);
		}
		else {
			return this.enclosingClassLoader.loadClass(name);
		}
	}

	/**
	 * 确定是否应将给定的类从阴影中排除。
	 */
	private boolean shouldShadow(String className) {
		return (!className.equals(getClass().getName()) && !className.endsWith("ShadowingClassLoader") &&
				isEligibleForShadowing(className));
	}

	/**
	 * 确定指定类不在自定义类加载器排除加载的范围内
	 */
	protected boolean isEligibleForShadowing(String className) {
		return !isExcluded(className);
	}


	private Class<?> doLoadClass(String name) throws ClassNotFoundException {
		String internalName = StringUtils.replace(name, ".", "/") + ".class";
		InputStream is = this.enclosingClassLoader.getResourceAsStream(internalName);
		if (is == null) {
			throw new ClassNotFoundException(name);
		}
		try {
			byte[] bytes = FileCopyUtils.copyToByteArray(is);
			bytes = applyTransformers(name, bytes);
			Class<?> cls = defineClass(name, bytes, 0, bytes.length);
			// Additional check for defining the package, if not defined yet.
			if (cls.getPackage() == null) {
				int packageSeparator = name.lastIndexOf('.');
				if (packageSeparator != -1) {
					String packageName = name.substring(0, packageSeparator);
					definePackage(packageName, null, null, null, null, null, null, null);
				}
			}
			this.classCache.put(name, cls);
			return cls;
		}
		catch (IOException ex) {
			throw new ClassNotFoundException("Cannot load resource for class [" + name + "]", ex);
		}
	}

	/**
	 * 执行字节码转换
	 */
	private byte[] applyTransformers(String name, byte[] bytes) {
		String internalName = StringUtils.replace(name, ".", "/");
		try {
			for (ClassFileTransformer transformer : this.classFileTransformers) {
				byte[] transformed = transformer.transform(this, internalName, null, null, bytes);
				bytes = (transformed != null ? transformed : bytes);
			}
			return bytes;
		}
		catch (IllegalClassFormatException ex) {
			throw new IllegalStateException(ex);
		}
	}


	/**
	 * 重写加载资源，使用enclosingClassLoader加载
	 */
	@Override
	public URL getResource(String name) {
		return this.enclosingClassLoader.getResource(name);
	}

	/**
	 * 重写打开指定类的inputstream，使用enclosingClassLoader打开
	 */
	@Override
	@Nullable
	public InputStream getResourceAsStream(String name) {
		return this.enclosingClassLoader.getResourceAsStream(name);
	}

	/**
	 * 重写加载资源，使用enclosingClassLoader加载
	 */
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return this.enclosingClassLoader.getResources(name);
	}

}
